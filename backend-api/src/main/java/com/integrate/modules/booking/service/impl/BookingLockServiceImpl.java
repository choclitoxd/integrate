package com.integrate.modules.booking.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.payload.InvalidParam;
import com.integrate.common.web.TraceIdFilter;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.service.ConsentService;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.ConsentStatus.State;
import com.integrate.modules.booking.config.BookingProperties;
import com.integrate.modules.booking.dto.request.BookingLockRequest;
import com.integrate.modules.booking.dto.request.BookingLockRequest.Participant;
import com.integrate.modules.booking.dto.response.BookingLockResponse;
import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.entity.Booking.EmergencyCiphertexts;
import com.integrate.modules.booking.entity.BookingParticipant;
import com.integrate.modules.booking.repository.BookingParticipantRepository;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingLockService;
import com.integrate.modules.booking.service.model.LockOutcome;
import com.integrate.modules.catalog.service.SlotInventory;
import com.integrate.modules.catalog.service.model.ReservedSlots;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orden exacto de ADR-0008 §3: validación, idempotencia y consentimiento <b>fuera</b> de la transacción (solo
 * lecturas, sin bloqueos); después una transacción {@code READ COMMITTED} que bloquea la franja, descuenta,
 * inserta la reserva y sus participantes y audita como última sentencia. El {@code lock_timeout} de 2 s lo fija
 * el rol {@code integrate_api} ({@code 00_bootstrap.sql}).
 */
@Service
public class BookingLockServiceImpl implements BookingLockService {

    static final String IDEMPOTENCY_CONSTRAINT = "uq_bookings_idempotency";

    private final BookingRepository bookingRepository;
    private final BookingParticipantRepository participantRepository;
    private final SlotInventory slotInventory;
    private final ConsentService consentService;
    private final SensitiveDataCipher cipher;
    private final AuditTrail auditTrail;
    private final BookingRequestHasher hasher;
    private final BookingProperties properties;
    private final Clock clock;
    private final TransactionTemplate lockTransaction;

