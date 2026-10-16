package com.integrate.quality.reliability;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RNF-16 (Fiabilidad · consistencia ACID) y la métrica "Tasa de sobreventa de cupos" del §3.4.2: prueba de estrés
 * transaccional con muchas más solicitudes que cupos, repartidas entre varias franjas a la vez y con tamaños de
 * reserva mezclados, para forzar contención real sobre el {@code SELECT ... FOR UPDATE} de cada franja.
 */
@Tag("rnf-16")
@Tag("iso-fiabilidad")
@DisplayName("[RNF-16] Consistencia transaccional y tasa cero de overbooking")
class OverbookingStressQualityTest extends QualityIntegrationTest {

    private static final int REQUESTS = 150;
    private static final int THREADS = 24;

    @Test
    @DisplayName("[RNF-16] 150 solicitudes concurrentes contra 15 cupos: sobreventa 0,0 % y sin deriva de inventario")
    void contentionNeverOversells() throws Exception {
        Scenario s = scenario(12);
        List<String> schedules = List.of(schedule(s, 3), schedule(s, 5), schedule(s, 7));
        List<String> tourists = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tourists.add(tokenFor(registerTourist()));
        }
        Random random = new Random(20261010L);
        List<int[]> plan = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            plan.add(new int[] {random.nextInt(schedules.size()), 1 + random.nextInt(2), random.nextInt(tourists.size())});
        }

        List<Integer> statuses = runConcurrently(THREADS, REQUESTS, i -> {
            int[] p = plan.get(i);
            return lock(tourists.get(p[2]), schedules.get(p[0]), p[1]).andReturn().getResponse().getStatus();
        });

        Map<Integer, Long> byStatus = statuses.stream().collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        long created = byStatus.getOrDefault(201, 0L);
        long unexpected = statuses.stream().filter(x -> x != 201 && x != 409 && x != 503).count();
        long oversoldSlots = 0;
        long soldSlots = 0;
        long drift = 0;
        for (String scheduleId : schedules) {
            long sold = count("""
                    SELECT coalesce(sum(slots_count), 0) FROM booking.bookings
                     WHERE schedule_id = ?::uuid AND status IN ('PENDING', 'CONFIRMED')""", scheduleId);
            int total = totalSlots(scheduleId);
            soldSlots += sold;
            oversoldSlots += Math.max(0, sold - total);
            drift += sold + availableSlots(scheduleId) == total ? 0 : 1;
            drift += Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT has_drift FROM booking.v_schedule_slot_reconciliation WHERE schedule_id = ?::uuid""",
                    Boolean.class, scheduleId)) ? 1 : 0;
        }
        double oversellRate = QualityMetrics.percent(oversoldSlots, Math.max(1, soldSlots));

        QualityMetrics.record("RNF-16", "Tasa de sobreventa de cupos (overbooking)", oversellRate, "%", "= 0,0 %",
                oversoldSlots == 0, REQUESTS + " solicitudes con " + THREADS + " hilos contra 3 franjas (3+5+7 = 15 "
                        + "cupos); respuestas " + byStatus + "; cupos vendidos " + soldSlots);
        QualityMetrics.record("RNF-16", "Franjas con deriva entre inventario y reservas", drift, "franjas", "= 0",
                drift == 0, "available_slots + reservados = total_slots y v_schedule_slot_reconciliation");
        QualityMetrics.record("RNF-02", "Respuestas inesperadas bajo concurrencia (deadlocks o 5xx)", unexpected,
                "respuestas", "= 0", unexpected == 0, "solo se aceptan 201, 409 (sin cupos) y 503 (lock_timeout "
                        + "con Retry-After)");
        assertThat(created).isPositive();
        assertThat(oversoldSlots).isZero();
        assertThat(drift).isZero();
        assertThat(unexpected).isZero();
    }
}
