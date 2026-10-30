package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.booking.service.BookingCancellations;
import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.booking.service.model.CancellableBooking;
import com.integrate.modules.booking.service.model.PayableBooking;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import com.integrate.modules.payment.dto.response.CancellationResponse;
import com.integrate.modules.payment.entity.CommissionPolicy;
import com.integrate.modules.payment.entity.PaymentStatus;
import com.integrate.modules.payment.entity.PaymentTransaction;
import com.integrate.modules.payment.entity.Refund;
import com.integrate.modules.payment.repository.BookingVoucherLock;
import com.integrate.modules.payment.repository.CommissionPolicyRepository;
import com.integrate.modules.payment.repository.DisbursementRepository;
import com.integrate.modules.payment.repository.PaymentTransactionRepository;
import com.integrate.modules.payment.repository.RefundRepository;
import com.integrate.modules.payment.service.BookingCancellationService;
import com.integrate.modules.payment.service.impl.CancellationPolicy.Decision;
import com.integrate.modules.payment.service.impl.CancellationPolicy.Kind;
import com.integrate.modules.payment.service.model.BookingCancelledEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelación del turista (T-2.6, ADR-0034 §2). Una transacción con el orden de bloqueo canónico voucher → reserva →
 * pago → dispersión → franja. La auditoría es la última sentencia.
 */
@Service
@RequiredArgsConstructor
class BookingCancellationServiceImpl implements BookingCancellationService {

    private final BookingPayments bookingPayments;
    private final BookingCancellations bookingCancellations;
    private final BookingVoucherLock voucherLock;
    private final PaymentTransactionRepository payments;
    private final CommissionPolicyRepository policies;
    private final DisbursementRepository disbursements;
    private final RefundRepository refunds;
    private final ScheduleDirectory scheduleDirectory;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    @Override
    @Transactional
    public CancellationResponse cancel(UUID touristId, UUID bookingId, String reason) {
        // Existencia y dueño sin bloquear: una reserva ajena responde 404 sin tocar el voucher de nadie.
        PayableBooking visible = bookingPayments.find(bookingId).filter(b -> b.touristId().equals(touristId))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND));
        voucherLock.lockOf(bookingId);
        CancellableBooking booking = bookingCancellations.lock(visible.bookingId())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!booking.isConfirmed()) {
            throw new BusinessRuleException(ErrorCode.BOOKING_NOT_CANCELLABLE).with("status", booking.status());
        }
        PaymentTransaction payment = payments.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId,
                        PaymentStatus.APPROVED)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.BOOKING_NOT_CANCELLABLE));
        CommissionPolicy policy = policies.findById(payment.getCommissionPolicyId())
                .orElseThrow(() -> new IllegalStateException("Payment " + payment.getId() + " has no policy"));
        ScheduleSummary schedule = scheduleDirectory.describe(booking.scheduleId())
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " has no schedule"));

        Instant now = clock.instant();
        if (!schedule.startTime().isAfter(now)) {
            throw new BusinessRuleException(ErrorCode.BOOKING_ALREADY_STARTED);
        }
        Decision decision = CancellationPolicy.decide(schedule.startTime(), now,
                policy.getCancellationThresholdHours(), policy.getEarlyCancelRefundPct());
        boolean early = decision.kind() == Kind.EARLY_REFUND;
        long cancellationId = bookingCancellations.cancelByTourist(bookingId, touristId, reason, early,
                decision.hoursBeforeStart(), decision.touristRefundPct(), now);

        BigDecimal refundAmount = BigDecimal.ZERO;
        String refundStatus = null;
        if (early) {
            refundAmount = MoneyRules.earlyRefund(payment.getAmountCop(), decision.touristRefundPct());
            if (refundAmount.signum() > 0) {
                refundStatus = refunds.saveAndFlush(Refund.earlyCancellation(payment, refundAmount, cancellationId))
                        .getStatus();
            }
            // La comunidad no recibe nada en una cancelación anticipada: el 85 % vuelve al turista.
            disbursements.cancelOfBooking(bookingId, "Cancelación anticipada del turista");
        }
        events.publishEvent(new BookingCancelledEvent(bookingId, now, "Cancelada por el turista"));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("policyApplied", early ? "EARLY_REFUND" : "LATE_NO_REFUND");
        details.put("hoursBeforeStart", decision.hoursBeforeStart().toPlainString());
        details.put("refundAmountCop", refundAmount.longValueExact());
        audit.record(new AuditEntry("payments", "bookings", bookingId.toString(), "BOOKING_CANCELLED",
                AuditEntry.Severity.INFO, touristId, null, null, details));
        return new CancellationResponse(bookingId, early ? "CANCELLED_REFUNDED" : "CANCELLED_NO_REFUND",
                early ? "EARLY_REFUND" : "LATE_NO_REFUND", decision.hoursBeforeStart(), decision.touristRefundPct(),
                refundAmount.longValueExact(), refundStatus, early, now);
    }
}