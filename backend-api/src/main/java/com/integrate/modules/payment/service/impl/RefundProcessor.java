package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.booking.service.model.PayableBooking;
import com.integrate.modules.payment.entity.PaymentTransaction;
import com.integrate.modules.payment.entity.Refund;
import com.integrate.modules.payment.gateway.RefundGateway;
import com.integrate.modules.payment.gateway.RefundGateway.Outcome;
import com.integrate.modules.payment.gateway.RefundGateway.Result;
import com.integrate.modules.payment.repository.PaymentTransactionRepository;
import com.integrate.modules.payment.repository.RefundRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ejecuta los reembolsos pendientes contra la pasarela (ADR-0034 §3). Cada orden es una máquina de estados
 * reanudable: una transacción corta la reclama ({@code REQUESTED → PROCESSING}), la llamada de red va **fuera** de
 * cualquier transacción y otra transacción corta la cierra. La clave de idempotencia hace seguro repetir la llamada
 * si el proceso cae entre una y otra.
 */
@Component
@RequiredArgsConstructor
public class RefundProcessor {

    /** Motivo cuando la reserva quedó en conflicto de sincronización: el 85 % comunitario no se compensa (ADR-0033). */
    public static final String SYNC_CONFLICT_REASON = "SYNC_CONFLICT_COMMUNITY_PROTECTED";

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final RefundRepository refunds;
    private final PaymentTransactionRepository payments;
    private final BookingPayments bookingPayments;
    private final RefundGateway gateway;
    private final AuditTrail audit;
    private final PlatformTransactionManager transactionManager;

    @Value("${integrate.payments.workers.batch-size:50}")
    private int batchSize;

    /** @return órdenes que quedaron cerradas ({@code COMPLETED} o {@code FAILED}) en este barrido */
    public int sweepOnce() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<UUID> ids = tx.execute(status -> refunds.findOpenIds(batchSize));
        int closed = 0;
        for (UUID id : ids == null ? List.<UUID>of() : ids) {
            try {
                if (process(tx, id)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.warn("refund.process failed refundId={} cause={}", id, e.toString());
            }
        }
        return closed;
    }

    private boolean process(TransactionTemplate tx, UUID id) {
        Claim claim = tx.execute(status -> claim(id));
        if (claim == null) {
            return false;
        }
        if (claim.conflict()) {
            return true;
        }
        Result result = gateway.refund(claim.idempotencyKey(), claim.paymentId(), claim.amountCop());
        if (result.outcome() == Outcome.RETRY) {
            return false;
        }
        tx.executeWithoutResult(status -> finish(id, result));
        return true;
    }

    /** @return nulo si no hay nada que hacer; {@code conflict} si la orden se cerró aquí por el conflicto */
    private Claim claim(UUID id) {
        Refund refund = refunds.findByIdForUpdate(id).orElse(null);
        if (refund == null || !refund.isOpen()) {
            return null;
        }
        PaymentTransaction payment = payments.findById(refund.getPaymentId()).orElseThrow();
        String bookingStatus = bookingPayments.find(payment.getBookingId()).map(PayableBooking::status).orElse(null);
        if ("SYNC_CONFLICT".equals(bookingStatus)) {
            refund.fail(SYNC_CONFLICT_REASON);
            refunds.flush();
            audit.record(new AuditEntry("payments", "refunds", id.toString(), "REFUND_FAILED",
                    AuditEntry.Severity.HIGH, null, null, null, Map.of("reason", SYNC_CONFLICT_REASON,
                    "amountCop", refund.getAmountCop().longValueExact())));
            return new Claim(null, null, 0, true);
        }
        refund.markProcessing();
        refunds.flush();
        return new Claim(refund.getIdempotencyKey(), refund.getPaymentId(), refund.getAmountCop().longValueExact(),
                false);
    }

    private void finish(UUID id, Result result) {
        Refund refund = refunds.findByIdForUpdate(id).orElseThrow();
        if (!refund.isOpen()) {
            return;
        }
        if (result.outcome() == Outcome.COMPLETED) {
            refund.complete(result.gatewayRefundId());
        } else {
            refund.fail(result.failureReason());
        }
        refunds.flush();
        audit.record(new AuditEntry("payments", "refunds", id.toString(),
                result.outcome() == Outcome.COMPLETED ? "REFUND_COMPLETED" : "REFUND_FAILED",
                result.outcome() == Outcome.COMPLETED ? AuditEntry.Severity.INFO : AuditEntry.Severity.HIGH, null,
                null, null, Map.of("amountCop", refund.getAmountCop().longValueExact())));
    }

    private record Claim(String idempotencyKey, UUID paymentId, long amountCop, boolean conflict) {
    }
}