    public BookingLockServiceImpl(BookingRepository bookingRepository,
                                  BookingParticipantRepository participantRepository, SlotInventory slotInventory,
                                  ConsentService consentService, SensitiveDataCipher cipher, AuditTrail auditTrail,
                                  BookingRequestHasher hasher, BookingProperties properties, Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.participantRepository = participantRepository;
        this.slotInventory = slotInventory;
        this.consentService = consentService;
        this.cipher = cipher;
        this.auditTrail = auditTrail;
        this.hasher = hasher;
        this.properties = properties;
        this.clock = clock;
        this.lockTransaction = new TransactionTemplate(transactionManager);
        this.lockTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public LockOutcome lock(UUID touristId, UUID idempotencyKey, BookingLockRequest request, ClientInfo client) {
        requireConsistent(request);
        String requestSha256 = hasher.hash(request);

        Optional<LockOutcome> replay = replay(touristId, idempotencyKey, requestSha256);
        if (replay.isPresent()) {
            return replay.get();
        }
        requireHealthConsent(touristId);

        try {
            return lockTransaction.execute(tx -> lockInTransaction(touristId, idempotencyKey, requestSha256, request,
                    client));
        } catch (DataIntegrityViolationException e) {
            // Carrera de la misma clave: la otra petición ganó el índice único y esta transacción se revirtió
            // completa, incluido el descuento de cupos. Se responde como un reintento.
            if (violates(e, IDEMPOTENCY_CONSTRAINT)) {
                return replay(touristId, idempotencyKey, requestSha256).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private LockOutcome lockInTransaction(UUID touristId, UUID idempotencyKey, String requestSha256,
                                          BookingLockRequest request, ClientInfo client) {
        // timestamptz guarda microsegundos: así el 201 y las lecturas posteriores muestran el mismo instante.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int slots = request.requestedSlots();
        ReservedSlots reserved = slotInventory.reserve(request.scheduleId(), slots, now);

        UUID bookingId = UUID.randomUUID();
        short keyVersion = cipher.currentKeyVersion();
        Booking booking = bookingRepository.saveAndFlush(Booking.hold(bookingId, touristId, request.scheduleId(),
                slots, reserved.unitPriceCop(), now, now.plus(properties.holdTtl()),
                encryptEmergencyContact(bookingId, request.emergencyContact(), keyVersion), idempotencyKey,
                requestSha256));
        participantRepository.saveAllAndFlush(request.participants().stream()
                .map(p -> BookingParticipant.of(bookingId, p.fullName().strip(), p.docType(), p.docNumber(),
                        cipher.encrypt(p.medicalEps().strip(),
                                BookingParticipant.epsAad(bookingId, p.docType(), p.docNumber()), keyVersion),
                        keyVersion, p.isHolder()))
                .toList());

        // Última sentencia: el trigger de la bitácora retiene audit.chain_head hasta el COMMIT (ADR-0008 §6).
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scheduleId", request.scheduleId().toString());
        details.put("slots", slots);
        details.put("totalAmountCop", booking.getTotalAmountCop().longValueExact());
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            details.put("traceId", traceId);
        }
        auditTrail.record(new AuditEntry("booking", "bookings", bookingId.toString(), "BOOKING_SLOTS_LOCKED",
                Severity.INFO, touristId, client.ipAddress(), client.userAgent(), details));

        return new LockOutcome(BookingLockResponse.from(booking, reserved.experienceId(), now), false);
    }

    /**
     * Mismo hash dentro de la ventana → reproducción (200). Hash distinto, o la clave fuera de la ventana → 409.
     */
    private Optional<LockOutcome> replay(UUID touristId, UUID idempotencyKey, String requestSha256) {
        return bookingRepository.findByTouristIdAndIdempotencyKey(touristId, idempotencyKey).map(existing -> {
            Instant now = clock.instant();
            if (!requestSha256.equals(existing.getRequestSha256())) {
                throw BookingErrors.idempotencyKeyReused("Idempotency-Key was already used with a different payload.");
            }
            if (now.isAfter(existing.getCreatedAt().plus(properties.idempotencyWindow()))) {
                throw BookingErrors.idempotencyKeyReused("Idempotency-Key was already used and its replay window "
                        + "has expired; send a new key.");
            }
            UUID experienceId = slotInventory.experienceOf(existing.getScheduleId())
                    .orElseThrow(() -> new IllegalStateException("Booking " + existing.getId() + " has no schedule"));
            return new LockOutcome(BookingLockResponse.from(existing, experienceId, now), true);
        });
    }

    private void requireHealthConsent(UUID touristId) {
        ConsentStatus status = consentService.status(touristId);
        boolean granted = status.purposes().stream()
                .anyMatch(p -> p.purpose() == ConsentPurpose.HEALTH_EMERGENCY && p.state() == State.VALID);
        if (!granted) {
            throw BookingErrors.healthConsentRequired(status.currentPolicyVersion());
        }
    }

    /** Reglas entre campos del contrato §5.1 (422), evaluadas antes de cualquier lectura de la base. */
    private static void requireConsistent(BookingLockRequest request) {
        List<InvalidParam> params = new ArrayList<>();
        List<Participant> participants = request.participants();
        if (participants.size() != request.requestedSlots()) {
            params.add(new InvalidParam("participants", "must contain exactly requestedSlots ("
                    + request.requestedSlots() + ") participants"));
        }
        if (participants.stream().filter(Participant::isHolder).count() != 1) {
            params.add(new InvalidParam("participants", "exactly one participant must be the holder (isHolder)"));
        }
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            if (p.fullName().strip().length() < 3) {
                params.add(new InvalidParam("participants[" + i + "].fullName",
                        "must have at least 3 non-blank characters"));
            }
            Integer first = seen.putIfAbsent(p.docType() + ":" + p.docNumber().toUpperCase(), i);
            if (first != null) {
                params.add(new InvalidParam("participants[" + i + "].docNumber",
                        "duplicates the document of participants[" + first + "]"));
            }
        }
        if (request.emergencyContact().fullName().strip().length() < 3) {
            params.add(new InvalidParam("emergencyContact.fullName", "must have at least 3 non-blank characters"));
        }
        if (!params.isEmpty()) {
            throw BookingErrors.invalid(params);
        }
    }

    private EmergencyCiphertexts encryptEmergencyContact(UUID bookingId, BookingLockRequest.EmergencyContact contact,
                                                         short keyVersion) {
        return new EmergencyCiphertexts(
                cipher.encrypt(contact.fullName().strip(), Booking.aad("emergency_contact_name_enc", bookingId),
                        keyVersion),
                cipher.encrypt(contact.phone(), Booking.aad("emergency_contact_phone_enc", bookingId), keyVersion),
                keyVersion);
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConstraintViolationException cve && constraint.equals(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
