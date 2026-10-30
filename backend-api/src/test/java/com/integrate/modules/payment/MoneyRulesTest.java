package com.integrate.modules.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.payment.service.impl.MoneyRules;
import com.integrate.modules.payment.service.impl.MoneyRules.SettlementBreakdown;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Reglas de dinero de ADR-0034 §1: enteros en COP, redondeo explícito y sin perder ni inventar un peso. */
class MoneyRulesTest {

    private static final BigDecimal PCT_85 = new BigDecimal("85.00");

    private static BigDecimal cop(long value) {
        return BigDecimal.valueOf(value);
    }

    @ParameterizedTest(name = "bruto {0} -> comunidad {1}, plataforma {2}, reembolso {3}")
    @CsvSource({
            "100000, 85000, 15000, 85000",
            "99999,  85000, 14999, 84999",
            "1,      1,     0,     0",
            "3,      3,     0,     2",
            "7,      6,     1,     5",
            "20000,  17000, 3000,  17000"})
    void knownAmountsSplitWithTheDocumentedRounding(long gross, long community, long platform, long refund) {
        BigDecimal communityShare = MoneyRules.communityShare(cop(gross), PCT_85);

        assertThat(communityShare).isEqualByComparingTo(cop(community));
        assertThat(MoneyRules.platformShare(cop(gross), communityShare)).isEqualByComparingTo(cop(platform));
        assertThat(MoneyRules.earlyRefund(cop(gross), PCT_85)).isEqualByComparingTo(cop(refund));
    }

    @Test
    void forEveryGrossTheSplitAddsUpAndTheRefundNeverExceedsEightyFivePercent() {
        for (long gross = 1; gross <= 20_000; gross++) {
            BigDecimal g = cop(gross);
            BigDecimal community = MoneyRules.communityShare(g, PCT_85);
            BigDecimal platform = MoneyRules.platformShare(g, community);
            BigDecimal refund = MoneyRules.earlyRefund(g, PCT_85);

            assertThat(community.add(platform)).isEqualByComparingTo(g);
            assertThat(platform.signum()).isGreaterThanOrEqualTo(0);
            // CEIL a favor de la comunidad; FLOOR a favor de la plataforma para el reembolso.
            assertThat(community.multiply(cop(100))).isGreaterThanOrEqualTo(g.multiply(PCT_85));
            assertThat(refund.multiply(cop(100))).isLessThanOrEqualTo(g.multiply(PCT_85));
            assertThat(refund).isLessThanOrEqualTo(community);
            // Lo que se retiene del turista nunca supera lo que se le cobró.
            assertThat(g.subtract(refund).signum()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void theSettlementBreakdownIsDerivedFromTheBatchTotals() {
        // Tres pagos de un mismo lote: 100000, 99999 y 40000 con comisión de pasarela de 3000, 2999 y 1200.
        List<BigDecimal> gross = List.of(cop(100000), cop(99999), cop(40000));
        BigDecimal totalGross = gross.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.stream().map(g -> MoneyRules.communityShare(g, PCT_85))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SettlementBreakdown breakdown = MoneyRules.settlement(totalGross, cop(3000 + 2999 + 1200), net);

        assertThat(breakdown.grossCop()).isEqualByComparingTo(cop(239999));
        assertThat(breakdown.netCommunityCop()).isEqualByComparingTo(cop(85000 + 85000 + 34000));
        assertThat(breakdown.platformFeeCop()).isEqualByComparingTo(cop(239999 - 204000));
        assertThat(breakdown.gatewayFeeCop()).isEqualByComparingTo(cop(7199));
        // El CHECK de settlement_receipts: bruto = neto + comisión de plataforma.
        assertThat(breakdown.netCommunityCop().add(breakdown.platformFeeCop())).isEqualByComparingTo(totalGross);
    }
}