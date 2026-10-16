package com.integrate.quality.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * RNF-24 (Adecuación funcional · pertinencia): ningún gestor o anfitrión puede ofertar más cupos que la capacidad de
 * carga ambiental de la experiencia. Se intentan todas las vías de parametrización que expone la API.
 */
@Tag("rnf-24")
@Tag("iso-adecuacion")
@DisplayName("[RNF-24] Restricción rigurosa de la capacidad de carga ecológica")
class CapacityParametrizationQualityTest extends QualityIntegrationTest {

    @Test
    @DisplayName("[RNF-24] 100 % de los intentos de superar la capacidad de carga se rechazan en backend")
    void everyAttemptToExceedTheCarryingCapacityIsRejected() throws Exception {
        Scenario s = scenario(8);
        schedule(s, 8); // franja abierta con la capacidad completa

        List<Integer> attempts = new ArrayList<>();
        // 1. Franjas por encima de la capacidad de la experiencia (8).
        for (int slots : new int[] {9, 12, 30}) {
            attempts.add(createSchedule(s, slots).andReturn().getResponse().getStatus());
        }
        // 2. Por encima del máximo absoluto del contrato (30).
        attempts.add(createSchedule(s, 31).andReturn().getResponse().getStatus());
        attempts.add(createSchedule(s, 0).andReturn().getResponse().getStatus());
        // 3. Reducir la capacidad por debajo de una franja ya abierta con 8 cupos (experiencia en borrador, editable).
        String draft = createExperience(s.hostToken(), 8);
        postJson("/api/v1/host/experiences/" + draft + "/schedules", s.hostToken(),
                "{\"schedules\":[{\"localStart\":\"2026-12-01T07:00:00\",\"localEnd\":\"2026-12-01T12:00:00\","
                        + "\"totalSlots\":8}]}").andExpect(status().isCreated());
        attempts.add(mvc.perform(put("/api/v1/host/experiences/" + draft)
                        .header(HttpHeaders.AUTHORIZATION, bearer(s.hostToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(experienceBody(5)))
                .andReturn().getResponse().getStatus());
        // 4. Crear una experiencia con capacidad fuera de rango.
        attempts.add(postJson("/api/v1/host/experiences", s.hostToken(), experienceBody(31))
                .andReturn().getResponse().getStatus());

        long rejected = attempts.stream().filter(code -> code == 422).count();
        long oversized = count("""
                SELECT count(*) FROM catalog.schedules sc JOIN catalog.experiences e ON e.id = sc.experience_id
                 WHERE sc.total_slots > e.max_capacity_per_slot""");

        QualityMetrics.record("RNF-24", "Parametrizaciones que violan la capacidad de carga rechazadas",
                QualityMetrics.percent(rejected, attempts.size()), "%", "= 100 % (422)", rejected == attempts.size(),
                attempts.size() + " intentos (franjas de 9/12/30/31/0 cupos sobre capacidad 8, reducir capacidad "
                        + "bajo una franja abierta, capacidad 31); códigos " + attempts);
        QualityMetrics.record("RNF-24", "Franjas en base con mas cupos que la capacidad de carga", oversized,
                "franjas", "= 0", oversized == 0, "consulta sobre toda la base de prueba");
        assertThat(rejected).isEqualTo(attempts.size());
        assertThat(oversized).isZero();
    }

    private static String experienceBody(int capacity) {
        return """
                {"title":"Sendero con capacidad","description":"Recorrido guiado por la comunidad.",
                 "safetyGuidelines":"Llevar agua.","physicalIntensity":"MEDIUM","durationMinutes":240,
                 "municipalityCode":"05001","vereda":"La Esperanza","meetingPoint":"Parque principal",
                 "latitude":6.25,"longitude":-75.56,"maxCapacityPerSlot":%d,"unitPriceCop":80000,
                 "categoryCodes":["SENDERISMO_MEMORIA"]}""".formatted(capacity);
    }
}
