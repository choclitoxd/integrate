package com.integrate.modules.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.modules.payment.service.impl.CancellationPolicy;
import com.integrate.modules.payment.service.impl.CancellationPolicy.Decision;
import com.integrate.modules.payment.service.impl.CancellationPolicy.Kind;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Regla de 72 horas de ADR-0034 §1 (RF-13, RF-14): Δt estricto en UTC y borde exclusivo hacia el reembolso. */
class CancellationPolicyTest {

    private static final Instant START = Instant.parse("2026-11-10T13:00:00Z");
    private static final BigDecimal PCT_85 = new BigDecimal("85.00");

    private static Decision decide(Duration beforeStart) {
        return CancellationPolicy.decide(START, START.minus(beforeStart), 72, PCT_85);
    }

    @Test
    void moreThanSeventyTwoHoursIsAnEarlyRefundOfEightyFivePercent() {
        Decision decision = decide(Duration.ofHours(72).plusSeconds(1));

        assertThat(decision.kind()).isEqualTo(Kind.EARLY_REFUND);
        assertThat(decision.touristRefundPct()).isEqualByComparingTo("85.00");
        assertThat(decision.hoursBeforeStart()).isEqualByComparingTo("72.00");
    }

    @Test
    void exactlySeventyTwoHoursIsAlreadyLate() {
        Decision decision = decide(Duration.ofHours(72));

        assertThat(decision.kind()).isEqualTo(Kind.LATE_NO_REFUND);
        assertThat(decision.touristRefundPct()).isEqualByComparingTo("0");
    }

    @Test
    void justUnderSeventyTwoHoursIsLate() {
        assertThat(decide(Duration.ofHours(72).minusSeconds(1)).kind()).isEqualTo(Kind.LATE_NO_REFUND);
    }

    @Test
    void farInAdvanceKeepsTheSamePercentage() {
        Decision decision = decide(Duration.ofDays(30));

        assertThat(decision.kind()).isEqualTo(Kind.EARLY_REFUND);
        assertThat(decision.hoursBeforeStart()).isEqualByComparingTo("720.00");
    }

    @Test
    void theThresholdComesFromThePolicyOfThePayment() {
        // Una política antigua con umbral de 48 h: 60 h antes todavía es anticipada con ese umbral.
        Decision decision = CancellationPolicy.decide(START, START.minus(Duration.ofHours(60)), 48,
                new BigDecimal("70.00"));

        assertThat(decision.kind()).isEqualTo(Kind.EARLY_REFUND);
        assertThat(decision.touristRefundPct()).isEqualByComparingTo("70.00");
    }

    @Test
    void theHoursAreStoredWithTwoDecimalsTruncated() {
        // 90 minutos antes del inicio = 1,5 h; 100 minutos = 1,66 h (truncado, no redondeado).
        assertThat(decide(Duration.ofMinutes(90)).hoursBeforeStart()).isEqualByComparingTo("1.50");
        assertThat(decide(Duration.ofMinutes(100)).hoursBeforeStart()).isEqualByComparingTo("1.66");
    }

    @Test
    void cancellingAfterTheStartIsNotAPolicyCase() {
        assertThatThrownBy(() -> CancellationPolicy.decide(START, START, 72, PCT_85))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CancellationPolicy.decide(START, START.plusSeconds(1), 72, PCT_85))
                .isInstanceOf(IllegalArgumentException.class);
    }
}