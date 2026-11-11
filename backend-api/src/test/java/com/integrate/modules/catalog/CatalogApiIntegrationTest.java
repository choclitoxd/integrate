package com.integrate.modules.catalog;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.catalog.service.SlotInventory;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Catálogo de punta a punta (ADR-0006 §8) sobre la cadena real de Spring Security y PostgreSQL:
 * alta de asociación y anfitrión, ciclo de moderación con historial, franjas, consulta pública y
 * disponibilidad del contrato.
 */
@AutoConfigureMockMvc
class CatalogApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SlotInventory slotInventory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String adminToken;
    private String associationId;
    private Host host;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor(userWithRole("admin", RoleCode.ADMIN));
        associationId = createAssociation();
        host = createHost();
    }

    // ------------------------------------------------------- admin: asociaciones y anfitriones

    @Test
    void associationRulesAndAdminOnlyAccess() throws Exception {
        String nit = uniqueNit();
        String rnt = uniqueRnt();
        mvc.perform(postJson("/api/v1/admin/associations", adminToken, associationBody(nit, rnt, "05001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(postJson("/api/v1/admin/associations", adminToken, associationBody(nit, uniqueRnt(), "05001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ASSOCIATION_EXISTS"));
        mvc.perform(postJson("/api/v1/admin/associations", adminToken, associationBody(uniqueNit(), uniqueRnt(), "00000")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("municipalityCode"));
        mvc.perform(postJson("/api/v1/admin/associations", host.token(),
                        associationBody(uniqueNit(), uniqueRnt(), "05001")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RBAC_FORBIDDEN"));
    }

    @Test
    void adminCanUpdatePublishedExperienceAndManageAssociationLifecycle() throws Exception {
        String id = approvedExperience(host, "Sendero de la Memoria");
        mvc.perform(put("/api/v1/admin/experiences/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(experienceBody("Sendero de la Memoria (Edición Admin)", 14)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sendero de la Memoria (Edición Admin)"))
                .andExpect(jsonPath("$.maxCapacityPerSlot").value(14))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String nit = uniqueNit();
        String rnt = uniqueRnt();
        String assocJson = mvc.perform(postJson("/api/v1/admin/associations", adminToken,
                        associationBody(nit, rnt, "05001")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String assocId = JsonPath.read(assocJson, "$.id");

        mvc.perform(put("/api/v1/admin/associations/" + assocId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(associationBody(nit, rnt, "05649")
                                .replace("Asociación Campesina La Esperanza", "Asociación Renovada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Asociación Renovada"))
                .andExpect(jsonPath("$.municipalityCode").value("05649"));

        action(adminToken, "/api/v1/admin/associations/" + assocId + "/suspend", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        action(adminToken, "/api/v1/admin/associations/" + assocId + "/activate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void hostProfileGrantsHostRoleOnlyOnce() throws Exception {
        assertThat(JsonPath.<List<String>>read(login(host.email()), "$.user.roles")).contains("ROLE_HOST");

        mvc.perform(postJson("/api/v1/admin/host-profiles", adminToken,
                        "{\"userId\":\"" + host.userId() + "\",\"associationId\":\"" + associationId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_HOST_PROFILE_EXISTS"));
        mvc.perform(postJson("/api/v1/admin/host-profiles", adminToken,
                        "{\"userId\":\"" + UUID.randomUUID() + "\",\"associationId\":\"" + associationId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------- ciclo de vida del anfitrión (ADR-0070)

    @Test
    void suspendedHostCannotChangeAnythingAndReinstatementRestoresIt() throws Exception {
        String profileId = hostProfileId(host);
        String experienceId = approvedExperience(host, "Taller de tejido");

        mvc.perform(get("/api/v1/host/status").header(HttpHeaders.AUTHORIZATION, bearer(host.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.hostProfileId").value(profileId));

        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/suspend",
                "{\"reason\":\"Visita de verificación pendiente\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail
                 WHERE action = 'HOST_SUSPENDED' AND severity = 'WARNING' AND entity_id = ?""",
                Long.class, profileId)).as("el cambio queda en la bitácora con su motivo").isEqualTo(1L);

        // Leer sigue permitido; cambiar algo, no (el JWT conserva ROLE_HOST: suspender no toca el rol).
        mvc.perform(get("/api/v1/host/experiences").header(HttpHeaders.AUTHORIZATION, bearer(host.token())))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/host/experiences", host.token(), experienceBody("Otra experiencia", 8)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_HOST_NOT_ACTIVE"));
        action(host.token(), "/api/v1/host/experiences/" + experienceId + "/submit", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_HOST_NOT_ACTIVE"));
        mvc.perform(postJson("/api/v1/host/experiences/" + experienceId + "/schedules", host.token(),
                        "{\"schedules\":[" + slot(daysAhead(30), 7, 12, 8) + "]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_HOST_NOT_ACTIVE"));
        mvc.perform(get("/api/v1/host/status").header(HttpHeaders.AUTHORIZATION, bearer(host.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/admin/host-profiles").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.hostProfileId == '" + profileId + "')].status",
                        hasItem("SUSPENDED")));

        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/reinstate", "{\"reason\":\"Verificación completa\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(postJson("/api/v1/host/experiences", host.token(), experienceBody("Otra experiencia", 8)))
                .andExpect(status().isCreated());
    }

    @Test
    void revokingAHostRemovesTheRoleKeepsTouristAndIsFinal() throws Exception {
        String profileId = hostProfileId(host);

        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/revoke", "{\"reason\":\"Salió de la plataforma\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));

        assertThat(activeRole(host.userId(), "HOST")).as("ROLE_HOST revocado").isZero();
        assertThat(activeRole(host.userId(), "TOURIST")).as("ROLE_TOURIST se conserva (ADR-0064)").isEqualTo(1L);
        assertThat(JsonPath.<List<String>>read(login(host.email()), "$.user.roles"))
                .contains("ROLE_TOURIST").doesNotContain("ROLE_HOST");
        // El token viejo aún trae el rol (dura hasta 15 min): el estado se puede consultar y ya no deja operar.
        mvc.perform(get("/api/v1/host/status").header(HttpHeaders.AUTHORIZATION, bearer(host.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        mvc.perform(postJson("/api/v1/host/experiences", host.token(), experienceBody("Nueva", 8)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_HOST_NOT_ACTIVE"));

        for (String transition : List.of("reinstate", "suspend", "revoke")) {
            action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/" + transition, "{\"reason\":\"Otra vez\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATUS_TRANSITION"))
                    .andExpect(jsonPath("$.currentStatus").value("REVOKED"));
        }
    }

    @Test
    void experiencesOfASuspendedHostLeaveThePublicCatalogAndCannotBeBooked() throws Exception {
        String marker = "Suspension" + SEQ.incrementAndGet();
        String profileId = hostProfileId(host);
        String experienceId = approvedExperience(host, marker + " caminata");
        List<String> scheduleIds = createSchedules(host, experienceId, daysAhead(20), 1, 8);
        LocalDate day = daysAhead(20);
        mvc.perform(get("/api/v1/experiences").param("q", marker.toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(1));

        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/suspend", "{\"reason\":\"Alerta\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/experiences").param("q", marker.toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/experiences/" + experienceId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/experiences/" + experienceId + "/availability")
                        .param("dateFrom", day.toString()).param("dateTo", day.plusDays(3).toString()))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> slotInventory.reserve(UUID.fromString(scheduleIds.get(0)), 1, Instant.now())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/reinstate", "{\"reason\":\"Resuelto\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/experiences").param("q", marker.toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/experiences/" + experienceId + "/availability")
                        .param("dateFrom", day.toString()).param("dateTo", day.plusDays(3).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schedules.length()").value(1));
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> slotInventory.reserve(UUID.fromString(scheduleIds.get(0)), 1, Instant.now()));
    }

    @Test
    void onlyAnAdminChangesAHostStatusAndTheReasonIsRequired() throws Exception {
        String profileId = hostProfileId(host);
        String reason = "{\"reason\":\"Motivo\"}";

        action(host.token(), "/api/v1/admin/host-profiles/" + profileId + "/suspend", reason)
                .andExpect(status().isForbidden());
        action(tokenFor(registeredTourist()), "/api/v1/admin/host-profiles/" + profileId + "/suspend", reason)
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/host-profiles/" + profileId + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isUnauthorized());
        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/suspend", "{}")
                .andExpect(status().isUnprocessableEntity());
        action(adminToken, "/api/v1/admin/host-profiles/" + profileId + "/suspend", "{\"reason\":\"  \"}")
                .andExpect(status().isUnprocessableEntity());
        action(adminToken, "/api/v1/admin/host-profiles/" + UUID.randomUUID() + "/suspend", reason)
                .andExpect(status().isNotFound());
        // Nada de lo anterior cambió el estado.
        mvc.perform(get("/api/v1/host/status").header(HttpHeaders.AUTHORIZATION, bearer(host.token())))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        // Un turista sin perfil de anfitrión no consulta el estado de anfitrión.
        mvc.perform(get("/api/v1/host/status").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(registeredTourist()))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- ciclo de moderación

    @Test
    void fullModerationLifecycleIsTracedWithActorAndReason() throws Exception {
        String id = createExperience(host, "Sendero de la Memoria");
        assertThat(jdbc.queryForObject("SELECT created_by FROM catalog.experiences WHERE id = ?::uuid", UUID.class, id))
                .as("created_by = actor propagado").isEqualTo(host.userId());

        action(host.token(), "/api/v1/host/experiences/" + id + "/submit", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        action(adminToken, "/api/v1/admin/experiences/" + id + "/reject", "{\"reason\":\"Falta aval asociativo\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Falta aval asociativo"));
        mvc.perform(put("/api/v1/host/experiences/" + id).header(HttpHeaders.AUTHORIZATION, bearer(host.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(experienceBody("Sendero de la Memoria v2", 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        action(host.token(), "/api/v1/host/experiences/" + id + "/submit", null).andExpect(status().isOk());
        action(adminToken, "/api/v1/admin/experiences/" + id + "/approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        action(adminToken, "/api/v1/admin/experiences/" + id + "/approve", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("APPROVED"));

        List<Map<String, Object>> history = jdbc.queryForList("""
                SELECT from_status, to_status, reason, changed_by FROM catalog.experience_status_history
                 WHERE experience_id = ?::uuid ORDER BY id""", id);
        assertThat(history).extracting(h -> h.get("to_status"))
                .containsExactly("DRAFT", "PENDING_REVIEW", "REJECTED", "DRAFT", "PENDING_REVIEW", "APPROVED");
        assertThat(history.get(2).get("reason")).isEqualTo("Falta aval asociativo");
        assertThat(history.get(1).get("changed_by")).isEqualTo(host.userId());
        assertThat(history.get(5).get("changed_by")).isNotEqualTo(host.userId()).isNotNull();
    }

    @Test
    void suspensionClosesAndReinstatementReopensOnlyFutureSchedules() throws Exception {
        String id = approvedExperience(host, "Avistamiento de aves");
        List<String> scheduleIds = createSchedules(host, id, daysAhead(20), 2, 8);

        action(adminToken, "/api/v1/admin/experiences/" + id + "/suspend", "{\"reason\":\"Alerta climática\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(scheduleStatuses(scheduleIds)).containsOnly("CLOSED");

        action(adminToken, "/api/v1/admin/experiences/" + id + "/reinstate", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(scheduleStatuses(scheduleIds)).containsOnly("OPEN");

        action(adminToken, "/api/v1/admin/experiences/" + id + "/reinstate", null)
                .andExpect(status().isConflict());
    }

    @Test
    void hostsOnlySeeTheirOwnExperiencesAndTouristsCannotUseTheHostPanel() throws Exception {
        String id = createExperience(host, "Taller de memoria");
        Host other = createHost();

        mvc.perform(get("/api/v1/host/experiences/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isNotFound());
        action(other.token(), "/api/v1/host/experiences/" + id + "/submit", null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/host/experiences").header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        String touristToken = tokenFor(registeredTourist());
        mvc.perform(get("/api/v1/host/experiences").header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void experienceValidationMirrorsDatabaseRules() throws Exception {
        String body = experienceBody("Ruta", 31).replace("\"05001\"", "\"99999\"")
                .replace("\"latitude\":6.25,", "");
        mvc.perform(postJson("/api/v1/host/experiences", host.token(), body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("title")))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("maxCapacityPerSlot")));
        mvc.perform(postJson("/api/v1/host/experiences", host.token(),
                        experienceBody("Ruta del café", 10).replace("\"05001\"", "\"99999\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("municipalityCode"));
    }

    // ------------------------------------------------------------- datos de recorrido (ADR-0022)

    @Test
    void routeDetailsAreStoredReplacedAndOnlyTheirCountIsPublic() throws Exception {
        String route = """
                "routeType":"LOOP","distanceKm":8.5,"roundTripMinutes":300,
                "stops":[{"name":"Mirador del río","description":"Descanso de 15 minutos"},{"name":"Casa de la memoria"}],""";
        String created = JsonPath.read(mvc.perform(postJson("/api/v1/host/experiences", host.token(),
                        experienceBody("Sendero con paradas", 12).replace("\"unitPriceCop\"", route + "\"unitPriceCop\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routeType").value("LOOP"))
                .andExpect(jsonPath("$.distanceKm").value(8.5))
                .andExpect(jsonPath("$.roundTripMinutes").value(300))
                .andExpect(jsonPath("$.stops[*].name").value(org.hamcrest.Matchers.contains("Mirador del río", "Casa de la memoria")))
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Editar reemplaza las paradas; omitirlas las quita y deja el resto de datos como nulos.
        mvc.perform(put("/api/v1/host/experiences/" + created).header(HttpHeaders.AUTHORIZATION, bearer(host.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(experienceBody("Sendero con paradas", 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops").isEmpty())
                .andExpect(jsonPath("$.distanceKm").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(put("/api/v1/host/experiences/" + created).header(HttpHeaders.AUTHORIZATION, bearer(host.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(experienceBody("Sendero con paradas", 12).replace("\"unitPriceCop\"", route + "\"unitPriceCop\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops.length()").value(2));
        action(host.token(), "/api/v1/host/experiences/" + created + "/submit", null).andExpect(status().isOk());
        action(adminToken, "/api/v1/admin/experiences/" + created + "/approve", null).andExpect(status().isOk());

        mvc.perform(get("/api/v1/experiences/" + created))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.routeType").value("LOOP"))
                .andExpect(jsonPath("$.summary.distanceKm").value(8.5))
                .andExpect(jsonPath("$.summary.roundTripMinutes").value(300))
                .andExpect(jsonPath("$.summary.stopsCount").value(2))
                .andExpect(jsonPath("$.stops").isEmpty())
                .andExpect(jsonPath("$.vereda").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meetingPoint").value("MEDELLÍN"));
    }

    @Test
    void routeDetailsAreValidatedAndOptionalForExistingExperiences() throws Exception {
        String bad = experienceBody("Ruta inválida", 10).replace("\"unitPriceCop\"",
                "\"distanceKm\":0,\"roundTripMinutes\":5,\"stops\":[{\"name\":\"\"}],\"unitPriceCop\"");
        mvc.perform(postJson("/api/v1/host/experiences", host.token(), bad))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("distanceKm")))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("roundTripMinutes")))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("stops[0].name")));
        // Sin datos de recorrido la experiencia sigue siendo válida (compatibilidad con las ya publicadas).
        mvc.perform(postJson("/api/v1/host/experiences", host.token(), experienceBody("Ruta sin datos", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routeType").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stops").isEmpty());
    }

    // ---------------------------------------------------------------------- franjas

    @Test
    void schedulesConvertLocalTimeAndEnforceCapacityAndUniqueness() throws Exception {
        String id = createExperience(host, "Cocina tradicional");
        LocalDate day = daysAhead(15);

        MvcResult created = mvc.perform(postJson("/api/v1/host/experiences/" + id + "/schedules", host.token(),
                        "{\"schedules\":[" + slot(day, 7, 12, 10) + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].localStartTime").value(day + "T07:00:00"))
                .andExpect(jsonPath("$[0].startTime").value(day + "T12:00:00Z"))
                .andExpect(jsonPath("$[0].availableSlots").value(10))
                .andReturn();
        String scheduleId = JsonPath.read(created.getResponse().getContentAsString(), "$[0].id");

        mvc.perform(postJson("/api/v1/host/experiences/" + id + "/schedules", host.token(),
                        "{\"schedules\":[" + slot(day.plusDays(1), 7, 12, 13) + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("schedules[0].totalSlots"));
        // Lote atómico: la franja válida no se guarda si otra choca.
        mvc.perform(postJson("/api/v1/host/experiences/" + id + "/schedules", host.token(),
                        "{\"schedules\":[" + slot(day.plusDays(2), 7, 12, 5) + "," + slot(day, 7, 12, 5) + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("schedules[1].localStart"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.schedules WHERE experience_id = ?::uuid",
                Long.class, id)).isEqualTo(1);

        jdbc.update("UPDATE catalog.schedules SET available_slots = 9 WHERE id = ?::uuid", scheduleId);
        action(host.token(), "/api/v1/host/schedules/" + scheduleId + "/cancel", "{\"reason\":\"Lluvias\"}")
                .andExpect(status().isConflict());
        jdbc.update("UPDATE catalog.schedules SET available_slots = 10 WHERE id = ?::uuid", scheduleId);
        action(host.token(), "/api/v1/host/schedules/" + scheduleId + "/cancel", "{\"reason\":\"Lluvias\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------ consulta pública

    @Test
    void publicCatalogShowsOnlyApprovedExperiencesWithoutHostData() throws Exception {
        String marker = "Marcador" + SEQ.incrementAndGet();
        String approved = approvedExperience(host, marker + " aprobada");
        createExperience(host, marker + " borrador");

        MvcResult result = mvc.perform(get("/api/v1/experiences").param("q", marker.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(approved))
                .andExpect(jsonPath("$.items[0].municipality.name").value("MEDELLÍN"))
                .andExpect(jsonPath("$.items[0].department.code").value("05"))
                .andExpect(jsonPath("$.items[0].currency").value("COP"))
                .andExpect(jsonPath("$.items[0].categoryCodes[0]").value("SENDERISMO_MEMORIA"))
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain(host.userId().toString(), host.email(), "hostId", "Anfitrion Prueba");

        mvc.perform(get("/api/v1/experiences").param("q", marker).param("municipalityCode", "05002"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/experiences").param("q", marker).param("departmentCode", "05")
                        .param("category", "SENDERISMO_MEMORIA").param("intensity", "MEDIUM"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/experiences").param("q", marker).param("minSlots", "1"))
                .andExpect(jsonPath("$.totalElements").value(0));
        createSchedules(host, approved, daysAhead(10), 1, 6);
        mvc.perform(get("/api/v1/experiences").param("q", marker).param("minSlots", "6"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/experiences").param("q", marker).param("minSlots", "7"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/experiences/" + approved))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.associationName").isString())
                .andExpect(jsonPath("$.meetingPoint").value("MEDELLÍN"));
    }

    @Test
    void availabilityFollowsTheContract() throws Exception {
        String approved = approvedExperience(host, "Agroturismo cafetero");
        LocalDate day = daysAhead(12);
        List<String> ids = createSchedules(host, approved, day, 2, 4);
        jdbc.update("UPDATE catalog.schedules SET available_slots = 0 WHERE id = ?::uuid", ids.get(1));

        mvc.perform(get("/api/v1/experiences/" + approved + "/availability")
                        .param("dateFrom", day.toString()).param("dateTo", day.plusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Bogota"))
                .andExpect(jsonPath("$.unitPriceCop").value(80000))
                .andExpect(jsonPath("$.schedules.length()").value(2))
                .andExpect(jsonPath("$.schedules[1].availableSlots").value(0))
                .andExpect(jsonPath("$.schedules[0].localDate").value(day.toString()));
        mvc.perform(get("/api/v1/experiences/" + approved + "/availability").param("minSlots", "1")
                        .param("dateFrom", day.toString()).param("dateTo", day.plusDays(5).toString()))
                .andExpect(jsonPath("$.schedules.length()").value(1));
        mvc.perform(get("/api/v1/experiences/" + approved + "/availability")
                        .param("dateFrom", day.toString()).param("dateTo", day.plusDays(61).toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("dateTo"));

        String draft = createExperience(host, "Borrador oculto");
        mvc.perform(get("/api/v1/experiences/" + draft + "/availability")
                        .param("dateFrom", day.toString()).param("dateTo", day.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/experiences/" + draft + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("dateFrom", day.toString()).param("dateTo", day.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_EXPERIENCE_NOT_APPROVED"));
        mvc.perform(get("/api/v1/experiences/" + draft)).andExpect(status().isNotFound());
    }

    @Test
    void referenceDataIsPublic() throws Exception {
        mvc.perform(get("/api/v1/catalog/departments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(33));
        mvc.perform(get("/api/v1/catalog/departments/05/municipalities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("MEDELLÍN")))
                .andExpect(jsonPath("$[*].code", not(hasItem("08001"))));
        mvc.perform(get("/api/v1/catalog/departments/00/municipalities")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.municipalities", Long.class)).isEqualTo(1122);
    }

    // ---------------------------------------------------------------- fixtures

    private record Host(UUID userId, String email, String token) {
    }

    private String hostProfileId(Host owner) {
        return jdbc.queryForObject("SELECT id::text FROM catalog.host_profiles WHERE user_id = ?::uuid",
                String.class, owner.userId().toString());
    }

    private long activeRole(UUID userId, String role) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND r.code = ? AND ur.revoked_at IS NULL""", Long.class, userId, role);
    }

    private Host createHost() throws Exception {
        String email = uniqueEmail("anfitrion");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        mvc.perform(postJson("/api/v1/admin/host-profiles", adminToken,
                        "{\"userId\":\"" + userId + "\",\"associationId\":\"" + associationId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
        return new Host(userId, email, tokenFor(email));
    }

    private String createAssociation() throws Exception {
        return JsonPath.read(mvc.perform(postJson("/api/v1/admin/associations", adminToken,
                        associationBody(uniqueNit(), uniqueRnt(), "05001")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String createExperience(Host owner, String title) throws Exception {
        return JsonPath.read(mvc.perform(postJson("/api/v1/host/experiences", owner.token(), experienceBody(title, 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String approvedExperience(Host owner, String title) throws Exception {
        String id = createExperience(owner, title);
        action(owner.token(), "/api/v1/host/experiences/" + id + "/submit", null).andExpect(status().isOk());
        action(adminToken, "/api/v1/admin/experiences/" + id + "/approve", null).andExpect(status().isOk());
        return id;
    }

    /** {@code count} franjas consecutivas de 07:00 a 12:00 hora local desde {@code first}. */
    private List<String> createSchedules(Host owner, String experienceId, LocalDate first, int count, int slots)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"schedules\":[");
        for (int i = 0; i < count; i++) {
            body.append(i == 0 ? "" : ",").append(slot(first.plusDays(i), 7, 12, slots));
        }
        String json = mvc.perform(postJson("/api/v1/host/experiences/" + experienceId + "/schedules", owner.token(),
                        body.append("]}").toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].id");
    }

    private List<String> scheduleStatuses(List<String> ids) {
        return ids.stream().map(id -> jdbc.queryForObject("SELECT status FROM catalog.schedules WHERE id = ?::uuid",
                String.class, id)).toList();
    }

    private String userWithRole(String prefix, RoleCode role) {
        String email = uniqueEmail(prefix);
        UUID id = registrationService.registerTourist(tourist(email), CLIENT).userId();
        roleAssignmentService.grant(id, role);
        return email;
    }

    private String registeredTourist() {
        String email = uniqueEmail("turista");
        registrationService.registerTourist(tourist(email), CLIENT);
        return email;
    }

    private String tokenFor(String email) throws Exception {
        return JsonPath.read(login(email), "$.accessToken");
    }

    private String login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions action(String token, String path, String body) throws Exception {
        var request = post(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String path, String token, String body) {
        return post(path).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private LocalDate daysAhead(int days) {
        return LocalDate.ofInstant(clock.instant(), BOGOTA).plusDays(days);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String slot(LocalDate day, int fromHour, int toHour, int slots) {
        return "{\"localStart\":\"%sT%02d:00:00\",\"localEnd\":\"%sT%02d:00:00\",\"totalSlots\":%d}"
                .formatted(day, fromHour, day, toHour, slots);
    }

    private static String experienceBody(String title, int capacity) {
        return """
                {"title":"%s","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Llevar agua.",
                 "physicalIntensity":"MEDIUM","durationMinutes":240,"municipalityCode":"05001","vereda":"La Esperanza",
                 "meetingPoint":"Parque principal","latitude":6.25,"longitude":-75.56,"maxCapacityPerSlot":%d,
                 "unitPriceCop":80000,"categoryCodes":["SENDERISMO_MEMORIA"]}""".formatted(title, capacity);
    }

    private static String associationBody(String nit, String rnt, String municipality) {
        return """
                {"nit":"%s","rntNumber":"%s","legalName":"Asociación Campesina La Esperanza",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"%s","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(nit, rnt, municipality);
    }

    private static String uniqueNit() {
        return String.format("9%08d-%d", SEQ.incrementAndGet() % 100_000_000, SEQ.get() % 10);
    }

    private static String uniqueRnt() {
        return Long.toString(100_000 + SEQ.incrementAndGet());
    }
}
