package com.integrate.modules.payment.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Regla de cancelación de 72 horas (RF-13, RF-14, ADR-0034 §1): función pura. Δt = inicio − cancelación en UTC;
 * {@code Δt > umbral} (estricto) es reembolso anticipado, {@code Δt ≤ umbral} no tiene reembolso.
 */
public final class CancellationPolicy {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private CancellationPolicy() {
    }

    public enum Kind {
        EARLY_REFUND, LATE_NO_REFUND
    }

    /**
     * @param hoursBeforeStart evidencia guardada en {@code booking_cancellations}: Δt en horas, truncado a 2 decimales
     * @param touristRefundPct 0 si es tardía
     */
    public record Decision(Kind kind, BigDecimal hoursBeforeStart, BigDecimal touristRefundPct) {
    }

    /** @throws IllegalArgumentException si la franja ya empezó: no es un caso de política sino de rechazo */
    public static Decision decide(Instant scheduleStart, Instant cancelledAt, int thresholdHours,
                                  BigDecimal earlyRefundPct) {
        Duration before = Duration.between(cancelledAt, scheduleStart);
        if (before.isZero() || before.isNegative()) {
            throw new IllegalArgumentException("The schedule already started at " + scheduleStart);
        }
        BigDecimal hours = BigDecimal.valueOf(before.getSeconds()).divide(SECONDS_PER_HOUR, 2, RoundingMode.DOWN);
        boolean early = before.compareTo(Duration.ofHours(thresholdHours)) > 0;
        return early ? new Decision(Kind.EARLY_REFUND, hours, earlyRefundPct)
                : new Decision(Kind.LATE_NO_REFUND, hours, BigDecimal.ZERO);
    }
}