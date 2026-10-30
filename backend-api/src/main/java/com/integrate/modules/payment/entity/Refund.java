package com.integrate.modules.payment.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Orden de reembolso al turista ({@code payments.refunds}). Nace {@code REQUESTED} (aprobación tardía, ADR-0009 §5;
 * cancelación anticipada, ADR-0034 §2) y la ejecuta el trabajador de reembolsos contra la pasarela:
 * {@code REQUESTED → PROCESSING → COMPLETED | FAILED}.
 */
@Entity
@Table(name = "refunds", schema = "payments")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    public static final String LATE_APPROVAL = "LATE_APPROVAL";
    public static final String EARLY_CANCELLATION = "EARLY_CANCELLATION";
    public static final String REQUESTED = "REQUESTED";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "booking_cancellation_id", updatable = false)
    private Long bookingCancellationId;

    @Column(nullable = false, length = 30, updatable = false)
    private String reason;

    @Column(name = "amount_cop", nullable = false, updatable = false, precision = 14, scale = 2,
            columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal amountCop;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "gateway_refund_id", length = 100)
    private String gatewayRefundId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Lo fija el trigger al pasar a {@code COMPLETED}. */
    @Column(name = "completed_at", insertable = false, updatable = false)
    private Instant completedAt;

    /** 100% del cobro: la pasarela aprobó cuando la reserva ya había expirado o fallado. */
    public static Refund lateApproval(PaymentTransaction payment) {
        Refund refund = new Refund();
        refund.paymentId = payment.getId();
        refund.reason = LATE_APPROVAL;
        refund.amountCop = payment.getAmountCop();
        refund.idempotencyKey = "refund:late:" + payment.getId();
        refund.status = REQUESTED;
        return refund;
    }

    /** Reembolso de una cancelación anticipada (RF-13): el monto ya viene redondeado por {@code MoneyRules}. */
    public static Refund earlyCancellation(PaymentTransaction payment, BigDecimal amountCop, long cancellationId) {
        Refund refund = new Refund();
        refund.paymentId = payment.getId();
        refund.bookingCancellationId = cancellationId;
        refund.reason = EARLY_CANCELLATION;
        refund.amountCop = amountCop;
        refund.idempotencyKey = "refund:cancel:" + cancellationId;
        refund.status = REQUESTED;
        return refund;
    }

    public boolean isOpen() {
        return REQUESTED.equals(status) || PROCESSING.equals(status);
    }

    public void markProcessing() {
        this.status = PROCESSING;
    }

    public void complete(String gatewayRefundId) {
        this.gatewayRefundId = gatewayRefundId;
        this.status = COMPLETED;
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.status = FAILED;
    }
}