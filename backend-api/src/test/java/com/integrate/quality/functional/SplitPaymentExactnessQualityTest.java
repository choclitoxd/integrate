package com.integrate.quality.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RNF-21 (Adecuación funcional · corrección), en la parte que ya existe: al confirmar cada pago se deja en custodia
 * la participación de la comunidad según la política vigente (85 %, redondeo a favor de la comunidad: CEIL) y el
 * resto es la comisión de sostenibilidad (15 %). La orden de giro bancario es de la Fase 2 (T-2.5).
 */
@Tag("rnf-21")
@Tag("iso-adecuacion")
@DisplayName("[RNF-21] Exactitud algorítmica de la dispersión 85/15 (custodia)")
class SplitPaymentExactnessQualityTest extends QualityIntegrationTest {

    /** Precios que no dan un 85 % entero, para ejercitar el redondeo. */
    private static final long[] PRICES = {80_001, 33_333, 12_347};

    @Test
    @DisplayName("[RNF-21] 100 % de los pagos confirmados quedan liquidados exactamente 85/15")
    void everyConfirmedPaymentIsSplitExactly() throws Exception {
        Scenario s = scenario(12);
        Map<String, Object> policy = jdbc.queryForMap("""
                SELECT community_pct, platform_pct FROM payments.commission_policies WHERE code = 'STANDARD_2026'""");
        BigDecimal communityPct = (BigDecimal) policy.get("community_pct");

        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (long price : PRICES) {
            String experienceId = createExperience(s.hostToken(), 12, price);
            postJson("/api/v1/host/experiences/" + experienceId + "/submit", s.hostToken(), null)
                    .andExpect(status().isOk());
            postJson("/api/v1/admin/experiences/" + experienceId + "/approve", s.adminToken(), null)
                    .andExpect(status().isOk());
            String scheduleId = JsonPath.read(createSchedule(s.hostToken(), experienceId, 12)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$[0].id");
            for (int slots = 1; slots <= 4; slots++) {
                cases++;
                String paymentId = startPayment(s.touristToken(), lockBooking(s.touristToken(), scheduleId, slots));
                approvePayment(s.touristToken(), paymentId);
                BigDecimal total = BigDecimal.valueOf(price * slots);
                BigDecimal community = jdbc.queryForObject(
                        "SELECT amount_cop FROM payments.disbursements WHERE payment_id = ?::uuid", BigDecimal.class,
                        paymentId);
                BigDecimal expected = total.multiply(communityPct).divide(BigDecimal.valueOf(100), 0,
                        RoundingMode.CEILING);
                BigDecimal platform = total.subtract(community);
                BigDecimal platformPct = platform.multiply(BigDecimal.valueOf(100)).divide(total, 4,
                        RoundingMode.HALF_UP);
                if (community.compareTo(expected) != 0 || community.add(platform).compareTo(total) != 0
                        || platformPct.compareTo(BigDecimal.valueOf(15)) > 0) {
                    failures.add("total " + total + " → comunidad " + community + " (esperado " + expected
                            + "), plataforma " + platform);
                }
            }
        }

        QualityMetrics.record("RNF-21", "Exactitud en dispersion financiera 85/15 (custodia)",
                QualityMetrics.percent(cases - failures.size(), cases), "%", "= 100 % (cero errores de liquidación)",
                failures.isEmpty(), cases + " pagos confirmados con precios " + java.util.Arrays.toString(PRICES)
                        + " × 1-4 cupos; comunidad = CEIL(total × " + communityPct + " %), plataforma = resto");
        assertThat(failures).isEmpty();
    }
}
