package com.integrate.quality.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.booking.config.BookingProperties;
import com.integrate.modules.booking.service.BookingExpirationService;
import com.integrate.modules.booking.service.model.ExpiredBatch;
import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RNF-11 (Eficiencia · utilización de recursos) y la métrica "Efectividad en liberación de cupos (TTL)" del
 * §3.4.2: un barrido completo del worker de expiración sobre cientos de reservas vencidas, con lecturas del catálogo
 * corriendo en paralelo para comprobar que el barrido no bloquea las tablas de consulta.
 */
@Tag("rnf-11")
@Tag("iso-eficiencia")
@Tag("performance")
@DisplayName("[RNF-11] Eficiencia del worker de expiración TTL")
class ExpirationSweepQualityTest extends QualityIntegrationTest {

    private static final int SCHEDULES = 14;
    private static final int BOOKINGS = 400;

    @Autowired
    private BookingExpirationService expirationService;

    @Autowired
    private BookingProperties properties;

    @Test
    @DisplayName("[RNF-11] Barrido de 400 reservas vencidas < 1,5 s, 100 % de cupos liberados y catálogo disponible")
    void sweepReleasesEverythingQuicklyWithoutBlockingReads() throws Exception {
        Scenario s = scenario(30);
        List<String> schedules = new ArrayList<>();
        for (int i = 0; i < SCHEDULES; i++) {
            schedules.add(schedule(s, 30));
        }
        runConcurrently(8, BOOKINGS, i -> lockBooking(s.touristToken(), schedules.get(i % SCHEDULES), 1));
        long locked = schedules.stream().mapToLong(id -> totalSlots(id) - availableSlots(id)).sum();
        assertThat(locked).isEqualTo(BOOKINGS);

        clock.advance(Duration.ofMinutes(16));
        AtomicBoolean sweeping = new AtomicBoolean(true);
        CompletableFuture<List<long[]>> reader = CompletableFuture.supplyAsync(() -> {
            List<long[]> reads = new ArrayList<>();
            while (sweeping.get()) {
                long t0 = System.nanoTime();
                try {
                    int status = getJson(availabilityPath(s.experienceId()), null)
                            .andReturn().getResponse().getStatus();
                    reads.add(new long[] {System.nanoTime() - t0, status});
                    Thread.sleep(10); // un turista consultando, no un bucle que compita por la CPU del barrido
                } catch (Exception e) {
                    reads.add(new long[] {System.nanoTime() - t0, 599});
                }
            }
            return reads;
        });

        int batchSize = properties.expiration().batchSize();
        long started = System.nanoTime();
        int expired = 0;
        int released = 0;
        ExpiredBatch batch;
        do {
            batch = expirationService.expireBatch(batchSize);
            expired += batch.expired();
            released += batch.releasedSlots();
        } while (batch.expired() == batchSize);
        double sweepMs = (System.nanoTime() - started) / 1e6;
        sweeping.set(false);
        List<long[]> reads = reader.get();

        long restored = schedules.stream().filter(id -> availableSlots(id) == totalSlots(id)).count();
        // La base es compartida con otras clases de prueba: el barrido también expira sus reservas pendientes.
        long releasedOwn = schedules.stream().mapToLong(this::availableSlots).sum() - (long) SCHEDULES * 30 + BOOKINGS;
        long failedReads = reads.stream().filter(r -> r[1] != 200).count();
        double readP95 = reads.isEmpty() ? 0 : QualityMetrics.percentile(reads.stream().map(r -> r[0]).toList(), 95);

        QualityMetrics.record("RNF-11", "Tiempo de ejecucion por barrido del worker TTL", sweepMs, "ms", "< 1500 ms",
                sweepMs < 1500, expired + " reservas vencidas en lotes de " + batchSize + " (FOR UPDATE SKIP LOCKED)");
        QualityMetrics.record("RNF-11", "Efectividad en liberacion de cupos (TTL 15 min)",
                QualityMetrics.percent(releasedOwn, BOOKINGS), "%", "= 100 %", releasedOwn == BOOKINGS
                        && restored == SCHEDULES, releasedOwn + " de " + BOOKINGS + " cupos devueltos; " + restored
                        + " de " + SCHEDULES + " franjas con inventario completo");
        QualityMetrics.record("RNF-11", "Lecturas del catalogo fallidas durante el barrido", failedReads, "lecturas",
                "= 0 (sin bloquear tablas de consulta)", failedReads == 0, reads.size()
                        + " lecturas de disponibilidad en paralelo; p95 = " + String.format("%.1f", readP95) + " ms");
        assertThat(expired).isGreaterThanOrEqualTo(BOOKINGS);
        assertThat(released).isGreaterThanOrEqualTo(BOOKINGS);
        assertThat(releasedOwn).isEqualTo(BOOKINGS);
        assertThat(restored).isEqualTo(SCHEDULES);
        assertThat(failedReads).isZero();
        assertThat(sweepMs).isLessThan(1500);
    }
}
