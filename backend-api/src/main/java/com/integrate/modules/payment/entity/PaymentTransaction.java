package com.integrate.modules.payment.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Intento de cobro de una reserva ({@code payments.payment_transactions}, ADR-0009 §3). El identificador se asigna
 * en la aplicación porque también es la referencia que viaja a la pasarela ({@code gateway_reference}).
 * {@code currency} no se mapea: su único valor es el {@code DEFAULT 'COP'}.
 */
@Entity
@Table(name = "payment_transactions", schema = "payments")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    private static final int MAX_DETAIL = 255;

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "commission_policy_id", nullable = false, updatable = false)
    private short commissionPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @Column(name = "gateway_reference", length = 100, updatable = false)
    private String gatewayReference;

    @Column(name = "amount_cop", nullable = false, updatable = false, precision = 14, scale = 2,
            columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal amountCop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "status_detail", length = MAX_DETAIL)
    private String statusDetail;

    /** Lo fija el trigger al pasar a {@code APPROVED}. */
    @Column(name = "approved_at", insertable = false, updatable = false)
    private Instant approvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Intento nuevo en {@code INITIATED} con la política vigente congelada y el total exacto de la reserva. */
    public static PaymentTransaction initiate(UUID id, UUID bookingId, short commissionPolicyId,
                                              PaymentProvider provider, PaymentMethod method, BigDecimal amountCop) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.id = id;
        payment.bookingId = bookingId;
        payment.commissionPolicyId = commissionPolicyId;
        payment.provider = provider;
        payment.paymentMethod = method;
        payment.idempotencyKey = "pay:" + id;
        payment.gatewayReference = id.toString();
        payment.amountCop = amountCop;
        payment.status = PaymentStatus.INITIATED;
        return payment;
    }

    /** El turista cambió de medio antes de que la pasarela respondiera. */
    public void changeMethod(PaymentMethod method) {
        if (status == PaymentStatus.INITIATED) {
            this.paymentMethod = method;
        }
    }

    /** Identificador de la transacción en la pasarela; se fija con el primer evento que la menciona. */
    public void attachGatewayTransaction(String gatewayTransactionId) {
        if (gatewayTransactionId != null && !gatewayTransactionId.equals(this.gatewayTransactionId)) {
            this.gatewayTransactionId = gatewayTransactionId;
        }
    }

    public void moveTo(PaymentStatus target, String detail) {
        if (status.isFinal()) {
            throw new IllegalStateException("Payment " + id + " is already " + status);
        }
        this.status = target;
        this.statusDetail = detail == null || detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }

    public long amountCopValue() {
        return amountCop.longValueExact();
    }
}
