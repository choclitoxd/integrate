package com.integrate.modules.payment.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Orden de reembolso al turista ({@code payments.refunds}). En T-1.7 solo nace la de aprobación tardía (S3, ADR-0009
 * §5), en {@code REQUESTED}; su ejecución contra la pasarela llega con T-2.6.
 */
@Entity
@Table(name = "refunds", schema = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    public static final String LATE_APPROVAL = "LATE_APPROVAL";
    public static final String REQUESTED = "REQUESTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 30, updatable = false)
    private String reason;

    @Column(name = "amount_cop", nullable = false, updatable = false, precision = 14, scale = 2,
            columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal amountCop;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String status;

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
}
