package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.storage.VoucherDocumentStorage;
import com.integrate.modules.notification.service.NotificationOutbox;
import com.integrate.modules.notification.service.model.EmailRequest;
import com.integrate.modules.notification.service.model.EmailRequest.Attachment;
import com.integrate.modules.payment.gateway.PayoutGateway;
import com.integrate.modules.payment.gateway.PayoutGateway.Outcome;
import com.integrate.modules.payment.gateway.PayoutGateway.Result;
import com.integrate.modules.payment.repository.PayoutQueries;
import com.integrate.modules.payment.repository.PayoutQueries.AssociationContact;
import com.integrate.modules.payment.repository.PayoutQueries.SubmittedBatch;
import com.integrate.modules.payment.repository.PayoutQueries.Totals;
import com.integrate.modules.payment.service.impl.MoneyRules.SettlementBreakdown;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pago a las asociaciones por lotes y comprobante contable (T-2.5, ADR-0034 §5 y §6). Máquina de estados
 * reanudable con la llamada de red fuera de toda transacción: 1) arma lotes {@code SUBMITTED} con las porciones
 * liberadas, 2) los envía a la pasarela con clave de idempotencia, 3) los cierra {@code PAID} (con comprobante y
 * correo) o {@code FAILED} (devolviendo las porciones al conjunto de pendientes).
 */
@Component
@RequiredArgsConstructor
public class PayoutProcessor {

    static final String RECEIPT_TEMPLATE = "settlement-receipt";
    static final String RECEIPT_EVENT = "SETTLEMENT_RECEIPT_ISSUED";
    private static final Logger log = LoggerFactory.getLogger(PayoutProcessor.class);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",
            Locale.forLanguageTag("es-CO"));

    private final PayoutQueries queries;
    private final PayoutGateway gateway;
    private final ReceiptPdfRenderer renderer;
    private final VoucherDocumentStorage storage;
    private final NotificationOutbox outbox;
    private final AuditTrail audit;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Value("${integrate.payments.workers.batch-size:50}")
    private int batchSize;

    /** Asociaciones ya avisadas hoy de que no tienen cuenta: una alerta por asociación y día. */
    private final Map<UUID, LocalDate> missingAccountAlerts = new ConcurrentHashMap<>();

    /** @return lotes que quedaron pagados en este barrido */
    public int sweepOnce() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<UUID> associations = tx.execute(status -> queries.associationsWithUnbatchedShares());
        for (UUID association : associations == null ? List.<UUID>of() : associations) {
            try {
                tx.executeWithoutResult(status -> assemble(association));
            } catch (RuntimeException e) {
                log.warn("payout.assemble failed associationId={} cause={}", association, e.toString());
            }
        }
        List<SubmittedBatch> submitted = tx.execute(status -> queries.submittedBatches(batchSize));
        int paid = 0;
        for (SubmittedBatch batch : submitted == null ? List.<SubmittedBatch>of() : submitted) {
            try {
                if (submit(tx, batch)) {
                    paid++;
                }
            } catch (RuntimeException e) {
                log.warn("payout.submit failed batchId={} cause={}", batch.id(), e.toString());
            }
        }
        return paid;
    }

    /** Sin cuenta por defecto verificada no se arma lote: la porción sigue liberada (ADR-0034 §5). */
    private void assemble(UUID associationId) {
        Instant now = clock.instant();
        UUID account = queries.defaultAccount(associationId).orElse(null);
        if (account == null) {
            LocalDate today = LocalDate.ofInstant(now, BOGOTA);
            if (!today.equals(missingAccountAlerts.put(associationId, today))) {
                audit.record(new AuditEntry("payments", "community_associations", associationId.toString(),
                        "PAYOUT_ACCOUNT_MISSING", AuditEntry.Severity.WARNING, null, null, null,
                        Map.of("date", today.toString())));
            }
            return;
        }
        UUID batchId = UUID.randomUUID();
        queries.createSubmittedBatch(batchId, associationId, account, now);
        if (queries.attachShares(batchId, associationId) == 0) {
            throw new IllegalStateException("No released shares could be attached to batch " + batchId);
        }
    }

    private boolean submit(TransactionTemplate tx, SubmittedBatch batch) {
        Result result = gateway.payout("payout:" + batch.id(), batch.id(), batch.payoutAccountId(),
                batch.amountCop().longValueExact());
        if (result.outcome() == Outcome.RETRY) {
            return false;
        }
        if (result.outcome() == Outcome.REJECTED) {
            tx.executeWithoutResult(status -> fail(batch, result.failureReason()));
            return false;
        }
        tx.executeWithoutResult(status -> complete(batch, result.gatewayPayoutId()));
        return true;
    }

    private void fail(SubmittedBatch batch, String reason) {
        queries.markFailed(batch.id(), reason);
        queries.detachShares(batch.id());
        audit.record(new AuditEntry("payments", "payout_batches", batch.id().toString(), "PAYOUT_FAILED",
                AuditEntry.Severity.HIGH, null, null, null,
                Map.of("reason", reason, "amountCop", batch.amountCop().longValueExact())));
    }

    private void complete(SubmittedBatch batch, String gatewayPayoutId) {
        Instant now = clock.instant();
        queries.markPaid(batch.id(), gatewayPayoutId, now);
        queries.payShares(batch.id());

        Totals totals = queries.totals(batch.id());
        SettlementBreakdown breakdown = MoneyRules.settlement(totals.grossCop(), totals.gatewayFeeCop(),
                totals.netCommunityCop());
        AssociationContact contact = queries.contactOf(batch.associationId()).orElseThrow();
        String pdfKey = "settlements/" + batch.id() + ".pdf";
        storage.put(pdfKey, renderer.render(batch.id(), contact.legalName(), breakdown, totals.count(), now),
                ReceiptPdfRenderer.CONTENT_TYPE);
        UUID receiptId = queries.insertReceipt(batch.id(), breakdown.grossCop(), breakdown.gatewayFeeCop(),
                breakdown.platformFeeCop(), breakdown.netCommunityCop(), pdfKey, now);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("associationName", contact.legalName());
        payload.put("paidDate", DATE.format(now.atZone(BOGOTA)));
        payload.put("paymentCount", totals.count());
        payload.put("grossAmount", ReceiptPdfRenderer.cop(breakdown.grossCop()));
        payload.put("platformFee", ReceiptPdfRenderer.cop(breakdown.platformFeeCop()));
        payload.put("gatewayFee", ReceiptPdfRenderer.cop(breakdown.gatewayFeeCop()));
        payload.put("netAmount", ReceiptPdfRenderer.cop(breakdown.netCommunityCop()));
        payload.put("batchId", batch.id().toString());
        outbox.enqueueEmail(new EmailRequest("payout_batch", batch.id().toString(), RECEIPT_EVENT, null,
                contact.contactEmail(), RECEIPT_TEMPLATE, payload,
                List.of(new Attachment(Attachment.VOUCHERS, pdfKey, "comprobante-" + batch.id() + ".pdf",
                        ReceiptPdfRenderer.CONTENT_TYPE))));

        audit.record(new AuditEntry("payments", "payout_batches", batch.id().toString(), "PAYOUT_COMPLETED",
                AuditEntry.Severity.INFO, null, null, null,
                Map.of("receiptId", receiptId.toString(), "netCommunityCop", breakdown.netCommunityCop().longValueExact(),
                        "payments", totals.count())));
    }
}