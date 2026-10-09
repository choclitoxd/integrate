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

/**
 * Derecho de la asociación sobre un pago aprobado ({@code payments.disbursements}): el 85% en custodia (escrow) hasta
 * su liberación (ADR-0009 §5). Este módulo solo lo crea {@code SCHEDULED}; liberarlo y pagarlo es de la Etapa 2.
 */
@Entity
@Table(name = "disbursements", schema = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disbursement {

    public static final String SCHEDULED = "SCHEDULED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "amount_cop", nullable = false, updatable = false, precision = 14, scale = 2,
            columnDefinition = PgDomains.COP_AMOUNT)
    private BigDecimal amountCop;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "release_at", nullable = false)
    private Instant releaseAt;

    public static Disbursement schedule(UUID paymentId, UUID associationId, BigDecimal amountCop, Instant releaseAt) {
        Disbursement disbursement = new Disbursement();
        disbursement.paymentId = paymentId;
        disbursement.associationId = associationId;
        disbursement.amountCop = amountCop;
        disbursement.status = SCHEDULED;
        disbursement.releaseAt = releaseAt;
        return disbursement;
    }
}
