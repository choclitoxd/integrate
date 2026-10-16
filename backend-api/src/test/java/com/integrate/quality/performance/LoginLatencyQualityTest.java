package com.integrate.quality.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RNF-08 (Eficiencia · comportamiento temporal) y la métrica "Tiempo de respuesta en autenticación" del §3.4.1:
 * verificación Argon2id + emisión del JWT bajo carga nominal. Indicativa en proceso; la formal es
 * {@code qa/k6/login.js}.
 */
@Tag("rnf-08")
@Tag("iso-eficiencia")
@Tag("performance")
@DisplayName("[RNF-08] Latencia en emisión y verificación de tokens JWT")
class LoginLatencyQualityTest extends QualityIntegrationTest {

    private static final int USERS = 8;
    private static final int LOGINS = 64;
    private static final int CONCURRENCY = 4;

    @Test
    @DisplayName("[RNF-08] p95 de POST /auth/login < 800 ms bajo carga nominal")
    void loginLatencyStaysUnderBudget() throws Exception {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            emails.add(registerTourist());
        }
        for (String email : emails) {
            tokenFor(email); // calentamiento
        }

        List<long[]> samples = runConcurrently(CONCURRENCY, LOGINS, i -> {
            long t0 = System.nanoTime();
            int status = login(emails.get(i % USERS)).andReturn().getResponse().getStatus();
            return new long[] {System.nanoTime() - t0, status};
        });

        List<Long> durations = samples.stream().map(x -> x[0]).toList();
        long ok = samples.stream().filter(x -> x[1] == 200).count();
        double p95 = QualityMetrics.percentile(durations, 95);
        double mean = durations.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;

        QualityMetrics.record("RNF-08", "Tiempo de respuesta en autenticacion (p95)", p95, "ms", "p95 < 800 ms",
                p95 < 800 && ok == LOGINS, LOGINS + " logins, " + CONCURRENCY + " concurrentes; media = "
                        + String.format("%.1f", mean) + " ms; Argon2id m=19 MiB t=2; en proceso (indicativo)");
        assertThat(ok).isEqualTo(LOGINS);
        assertThat(p95).isLessThan(800);
    }
}
