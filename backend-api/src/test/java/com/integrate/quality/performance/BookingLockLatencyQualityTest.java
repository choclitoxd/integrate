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
 * RNF-02 (Eficiencia · comportamiento temporal y capacidad): latencia del bloqueo pesimista de aforo medida dentro
 * del proceso (MockMvc + PostgreSQL en Testcontainers). Es evidencia <em>indicativa</em>: no incluye red ni TLS. La
 * medición formal con tráfico HTTP real es {@code qa/k6/booking-lock.js} (ADR-0019 §5).
 */
@Tag("rnf-02")
@Tag("iso-eficiencia")
@Tag("performance")
@DisplayName("[RNF-02] Rendimiento y tiempo de respuesta en el bloqueo transaccional")
class BookingLockLatencyQualityTest extends QualityIntegrationTest {

    private static final int REQUESTS = 100;
    private static final int CONCURRENCY = 10;

    @Test
    @DisplayName("[RNF-02] p95 del bloqueo de aforo <= 200 ms con 100 transacciones concurrentes")
    void lockLatencyStaysUnderBudget() throws Exception {
        Scenario s = scenario(30);
        List<String> schedules = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schedules.add(schedule(s, 30));
        }
        List<String> tourists = new ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            tourists.add(tokenFor(registerTourist()));
        }
        // Calentamiento: JIT, pool de conexiones y planes de consulta.
        for (int i = 0; i < 10; i++) {
            lockBooking(tourists.get(i % tourists.size()), schedules.get(i % schedules.size()), 1);
        }

        long started = System.nanoTime();
        List<long[]> samples = runConcurrently(CONCURRENCY, REQUESTS, i -> {
            long t0 = System.nanoTime();
            int status = lock(tourists.get(i % tourists.size()), schedules.get(i % schedules.size()), 1)
                    .andReturn().getResponse().getStatus();
            return new long[] {System.nanoTime() - t0, status};
        });
        double elapsedSeconds = (System.nanoTime() - started) / 1e9;

        List<Long> durations = samples.stream().map(x -> x[0]).toList();
        long created = samples.stream().filter(x -> x[1] == 201).count();
        double p95 = QualityMetrics.percentile(durations, 95);
        double p50 = QualityMetrics.percentile(durations, 50);
        double perMinute = REQUESTS / elapsedSeconds * 60;

        QualityMetrics.record("RNF-02", "Tiempo de bloqueo transaccional de aforo (p95)", p95, "ms", "p95 <= 200 ms",
                p95 <= 200, REQUESTS + " bloqueos, " + CONCURRENCY + " concurrentes sobre 5 franjas; p50 = "
                        + String.format("%.1f", p50) + " ms; en proceso (indicativo, ver k6)");
        QualityMetrics.record("RNF-02", "Transacciones de bloqueo por minuto sostenidas", perMinute, "tx/min",
                ">= 100 tx/min", perMinute >= 100 && created == REQUESTS, created + " de " + REQUESTS
                        + " creadas en " + String.format("%.2f", elapsedSeconds) + " s");
        assertThat(created).isEqualTo(REQUESTS);
        assertThat(p95).isLessThanOrEqualTo(200);
        assertThat(perMinute).isGreaterThanOrEqualTo(100);
    }
}
