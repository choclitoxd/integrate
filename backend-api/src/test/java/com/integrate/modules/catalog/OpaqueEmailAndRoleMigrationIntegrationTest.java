package com.integrate.modules.catalog;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Migración {@code V16} (ADR-0038 §1.5 y §2.5): se reconstruye el estado heredado, se ejecuta el script tal cual
 * lo corre Flyway y se comprueba qué cambia y, sobre todo, qué <em>no</em> cambia. Flyway ya lo aplicó una vez al
 * arrancar el contexto (sin datos que tocar); aquí se vuelve a aplicar sobre datos reales, y de paso se prueba que
 * es idempotente.
 */
@AutoConfigureMockMvc
class OpaqueEmailAndRoleMigrationIntegrationTest extends PostgresIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String OPAQUE = "^h-[a-hj-np-z2-9]{10}@comunidad\\.integrate\\.co$";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void renamesOnlyPendingAccountsWithTheLegacySyntheticEmail() throws Exception {
        UUID pendingA = account("pa");
        UUID pendingB = account("pb");
        UUID active = account("ac");
        UUID realEmail = account("re");
        String legacyA = legacyEmail("cc1094000001");
        String legacyB = legacyEmail("cc1094000002");
        String legacyActive = legacyEmail("cc1094000003");
        String real = uniqueEmail("real");
        setEmail(pendingA, legacyA, "PENDING_RESET");
        setEmail(pendingB, legacyB, "PENDING_RESET");
        setEmail(active, legacyActive, "ACTIVE");
        setEmail(realEmail, real, "PENDING_RESET");

        runMigration();

        String renamedA = emailOf(pendingA);
        String renamedB = emailOf(pendingB);
        assertThat(renamedA).matches(OPAQUE).isNotEqualTo(legacyA);
        assertThat(renamedB).matches(OPAQUE).isNotEqualTo(legacyB);
        assertThat(renamedA).as("cada cuenta recibe un correo distinto").isNotEqualTo(renamedB);
        assertThat(emailOf(active)).as("una cuenta activa conserva su usuario de ingreso").isEqualTo(legacyActive);
        assertThat(emailOf(realEmail)).as("un correo real no se toca").isEqualTo(real);

        // Idempotente: un segundo pase no vuelve a renombrar lo que ya es opaco.
        runMigration();
        assertThat(emailOf(pendingA)).isEqualTo(renamedA);
        assertThat(emailOf(pendingB)).isEqualTo(renamedB);
    }

    @Test
    void closesTheGestorRoleOnlyForThoseWhoseEveryAccreditationIsRevoked() throws Exception {
        String admin = adminToken();
        String association = association(admin);
        UUID revoked = accredited(admin, association);
        UUID active = accredited(admin, association);
        UUID suspended = accredited(admin, association);

        revoke(admin, revoked);
        suspend(admin, suspended);
        // Estado heredado: antes de ADR-0038 revocar dejaba el rol vigente.
        roleAssignmentService.grant(revoked, RoleCode.LINK_MANAGER);
        assertThat(activeManagerRoles(revoked)).isEqualTo(1L);

        runMigration();

        assertThat(activeManagerRoles(revoked)).as("revocada: pierde el rol").isZero();
        assertThat(activeManagerRoles(active)).as("activa: lo conserva").isEqualTo(1L);
        assertThat(activeManagerRoles(suspended)).as("suspendida: lo conserva (es reversible)").isEqualTo(1L);

        runMigration();
        assertThat(activeManagerRoles(active)).isEqualTo(1L);
    }

    // ------------------------------------------------------------------ utilidades

    private void runMigration() throws Exception {
        String sql = new ClassPathResource("db/migration/V16__opaque_synthetic_email_and_revoked_manager_roles.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }

    private UUID account(String prefix) {
        return registrationService.registerTourist(tourist(uniqueEmail(prefix)), CLIENT).userId();
    }

    private static String legacyEmail(String docTypeAndNumber) {
        return docTypeAndNumber + SEQ.incrementAndGet() + "@comunidad.integrate.co";
    }

    private void setEmail(UUID userId, String email, String credentialStatus) {
        jdbc.update("UPDATE auth.users SET email = ?, credential_status = ? WHERE id = ?", email, credentialStatus,
                userId);
    }

    private String emailOf(UUID userId) {
        return jdbc.queryForObject("SELECT email FROM auth.users WHERE id = ?", String.class, userId);
    }

    private long activeManagerRoles(UUID userId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND r.code = 'LINK_MANAGER' AND ur.revoked_at IS NULL""", Long.class, userId);
    }

    private String adminToken() throws Exception {
        String email = uniqueEmail("admin");
        UUID id = registrationService.registerTourist(tourist(email), CLIENT).userId();
        roleAssignmentService.grant(id, RoleCode.ADMIN);
        return login(email);
    }

    private String login(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String association(String admin) throws Exception {
        String body = """
                {"nit":"%s","rntNumber":"%s","legalName":"Asociación de la migración V16",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda V16",
                 "contactEmail":"contacto@v16.org","contactPhone":"+573001234567"}"""
                .formatted(String.format("7%08d-%d", SEQ.incrementAndGet() % 100_000_000, SEQ.get() % 10),
                        Long.toString(700_000 + SEQ.incrementAndGet()));
        return JsonPath.read(mvc.perform(post("/api/v1/admin/associations").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Un turista acreditado como gestor; devuelve el id de su usuario y recuerda su perfil por consulta. */
    private UUID accredited(String admin, String association) throws Exception {
        UUID userId = account("gestor");
        mvc.perform(post("/api/v1/admin/link-managers").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"associationId\":\"%s\",\"accreditedOn\":\"2026-10-01\"}"
                                .formatted(userId, association)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accreditationStatus").value("ACTIVE"));
        return userId;
    }

    private void revoke(String admin, UUID userId) throws Exception {
        transition(admin, userId, "revoke");
    }

    private void suspend(String admin, UUID userId) throws Exception {
        transition(admin, userId, "suspend");
    }

    private void transition(String admin, UUID userId, String action) throws Exception {
        List<String> ids = jdbc.queryForList(
                "SELECT id::text FROM catalog.link_manager_profiles WHERE user_id = ?", String.class, userId);
        mvc.perform(post("/api/v1/admin/link-managers/" + ids.getFirst() + "/" + action)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
    }
}
