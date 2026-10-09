package com.integrate.modules.voucher.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.storage.VoucherDocumentStorage;
import com.integrate.modules.booking.service.BookingVouchers;
import com.integrate.modules.booking.service.model.VoucherSubject;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import com.integrate.modules.voucher.config.VoucherProperties;
import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.modules.voucher.service.impl.SigningKeyRegistrar.ActiveSigningKey;
import com.integrate.modules.voucher.service.impl.VoucherPdfRenderer.VoucherDocument;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emisión idempotente por reserva y generación del PDF (ADR-0010 §3-§5). La emisión es una transacción corta sin
 * S3; el PDF se genera fuera de ella, de modo que un fallo de S3 deja {@code pdf_s3_key} nulo para el barrido y nunca
 * revierte el voucher.
 */
@Component
public class VoucherIssuer {

    private static final Logger log = LoggerFactory.getLogger(VoucherIssuer.class);
    private static final int SHORT_CODE_ATTEMPTS = 5;

    private final VoucherRepository voucherRepository;
    private final BookingVouchers bookingVouchers;
    private final ScheduleDirectory scheduleDirectory;
    private final SigningKeyRegistrar keys;
    private final VoucherSigner signer;
    private final VoucherPdfRenderer renderer;
    private final VoucherDocumentStorage storage;
    private final AuditTrail auditTrail;
    private final VoucherEmail voucherEmail;
    private final VoucherProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public VoucherIssuer(VoucherRepository voucherRepository, BookingVouchers bookingVouchers,
                         ScheduleDirectory scheduleDirectory, SigningKeyRegistrar keys, VoucherSigner signer,
                         VoucherPdfRenderer renderer, VoucherDocumentStorage storage, AuditTrail auditTrail,
                         VoucherEmail voucherEmail, VoucherProperties properties, Clock clock,
                         PlatformTransactionManager transactionManager) {
        this.voucherRepository = voucherRepository;
        this.bookingVouchers = bookingVouchers;
        this.scheduleDirectory = scheduleDirectory;
        this.keys = keys;
        this.signer = signer;
        this.renderer = renderer;
        this.storage = storage;
        this.auditTrail = auditTrail;
        this.voucherEmail = voucherEmail;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Emite el voucher de una reserva confirmada.
     *
     * @return id del voucher (nuevo o ya existente), o vacío si la reserva no existe o no está {@code CONFIRMED}
     */
    public Optional<UUID> issue(UUID bookingId) {
        Optional<Voucher> existing = voucherRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            return existing.map(Voucher::getId);
        }
        Optional<VoucherSubject> subject = bookingVouchers.subjectOf(bookingId).filter(VoucherSubject::isConfirmed);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        ScheduleSummary schedule = scheduleDirectory.describe(subject.get().scheduleId())
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " has no schedule"));
        ActiveSigningKey key = keys.active();
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = schedule.endTime().plus(properties.validityAfterEnd()).truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 1; attempt <= SHORT_CODE_ATTEMPTS; attempt++) {
            UUID voucherId = UUID.randomUUID();
            Voucher draft = Voucher.issue(voucherId, bookingId, UUID.randomUUID(), key.id(), "0".repeat(64),
                    ShortCodes.next(), issuedAt, expiresAt);
            String token = signer.sign(VoucherClaims.of(draft, subject.get(), schedule), key);
            Voucher voucher = Voucher.issue(voucherId, bookingId, draft.getJti(), key.id(),
                    VoucherSigner.sha256Hex(token), draft.getShortCode(), issuedAt, expiresAt);
            try {
                transaction.executeWithoutResult(status -> {
                    voucherRepository.saveAndFlush(voucher);
                    // Última sentencia: la bitácora retiene audit.chain_head hasta el COMMIT (ADR-0008 §6).
                    auditTrail.record(new AuditEntry("voucher", "vouchers", voucherId.toString(), "VOUCHER_ISSUED",
                            Severity.INFO, null, null, null, Map.of("bookingId", bookingId.toString(),
                            "jti", voucher.getJti().toString(), "kid", key.kid())));
                });
                log.info("voucher.issued bookingId={} voucherId={}", bookingId, voucherId);
                return Optional.of(voucherId);
            } catch (DataIntegrityViolationException e) {
                String constraint = constraintOf(e);
                if ("uq_vouchers_booking".equals(constraint)) {
                    // Otro emisor (evento o barrido) ganó la carrera por esta reserva.
                    return voucherRepository.findByBookingId(bookingId).map(Voucher::getId);
                }
                if (!"uq_vouchers_short_code".equals(constraint)) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Could not draw a unique short code for booking " + bookingId);
    }

    /** Genera y sube el PDF si falta. Idempotente: la clave S3 es fija por voucher. */
    public void renderPdf(UUID voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalStateException("Voucher " + voucherId + " not found"));
        if (voucher.getPdfS3Key() != null) {
            return;
        }
        VoucherSubject subject = bookingVouchers.subjectOf(voucher.getBookingId())
                .orElseThrow(() -> new IllegalStateException("Voucher " + voucherId + " has no booking"));
        ScheduleSummary schedule = scheduleDirectory.describe(subject.scheduleId())
                .orElseThrow(() -> new IllegalStateException("Voucher " + voucherId + " has no schedule"));
        String token = rederive(voucher, subject, schedule)
                .orElseThrow(() -> new IllegalStateException("Voucher " + voucherId
                        + " cannot be re-derived with the active key; its PDF cannot be rendered"));

        byte[] pdf = renderer.render(new VoucherDocument(schedule.title(), schedule.startTime(), schedule.endTime(),
                schedule.timezone(), schedule.meetingPoint(), schedule.municipality(), schedule.department(),
                subject.holder().fullName(), subject.holder().docType() + " " + subject.holder().docNumber(),
                subject.slots(), subject.participantNames(), voucher.getShortCode(), voucher.getJti().toString(),
                voucher.getCreatedAt(), voucher.getExpiresAt(), token));
        String key = pdfKey(voucher);
        storage.put(key, pdf, VoucherPdfRenderer.CONTENT_TYPE);
        // Mismo COMMIT para el PDF y el correo (ADR-0011 §2): solo quien fija la clave encola, así el correo sale una
        // sola vez aunque dos generadores compitan, y nunca antes de que el adjunto exista en S3.
        transaction.executeWithoutResult(status -> {
            if (voucherRepository.attachPdf(voucherId, key) == 1) {
                voucherEmail.enqueue(voucher, key, subject, schedule);
            }
        });
        log.info("voucher.pdf voucherId={} bytes={}", voucherId, pdf.length);
    }

    /**
     * Vuelve a firmar los datos del voucher y comprueba que el resultado sea el token emitido ({@code token_sha256}).
     * Vacío si el voucher se firmó con otra llave o si los datos ya no producen el mismo token.
     */
    public Optional<String> rederive(Voucher voucher, VoucherSubject subject, ScheduleSummary schedule) {
        ActiveSigningKey key = keys.active();
        if (voucher.getSigningKeyId() != key.id()) {
            return Optional.empty();
        }
        String token = signer.sign(VoucherClaims.of(voucher, subject, schedule), key);
        return VoucherSigner.sha256Hex(token).equals(voucher.getTokenSha256()) ? Optional.of(token) : Optional.empty();
    }

    static String pdfKey(Voucher voucher) {
        var issued = voucher.getCreatedAt().atZone(ZoneOffset.UTC);
        return "vouchers/%d/%02d/%s.pdf".formatted(issued.getYear(), issued.getMonthValue(), voucher.getId());
    }

    private static String constraintOf(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
        }
        return null;
    }
}
