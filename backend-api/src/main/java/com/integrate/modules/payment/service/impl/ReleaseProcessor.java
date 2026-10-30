package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.modules.booking.service.BookingExecution;
import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.booking.service.model.BookingExecutedEvent;
import com.integrate.modules.booking.service.model.PayableBooking;
import com.integrate.modules.payment.repository.BookingVoucherLock;
import com.integrate.modules.payment.repository.DisbursementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liberación de la dispersión comunitaria (T-2.5, ADR-0034 §4): por asistencia (el evento del canje, en su misma
 * transacción) o supletoria a las 24 h. El barrido elige candidatas sin bloquear y, para cada una, abre su propia
 * transacción que bloquea en el orden canónico voucher → reserva → dispersión.
 */
@Component
@RequiredArgsConstructor
public class ReleaseProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReleaseProcessor.class);
    /** Reservas cuya dispersión se libera al vencer la gracia; las demás (cancelada con reembolso, conflicto) no. */
    private static final Set<String> RELEASABLE = Set.of("CONFIRMED", "NO_SHOW", "EXECUTED", "CANCELLED_NO_REFUND");

    private final DisbursementRepository disbursements;
    private final BookingVoucherLock voucherLock;
    private final BookingPayments bookingPayments;
    private final BookingExecution bookingExecution;
    private final AuditTrail audit;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Value("${integrate.payments.workers.batch-size:50}")
    private int batchSize;

    /** Liberación por asistencia: corre en el hilo y la transacción del canje. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onBookingExecuted(BookingExecutedEvent event) {
        disbursements.releaseScheduledOfBooking(event.bookingId(), event.at());
    }

    /** @return dispersiones liberadas en este barrido */
    public int sweepOnce() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Instant now = clock.instant();
        List<UUID> due = tx.execute(status -> disbursements.findDueIds(now, batchSize));
        int released = 0;
        for (UUID id : due == null ? List.<UUID>of() : due) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> releaseDue(id, now)))) {
                    released++;
                }
            } catch (RuntimeException e) {
                log.warn("disbursement.release failed id={} cause={}", id, e.toString());
            }
        }
        return released;
    }

    private boolean releaseDue(UUID disbursementId, Instant now) {
        UUID bookingId = disbursements.bookingIdOf(disbursementId);
        voucherLock.lockOf(bookingId);
        PayableBooking booking = bookingPayments.lockForPayment(bookingId).orElse(null);
        if (booking == null || !RELEASABLE.contains(booking.status())
                || disbursements.hasOpenSyncIncident(bookingId)) {
            return false;
        }
        if ("CONFIRMED".equals(booking.status())) {
            bookingExecution.markNoShow(bookingId);
        }
        if (disbursements.releaseScheduledOfBooking(bookingId, now) == 0) {
            return false;
        }
        audit.record(new AuditEntry("payments", "disbursements", disbursementId.toString(), "DISBURSEMENT_RELEASED",
                AuditEntry.Severity.INFO, null, null, null,
                Map.of("bookingId", bookingId.toString(), "trigger", "SUPPLETORY_GRACE",
                        "bookingStatus", booking.status())));
        return true;
    }
}