package com.integrate.modules.payment.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Parámetros financieros versionados ({@code payments.commission_policies}, F6). Solo lectura en la aplicación. */
@Entity
@Immutable
@Table(name = "commission_policies", schema = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    private short id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "community_pct", nullable = false, precision = 5, scale = 2, columnDefinition = PgDomains.PERCENTAGE)
    private BigDecimal communityPct;

    @Column(name = "payout_grace_hours", nullable = false)
    private short payoutGraceHours;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    /** Porción comunitaria del bruto, redondeada con {@code CEIL} a favor de la comunidad (F3, RF-12). */
    public BigDecimal communityShareOf(BigDecimal grossCop) {
        return grossCop.multiply(communityPct).divide(HUNDRED, 0, RoundingMode.CEILING);
    }
}
