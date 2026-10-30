package com.integrate.modules.audit;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.audit.SensitiveAccessRecorder.Access;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consulta, exportación y verificación de la bitácora, y rastro de los accesos sensibles (ADR-0035): solo
 * {@code ROLE_ADMIN}, filtros, paginación, redacción, búsqueda por titular, CSV seguro y auditoría de las consultas.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "integrate.audit.export.max-rows=3")
class AuditApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private AuditTrail auditTrail;

    @Autowired
    private SensitiveAccessRecorder recorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminEmail;
    private String adminToken;
    private String touristToken;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(START);
        adminEmail = uniqueEmail("auditor");
        UUID adminId = registrationService.registerTourist(tourist(adminEmail), CLIENT).userId();
        roleAssignmentService.grant(adminId, RoleCode.ADMIN);
        adminToken = tokenFor(adminEmail);
        String touristEmail = uniqueEmail("turista-auditoria");
        registrationService.registerTourist(tourist(touristEmail), CLIENT);
        touristToken = tokenFor(touristEmail);
    }

    // --------------------------------------------------------------------------- acceso

    @Test
    void onlyTheAdministratorMayReadTheAuditTrail() throws Exception {
        for (String path : List.of("/api/v1/admin/audit", "/api/v1/admin/audit/export?entityId=no-existe",
                "/api/v1/admin/audit/verify")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ filtros y página

    @Test
    void filtersAndPaginationReturnTheNewestFirst() throws Exception {
        String action = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            record("admin", "test_entity", "e-" + i, action, Severity.INFO, Map.of("n", i));
        }
        record("admin", "test_entity", "other", action, Severity.HIGH, Map.of());

        String first = getAsAdmin("/api/v1/admin/audit?action=" + action + "&size=2&page=0")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long newest = ((Number) JsonPath.read(first, "$.items[0].id")).longValue();
        long second = ((Number) JsonPath.read(first, "$.items[1].id")).longValue();
        assertThat(newest).isGreaterThan(second);

        getAsAdmin("/api/v1/admin/audit?action=" + action + "&size=2&page=2").andExpect(jsonPath("$.items.length()").value(2));
        getAsAdmin("/api/v1/admin/audit?action=" + action + "&entityId=e-3").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].entityId").value("e-3"))
                .andExpect(jsonPath("$.items[0].details.n").value(3));
        getAsAdmin("/api/v1/admin/audit?action=" + action + "&severity=HIGH").andExpect(jsonPath("$.totalElements").value(1));
        getAsAdmin("/api/v1/admin/audit?action=" + action + "&module=catalog").andExpect(jsonPath("$.totalElements").value(0));
        getAsAdmin("/api/v1/admin/audit?action=" + action + "&to=2020-01-01T00:00:00Z")
                .andExpect(jsonPath("$.totalElements").value(0));
        getAsAdmin("/api/v1/admin/audit?size=101").andExpect(status().isUnprocessableEntity());
        getAsAdmin("/api/v1/admin/audit?severity=ENORME").andExpect(status().isUnprocessableEntity());
    }

    // --------------------------------------------------------------------- redacción

    @Test
    void sensitiveKeysNeverReachTheTrailInClearText() throws Exception {
        String action = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        record("admin", "test_entity", "pii", action, Severity.INFO,
                Map.of("phone", "+573001234567", "nested", Map.of("docNumber", "1234567"), "ok", "visible"));

        getAsAdmin("/api/v1/admin/audit?action=" + action)
                .andExpect(jsonPath("$.items[0].details.phone").value("[REDACTED]"))
                .andExpect(jsonPath("$.items[0].details.nested.docNumber").value("[REDACTED]"))
                .andExpect(jsonPath("$.items[0].details.ok").value("visible"));
        assertThat(jdbc.queryForObject("SELECT details::text FROM audit.audit_trail WHERE action = ?", String.class,
                action)).doesNotContain("3001234567").doesNotContain("1234567\"");
    }

    // -------------------------------------------------------------- accesos sensibles

    @Test
    void sensitiveAccessIsTraceableByTheDataSubjectWithoutExposingTheValues() throws Exception {
        UUID adminId = jdbc.queryForObject("SELECT id FROM auth.users WHERE email = ?", UUID.class, adminEmail);
        String booking = UUID.randomUUID().toString();
        String unrelated = UUID.randomUUID().toString();
        inTransaction(() -> recorder.record(new Access("offline", "device", "dev-1", "OFFLINE_ROSTER",
                List.of("emergencyContact.name", "emergencyContact.phone", "holderDocument"),
                List.of(booking, "otra-reserva"), adminId, null, "JUnit")));

        getAsAdmin("/api/v1/admin/audit?action=SENSITIVE_DATA_ACCESSED&subject=" + booking)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].module").value("offline"))
                .andExpect(jsonPath("$.items[0].details.purpose").value("OFFLINE_ROSTER"))
                .andExpect(jsonPath("$.items[0].details.subjectCount").value(2))
                .andExpect(jsonPath("$.items[0].details.fields",
                        hasItem("emergencyContact.name")))
                .andExpect(jsonPath("$.items[0].details.subjects", hasItem(booking)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("+57"))));
        getAsAdmin("/api/v1/admin/audit?action=SENSITIVE_DATA_ACCESSED&subject=" + unrelated)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------- las consultas se auditan

    @Test
    void everyQueryExportAndVerificationLeavesItsOwnEntry() throws Exception {
        UUID adminId = jdbc.queryForObject("SELECT id FROM auth.users WHERE email = ?", UUID.class, adminEmail);
        long before = auditCount(adminId);

        getAsAdmin("/api/v1/admin/audit?module=catalog").andExpect(status().isOk());
        getAsAdmin("/api/v1/admin/audit/export?module=catalog&entityId=no-existe").andExpect(status().isOk());
        getAsAdmin("/api/v1/admin/audit/verify").andExpect(status().isOk());

        assertThat(auditCount(adminId)).isEqualTo(before + 3);
        assertThat(jdbc.queryForList("""
                SELECT action FROM audit.audit_trail WHERE actor_id = ? AND module = 'admin'
                   AND action IN ('AUDIT_QUERIED', 'AUDIT_EXPORTED', 'AUDIT_VERIFIED')""", String.class, adminId))
                .contains("AUDIT_QUERIED", "AUDIT_EXPORTED", "AUDIT_VERIFIED");
        assertThat(jdbc.queryForObject("""
                SELECT details ->> 'module' FROM audit.audit_trail
                 WHERE actor_id = ? AND action = 'AUDIT_QUERIED' ORDER BY id DESC LIMIT 1""", String.class, adminId))
                .isEqualTo("catalog");
    }

    // -------------------------------------------------------------------- exportación

    @Test
    void theExportIsCsvNeutralisesFormulasAndRespectsTheCap() throws Exception {
        String action = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        record("admin", "test_entity", "=1+1", action, Severity.INFO, Map.of("note", "a,b"));

        String csv = getAsAdmin("/api/v1/admin/audit/export?action=" + action)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo(
                "id,createdAt,module,entityName,entityId,action,severity,actorId,actorIp,userAgent,details");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).contains(",'=1+1,").contains(action);

        // El tope (3 filas en esta prueba): un filtro que devuelve más se rechaza y pide acotar.
        for (int i = 0; i < 4; i++) {
            record("admin", "test_entity", "bulk-" + i, "BULK_" + action, Severity.INFO, Map.of());
        }
        getAsAdmin("/api/v1/admin/audit/export?action=BULK_" + action).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUDIT_EXPORT_TOO_LARGE"));
    }

    // ------------------------------------------------------------------------- cadena

    @Test
    void theVerificationReportsAnIntactChainAndRejectsAbsurdRanges() throws Exception {
        record("admin", "test_entity", "v-1", "TEST_VERIFY", Severity.INFO, Map.of());

        getAsAdmin("/api/v1/admin/audit/verify")
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.checked").isNumber())
                .andExpect(jsonPath("$.brokenId").doesNotExist());
        long max = jdbc.queryForObject("SELECT max(id) FROM audit.audit_trail", Long.class);
        getAsAdmin("/api/v1/admin/audit/verify?fromId=" + max + "&toId=" + max)
                .andExpect(jsonPath("$.intact").value(true)).andExpect(jsonPath("$.checked").value(1));
        getAsAdmin("/api/v1/admin/audit/verify?fromId=10&toId=5").andExpect(status().isUnprocessableEntity());
        getAsAdmin("/api/v1/admin/audit/verify?fromId=1&toId=500000").andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- fixtures

    private long auditCount(UUID actorId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit.audit_trail WHERE actor_id = ? AND module = 'admin'",
                Long.class, actorId);
    }

    private void record(String module, String entity, String entityId, String action, Severity severity,
                        Map<String, Object> details) {
        inTransaction(() -> auditTrail.record(new AuditEntry(module, entity, entityId, action, severity, null, null,
                null, details)));
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
    }

    private String tokenFor(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
