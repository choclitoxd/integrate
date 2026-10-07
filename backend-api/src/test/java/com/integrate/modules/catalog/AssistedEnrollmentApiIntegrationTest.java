package com.integrate.modules.catalog;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.crypto.SensitiveDataException;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Enrolamiento asistido de punta a punta (ADR-0007 §8) sobre la cadena real de Spring Security y
 * PostgreSQL: acreditación del gestor, subida prefirmada (S3 en memoria), registro con datos de víctima
 * cifrados y consentimiento {@code ASSISTED_PROXY}, jurisdicción del gestor y activación de la cuenta.
 */
@AutoConfigureMockMvc
class AssistedEnrollmentApiIntegrationTest extends PostgresIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String NEW_PASSWORD = "clave-propia-del-anfitrion";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private SensitiveDataCipher cipher;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String associationId;
    private Gestor gestor;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(START);
        adminToken = tokenFor(userWithRole("admin", RoleCode.ADMIN), PASSWORD);
        associationId = createAssociation();
        gestor = accreditGestor(associationId);
    }

    /** El reloj es un bean compartido por todo el contexto de pruebas: no se deja adelantado. */
    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    // ------------------------------------------------------------ acreditación (RF-03, §2)

    @Test
    void adminAccreditsGestoresAndControlsTheirStatus() throws Exception {
        mvc.perform(postJson("/api/v1/admin/link-managers", adminToken, linkManagerBody(gestor.userId(), associationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_MANAGER_EXISTS"));
        mvc.perform(postJson("/api/v1/admin/link-managers", gestor.token(),
                        linkManagerBody(UUID.randomUUID(), associationId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RBAC_FORBIDDEN"));

        action(adminToken, "/api/v1/admin/link-managers/" + gestor.linkManagerId() + "/suspend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accreditationStatus").value("SUSPENDED"));
        // El JWT conserva ROLE_LINK_MANAGER, pero sin acreditación ACTIVE no opera.
        mvc.perform(postJson("/api/v1/gestor/uploads", gestor.token(), "{\"kind\":\"PHYSICAL_CONSENT_FORM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_MANAGER_NOT_ACTIVE"));
        mvc.perform(get("/api/v1/gestor/hosts").header(HttpHeaders.AUTHORIZATION, bearer(gestor.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_MANAGER_NOT_ACTIVE"));

        action(adminToken, "/api/v1/admin/link-managers/" + gestor.linkManagerId() + "/reinstate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accreditationStatus").value("ACTIVE"));
        action(adminToken, "/api/v1/admin/link-managers/" + gestor.linkManagerId() + "/revoke")
                .andExpect(status().isOk());
        action(adminToken, "/api/v1/admin/link-managers/" + gestor.linkManagerId() + "/reinstate")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("REVOKED"));
    }

    // ------------------------------------------------------------- registro asistido (§3)

    @Test
    void gestorRegistersAHostWithEncryptedVictimDataAndAssistedConsent() throws Exception {
        String consentKey = uploaded(gestor.token(), "PHYSICAL_CONSENT_FORM");
        String supportKey = uploaded(gestor.token(), "VICTIM_SUPPORT_DOC");
        String docNumber = uniqueDocNumber();

        String json = mvc.perform(postJson("/api/v1/auth/register-assisted", gestor.token(),
                        registrationBody(docNumber, null, null, victimBody(true, "RUV-2019-000123",
                                "DESPLAZAMIENTO_FORZADO", supportKey), consentKey))
                        .header(HttpHeaders.USER_AGENT, "Terminal-Gestor/1.0"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.email").value("cc" + docNumber.toLowerCase() + "@comunidad.integrate.co"))
                .andExpect(jsonPath("$.emailIsSynthetic").value(true))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_HOST"))
                .andExpect(jsonPath("$.credentialStatus").value("PENDING_RESET"))
                .andExpect(jsonPath("$.associationId").value(associationId))
                .andExpect(jsonPath("$.linkManagerId").value(gestor.linkManagerId()))
                .andExpect(jsonPath("$.consent.channel").value("ASSISTED_PROXY"))
                .andExpect(jsonPath("$.consent.capturedBy").value(gestor.userId().toString()))
                .andExpect(jsonPath("$.victimData").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("RUV-2019-000123", "DESPLAZAMIENTO_FORZADO");

        UUID userId = UUID.fromString(JsonPath.read(json, "$.userId"));
        UUID hostProfileId = UUID.fromString(JsonPath.read(json, "$.hostProfileId"));

        // Consentimiento: dos actos ASSISTED_PROXY con la terminal y el formato físico del gestor.
        List<Map<String, Object>> consents = jdbc.queryForList("""
                SELECT purpose, channel, captured_by, physical_consent_form_s3_key, user_agent
                  FROM auth.habeas_data_consents WHERE user_id = ? ORDER BY purpose""", userId);
        assertThat(consents).extracting(c -> c.get("purpose")).containsExactly("PERSONAL_DATA", "SENSITIVE_VICTIM");
        assertThat(consents).allSatisfy(c -> {
            assertThat(c.get("channel")).isEqualTo("ASSISTED_PROXY");
            assertThat(c.get("captured_by")).isEqualTo(gestor.userId());
            assertThat(c.get("physical_consent_form_s3_key")).isEqualTo(consentKey);
            assertThat(c.get("user_agent")).isEqualTo("Terminal-Gestor/1.0");
        });

        // Datos de víctima: ciphertext en la base, atado a su fila y columna; created_by = gestor.
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT victim_condition_enc, ruv_registration_number_enc, victimization_type_enc, support_doc_s3_key,
                       sensitive_key_version, created_by
                  FROM catalog.host_profiles WHERE id = ?""", hostProfileId);
        byte[] ruv = (byte[]) row.get("ruv_registration_number_enc");
        assertThat(new String(ruv, StandardCharsets.ISO_8859_1)).doesNotContain("RUV-2019-000123");
        short version = ((Number) row.get("sensitive_key_version")).shortValue();
        assertThat(cipher.decrypt(ruv, aad("ruv_registration_number_enc", hostProfileId), version))
                .isEqualTo("RUV-2019-000123");
        assertThat(cipher.decrypt((byte[]) row.get("victim_condition_enc"), aad("victim_condition_enc", hostProfileId),
                version)).isEqualTo("true");
        assertThat(cipher.decrypt((byte[]) row.get("victimization_type_enc"),
                aad("victimization_type_enc", hostProfileId), version)).isEqualTo("DESPLAZAMIENTO_FORZADO");
        assertThatThrownBy(() -> cipher.decrypt(ruv, aad("victimization_type_enc", hostProfileId), version))
                .isInstanceOf(SensitiveDataException.class);
        assertThat(row.get("support_doc_s3_key")).isEqualTo(supportKey);
        assertThat(row.get("created_by")).isEqualTo(gestor.userId());

        // Vínculo vigente con el gestor y cuenta que aún no puede iniciar sesión.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM catalog.host_manager_assignments
                 WHERE host_id = ? AND manager_id = ?::uuid AND valid_to IS NULL""", Long.class,
                hostProfileId, gestor.linkManagerId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT credential_status FROM auth.users WHERE id = ?", String.class, userId))
                .isEqualTo("PENDING_RESET");
    }

    @Test
    void adminMustChooseAnActiveGestor() throws Exception {
        String consentKey = uploaded(adminToken, "PHYSICAL_CONSENT_FORM");

        mvc.perform(postJson("/api/v1/auth/register-assisted", adminToken,
                        registrationBody(uniqueDocNumber(), null, null, victimBody(false, null, null, null), consentKey)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("linkManagerId"));
        mvc.perform(postJson("/api/v1/auth/register-assisted", adminToken,
                        registrationBody(uniqueDocNumber(), uniqueEmail("rosa"), gestor.linkManagerId(),
                                victimBody(false, null, null, null), consentKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailIsSynthetic").value(false))
                .andExpect(jsonPath("$.linkManagerId").value(gestor.linkManagerId()))
                .andExpect(jsonPath("$.consent.capturedBy").exists());
    }

    @Test
    void onlyGestoresAndAdminsMayRegisterHosts() throws Exception {
        String touristToken = tokenFor(registeredTourist(), PASSWORD);

        mvc.perform(postJson("/api/v1/auth/register-assisted", touristToken,
                        registrationBody(uniqueDocNumber(), null, null, victimBody(false, null, null, null),
                                "consents/physical/2026/10/" + UUID.randomUUID() + ".pdf")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RBAC_FORBIDDEN"));
        mvc.perform(postJson("/api/v1/gestor/uploads", touristToken, "{\"kind\":\"PHYSICAL_CONSENT_FORM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidRegistrationsWithoutWritingAnything() throws Exception {
        String consentKey = uploaded(gestor.token(), "PHYSICAL_CONSENT_FORM");
        String docNumber = uniqueDocNumber();
        String victim = victimBody(false, null, null, null);

        // Documento no subido, de otro tipo, que no es PDF o demasiado grande.
        String missing = "consents/physical/2026/10/" + UUID.randomUUID() + ".pdf";
        expectDocumentInvalid(registrationBody(docNumber, null, null, victim, missing), "consent.physicalConsentFormS3Key");
        String supportKey = uploaded(gestor.token(), "VICTIM_SUPPORT_DOC");
        expectDocumentInvalid(registrationBody(docNumber, null, null, victim, supportKey),
                "consent.physicalConsentFormS3Key");
        String image = presignedKey(gestor.token(), "PHYSICAL_CONSENT_FORM");
        documentStorage.put(image, "image/png", 1024);
        expectDocumentInvalid(registrationBody(docNumber, null, null, victim, image), "consent.physicalConsentFormS3Key");
        String huge = presignedKey(gestor.token(), "PHYSICAL_CONSENT_FORM");
        documentStorage.put(huge, "application/pdf", 6L * 1024 * 1024);
        expectDocumentInvalid(registrationBody(docNumber, null, null, victim, huge), "consent.physicalConsentFormS3Key");

        // Datos de víctima incoherentes, política o finalidades inválidas.
        register(registrationBody(docNumber, null, null, victimBody(false, "RUV-1", null, null), consentKey))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("victimData.ruvRegistrationNumber"));
        register(registrationBody(docNumber, null, null, victim, consentKey).replace(DEV_POLICY, "v9.9"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("consent.policyVersion"));
        register(registrationBody(docNumber, null, null, victim, consentKey).replace(",\"SENSITIVE_VICTIM\"", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("consent.purposes"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE doc_number = ?", Long.class, docNumber))
                .isZero();

        // El primer registro válido consume el formato físico; el documento duplicado responde 409.
        register(registrationBody(docNumber, null, null, victim, consentKey)).andExpect(status().isCreated());
        expectDocumentInvalid(registrationBody(uniqueDocNumber(), null, null, victim, consentKey),
                "consent.physicalConsentFormS3Key");
        register(registrationBody(docNumber, null, null, victim, uploaded(gestor.token(), "PHYSICAL_CONSENT_FORM")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_USER_ALREADY_EXISTS"));
    }

    // ------------------------------------------------------- jurisdicción y activación (§1, §6)

    @Test
    void gestorSeesOnlyHisHostsWithoutSensitiveData() throws Exception {
        String hostProfileId = registerHost(gestor);
        Gestor other = accreditGestor(associationId);

        mvc.perform(get("/api/v1/gestor/hosts").header(HttpHeaders.AUTHORIZATION, bearer(gestor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].hostProfileId").value(hostProfileId))
                .andExpect(jsonPath("$.items[0].emailIsSynthetic").value(true))
                .andExpect(jsonPath("$.items[0].docNumberLast4").isString())
                .andExpect(jsonPath("$.items[0].credentialStatus").value("PENDING_RESET"))
                .andExpect(jsonPath("$.items[0].victimCondition").doesNotExist())
                .andExpect(jsonPath("$.items[0].docNumber").doesNotExist());
        mvc.perform(get("/api/v1/gestor/hosts").header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        action(other.token(), "/api/v1/gestor/hosts/" + hostProfileId + "/activation-code")
                .andExpect(status().isNotFound());
    }

    @Test
    void hostActivatesTheAccountWithASingleUseCode() throws Exception {
        String hostProfileId = registerHost(gestor);
        String email = hostEmail(hostProfileId);

        String first = issueCode(hostProfileId);
        String second = issueCode(hostProfileId);
        activate(email, first, NEW_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACTIVATION_INVALID"));

        activate(email, second.toLowerCase(), "corta").andExpect(status().isUnprocessableContent());
        activate(email, second.toLowerCase(), NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(tokenFor(email, NEW_PASSWORD)).isNotBlank();

        activate(email, second, NEW_PASSWORD).andExpect(status().isUnauthorized());
        action(gestor.token(), "/api/v1/gestor/hosts/" + hostProfileId + "/activation-code")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"));
    }

    @Test
    void codesExpireAndBurnOutAfterFiveFailures() throws Exception {
        String hostProfileId = registerHost(gestor);
        String email = hostEmail(hostProfileId);

        String expiring = issueCode(hostProfileId);
        clock.advance(Duration.ofHours(73));
        activate(email, expiring, NEW_PASSWORD).andExpect(status().isUnauthorized());

        // El access token del gestor también venció: nueva sesión.
        String code = issueCode(tokenFor(gestor.email(), PASSWORD), hostProfileId);
        for (int i = 0; i < 5; i++) {
            activate(email, "00000-00000", NEW_PASSWORD).andExpect(status().isUnauthorized());
        }
        activate(email, code, NEW_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACTIVATION_INVALID"));
        activate(uniqueEmail("nadie"), code, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- fixtures

    private record Gestor(UUID userId, String email, String linkManagerId, String token) {
    }

    private Gestor accreditGestor(String association) throws Exception {
        String email = uniqueEmail("gestor");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        String json = mvc.perform(postJson("/api/v1/admin/link-managers", adminToken, linkManagerBody(userId, association)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accreditationStatus").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        // El rol aparece en el JWT desde el siguiente login.
        return new Gestor(userId, email, JsonPath.read(json, "$.linkManagerId"), tokenFor(email, PASSWORD));
    }

    private String registerHost(Gestor owner) throws Exception {
        String json = register(registrationBody(uniqueDocNumber(), null, null,
                        victimBody(true, null, null, null), uploaded(owner.token(), "PHYSICAL_CONSENT_FORM")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.hostProfileId");
    }

    private String hostEmail(String hostProfileId) {
        return jdbc.queryForObject("""
                SELECT u.email FROM auth.users u JOIN catalog.host_profiles h ON h.user_id = u.id
                 WHERE h.id = ?::uuid""", String.class, hostProfileId);
    }

    private String issueCode(String hostProfileId) throws Exception {
        return issueCode(gestor.token(), hostProfileId);
    }

    private String issueCode(String token, String hostProfileId) throws Exception {
        String json = action(token, "/api/v1/gestor/hosts/" + hostProfileId + "/activation-code")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.code");
    }

    private ResultActions activate(String email, String code, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\"}".formatted(email, code, password)));
    }

    /** Pide la URL prefirmada y simula el {@code PUT} de un PDF válido. */
    private String uploaded(String token, String kind) throws Exception {
        String key = presignedKey(token, kind);
        documentStorage.put(key, "application/pdf", 180_000);
        return key;
    }

    private String presignedKey(String token, String kind) throws Exception {
        String json = mvc.perform(postJson("/api/v1/gestor/uploads", token, "{\"kind\":\"" + kind + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.uploadUrl").isString())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.s3Key");
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(postJson("/api/v1/auth/register-assisted", gestor.token(), body));
    }

    private void expectDocumentInvalid(String body, String field) throws Exception {
        register(body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_DOCUMENT_INVALID"))
                .andExpect(jsonPath("$.invalidParams[0].name").value(field));
    }

    private String createAssociation() throws Exception {
        String body = """
                {"nit":"%s","rntNumber":"%s","legalName":"Asociación de Víctimas El Retorno",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda El Retorno",
                 "contactEmail":"contacto@retorno.org","contactPhone":"+573001234567"}"""
                .formatted(String.format("8%08d-%d", SEQ.incrementAndGet() % 100_000_000, SEQ.get() % 10),
                        Long.toString(200_000 + SEQ.incrementAndGet()));
        return JsonPath.read(mvc.perform(postJson("/api/v1/admin/associations", adminToken, body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
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

    private String tokenFor(String email, String password) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions action(String token, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static MockHttpServletRequestBuilder postJson(String path, String token, String body) {
        return post(path).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String aad(String column, UUID hostProfileId) {
        return "catalog.host_profiles." + column + ":" + hostProfileId;
    }

    private static String linkManagerBody(UUID userId, String association) {
        return "{\"userId\":\"%s\",\"associationId\":\"%s\",\"accreditedOn\":\"2026-09-01\"}".formatted(userId, association);
    }

    private static String victimBody(boolean victim, String ruv, String type, String supportKey) {
        return "{\"victimCondition\":%s,\"ruvRegistrationNumber\":%s,\"victimizationType\":%s,\"supportDocS3Key\":%s}"
                .formatted(victim, quoted(ruv), quoted(type), quoted(supportKey));
    }

    private static String registrationBody(String docNumber, String email, String linkManagerId, String victimData,
                                           String consentKey) {
        return """
                {"fullName":"Rosa Patiño","docType":"CC","docNumber":"%s","phone":"+573001234567","email":%s,
                 "linkManagerId":%s,"victimData":%s,
                 "consent":{"policyVersion":"%s","purposes":["PERSONAL_DATA","SENSITIVE_VICTIM"],
                            "physicalConsentFormS3Key":"%s"}}"""
                .formatted(docNumber, quoted(email), quoted(linkManagerId), victimData, DEV_POLICY, consentKey);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
