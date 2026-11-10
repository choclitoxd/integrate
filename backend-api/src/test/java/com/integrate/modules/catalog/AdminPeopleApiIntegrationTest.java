package com.integrate.modules.catalog;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * Consulta de personas del administrador (ADR-0043 §1): acceso solo para {@code ROLE_ADMIN}, búsqueda exacta por
 * correo o documento con respuesta mínima, correo enmascarado al buscar por documento, correos sintéticos fuera de la
 * búsqueda por correo, límite de uso, detalle con historial de roles, listados de gestores y anfitriones, y la huella
 * en la bitácora sin el criterio buscado.
 */
@AutoConfigureMockMvc
class AdminPeopleApiIntegrationTest extends PostgresIntegrationTest {

    private static final String SEARCH = "/api/v1/admin/users/search";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID adminId;
    private String adminToken;
    private String touristToken;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = uniqueEmail("admin-personas");
        adminId = registrationService.registerTourist(tourist(adminEmail), CLIENT).userId();
        roleAssignmentService.grant(adminId, RoleCode.ADMIN);
        adminToken = tokenFor(adminEmail);
        String touristEmail = uniqueEmail("turista-personas");
        registrationService.registerTourist(tourist(touristEmail), CLIENT);
        touristToken = tokenFor(touristEmail);
    }

    // --------------------------------------------------------------------------- acceso

    @Test
    void onlyTheAdministratorMayConsultPeople() throws Exception {
        UUID someone = registrationService.registerTourist(tourist(uniqueEmail("alguien")), CLIENT).userId();
        for (String path : List.of(SEARCH + "?email=a@b.co", "/api/v1/admin/users/" + someone,
                "/api/v1/admin/link-managers", "/api/v1/admin/host-profiles")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ búsqueda por correo

    @Test
    void searchByEmailIsExactCaseInsensitiveAndReturnsOnlyTheMinimum() throws Exception {
        String email = uniqueEmail("Buscada");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();

        String body = search("?email=" + email.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.items[0].email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.items[0].emailMasked").value(false))
                .andExpect(jsonPath("$.items[0].roles", hasItem("TOURIST")))
                .andExpect(jsonPath("$.items[0].hostProfileId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        // Nada de celular, documento ni credenciales.
        assertThat(body).doesNotContain("phone").doesNotContain("docNumber").doesNotContain("password")
                .doesNotContain("3001234567");

        search("?email=" + email.substring(0, 8) + "@ejemplo.com").andExpect(jsonPath("$.totalElements").value(0));
        search("?email=nadie@ejemplo.com").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void syntheticEmailAccountsAreNotFoundByEmail() throws Exception {
        String synthetic = "h-abcd2345xy@comunidad.integrate.co";
        registrationService.registerTourist(tourist(synthetic), CLIENT);

        search("?email=" + synthetic).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    // -------------------------------------------------------------- búsqueda por documento

    @Test
    void searchByDocumentMasksTheEmailAndNeedsTheDocumentType() throws Exception {
        String email = uniqueEmail("documentada");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        String document = jdbc.queryForObject("SELECT doc_number FROM auth.users WHERE id = ?", String.class, userId);

        String body = search("?docType=CC&document=" + document)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.items[0].email").value("d***@ejemplo.com"))
                .andExpect(jsonPath("$.items[0].emailMasked").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(email).doesNotContain(document);

        search("?docType=CE&document=" + document).andExpect(jsonPath("$.totalElements").value(0));
        search("?docType=CC&document=" + document.substring(0, 6)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchNeedsExactlyOneCriterion() throws Exception {
        search("").andExpect(status().isBadRequest());
        search("?email=a@b.co&docType=CC&document=ABCD1234").andExpect(status().isBadRequest());
        search("?document=ABCD1234").andExpect(status().isBadRequest());
        search("?docType=CC").andExpect(status().isBadRequest());
        search("?email=no-es-un-correo").andExpect(status().isUnprocessableEntity());
        search("?docType=CC&document=ab").andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------------ límite de uso

    @Test
    void lookupsAreLimitedPerAdministratorAndReopenAfterTheWindow() throws Exception {
        for (int i = 0; i < 20; i++) {
            search("?email=nadie" + i + "@ejemplo.com").andExpect(status().isOk());
        }
        search("?email=otro@ejemplo.com").andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("ADMIN_LOOKUP_RATE_LIMITED"));

        clock.advance(Duration.ofSeconds(61));
        search("?email=otro@ejemplo.com").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------ bitácora

    @Test
    void everyLookupLeavesATraceWithoutTheCriterion() throws Exception {
        String email = uniqueEmail("rastreada");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        String document = jdbc.queryForObject("SELECT doc_number FROM auth.users WHERE id = ?", String.class, userId);

        search("?email=" + email).andExpect(status().isOk());
        search("?docType=CC&document=" + document).andExpect(status().isOk());
        search("?email=nadie@ejemplo.com").andExpect(status().isOk());

        assertThat(purposes()).containsExactlyInAnyOrder("ADMIN_USER_LOOKUP_EMAIL", "ADMIN_USER_LOOKUP_DOCUMENT",
                "ADMIN_USER_LOOKUP_EMAIL");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail
                 WHERE action = 'SENSITIVE_DATA_ACCESSED' AND actor_id = ? AND details @> ?::jsonb""", Long.class,
                adminId, "{\"subjects\":[\"" + userId + "\"]}")).isEqualTo(2);
        // Ni el correo ni el documento buscados quedan escritos.
        String trail = jdbc.queryForObject("""
                SELECT string_agg(details::text, ' ') FROM audit.audit_trail
                 WHERE action = 'SENSITIVE_DATA_ACCESSED' AND actor_id = ?""", String.class, adminId);
        assertThat(trail).doesNotContain(email).doesNotContain(document).doesNotContain("nadie@");
    }

    // ------------------------------------------------------------------------------ detalle

    @Test
    void detailShowsTheRoleHistoryWithMaskedEmailAndDocument() throws Exception {
        UUID associationId = createAssociation();
        String email = uniqueEmail("gestora");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        String managerId = accredit(userId, associationId);
        postAction("/api/v1/admin/link-managers/" + managerId + "/revoke").andExpect(status().isOk());
        String document = jdbc.queryForObject("SELECT doc_number FROM auth.users WHERE id = ?", String.class, userId);

        String body = getAsAdmin("/api/v1/admin/users/" + userId)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.maskedEmail").value("g***@ejemplo.com"))
                .andExpect(jsonPath("$.docNumberLast4").value(document.substring(document.length() - 4)))
                .andExpect(jsonPath("$.roles", hasItem("TOURIST")))
                .andExpect(jsonPath("$.roleHistory[?(@.role=='LINK_MANAGER')].revokedAt").isNotEmpty())
                .andExpect(jsonPath("$.roleHistory[?(@.role=='LINK_MANAGER')].revokedBy",
                        hasItem(adminId.toString())))
                .andExpect(jsonPath("$.roleHistory[?(@.role=='TOURIST')].revokedAt").value(hasItem(nullValue())))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(email).doesNotContain(document).doesNotContain("phone");

        getAsAdmin("/api/v1/admin/users/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------ listados

    @Test
    void linkManagerListingFiltersByStatusAndNamesTheAssociationAndTheAccreditor() throws Exception {
        UUID associationId = createAssociation();
        UUID userId = registrationService.registerTourist(tourist(uniqueEmail("gestor-listado")), CLIENT).userId();
        String managerId = accredit(userId, associationId);

        getAsAdmin("/api/v1/admin/link-managers?status=ACTIVE&size=50")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.items[?(@.linkManagerId=='" + managerId + "')].associationName")
                        .value(hasItem("Asociación QA")))
                .andExpect(jsonPath("$.items[?(@.linkManagerId=='" + managerId + "')].accreditedByName")
                        .value(hasItem("Carlos Restrepo")))
                .andExpect(jsonPath("$.items[?(@.linkManagerId=='" + managerId + "')].userId")
                        .value(hasItem(userId.toString())));

        postAction("/api/v1/admin/link-managers/" + managerId + "/revoke").andExpect(status().isOk());
        getAsAdmin("/api/v1/admin/link-managers?status=ACTIVE&size=50")
                .andExpect(jsonPath("$.items[?(@.linkManagerId=='" + managerId + "')]").isEmpty());
        getAsAdmin("/api/v1/admin/link-managers?status=REVOKED&size=50")
                .andExpect(jsonPath("$.items[?(@.linkManagerId=='" + managerId + "')].accreditationStatus")
                        .value(hasItem("REVOKED")));
        getAsAdmin("/api/v1/admin/link-managers?status=ENORME").andExpect(status().isBadRequest());
        getAsAdmin("/api/v1/admin/link-managers?size=51").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void hostListingFiltersByAssociationAndFlagsSyntheticEmails() throws Exception {
        UUID associationId = createAssociation();
        UUID other = createAssociation();
        UUID realUser = registrationService.registerTourist(tourist(uniqueEmail("anfitrion-real")), CLIENT).userId();
        UUID syntheticUser = registrationService
                .registerTourist(tourist("h-zzzz2345ab@comunidad.integrate.co"), CLIENT).userId();
        createHost(realUser, associationId);
        createHost(syntheticUser, associationId);
        createHost(registrationService.registerTourist(tourist(uniqueEmail("anfitrion-otro")), CLIENT).userId(),
                other);

        String body = getAsAdmin("/api/v1/admin/host-profiles?associationId=" + associationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[?(@.userId=='" + syntheticUser + "')].emailIsSynthetic")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.items[?(@.userId=='" + realUser + "')].emailIsSynthetic")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.items[0].associationName").value("Asociación QA"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("phone").doesNotContain("docNumber").doesNotContain("victim");
        getAsAdmin("/api/v1/admin/host-profiles").andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(3)));

        // Cada listado (el filtrado y el completo) es un acceso sensible, con sus titulares.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail
                 WHERE action = 'SENSITIVE_DATA_ACCESSED' AND actor_id = ?
                   AND details->>'purpose' = 'ADMIN_USER_LIST' AND details @> ?::jsonb""", Long.class, adminId,
                "{\"subjects\":[\"" + realUser + "\"]}")).isEqualTo(2);
    }

    // ---------------------------------------------------------------- fixtures

    private List<String> purposes() {
        return jdbc.queryForList("""
                SELECT details->>'purpose' FROM audit.audit_trail
                 WHERE action = 'SENSITIVE_DATA_ACCESSED' AND actor_id = ? ORDER BY id""", String.class, adminId);
    }

    private UUID createAssociation() throws Exception {
        long n = ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
        String body = """
                {"nit":"%d-1","rntNumber":"%d","legalName":"Asociación QA","associationType":"OTRA",
                 "municipalityCode":"05001","contactEmail":"contacto@ejemplo.com","contactPhone":"+573001234567"}
                """.formatted(n, n);
        return UUID.fromString(JsonPath.read(mvc.perform(postJson("/api/v1/admin/associations", body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private String accredit(UUID userId, UUID associationId) throws Exception {
        String body = "{\"userId\":\"%s\",\"associationId\":\"%s\",\"accreditedOn\":\"%s\"}"
                .formatted(userId, associationId, LocalDate.ofInstant(clock.instant(),
                        java.time.ZoneId.of("America/Bogota")));
        return JsonPath.read(mvc.perform(postJson("/api/v1/admin/link-managers", body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.linkManagerId");
    }

    private void createHost(UUID userId, UUID associationId) throws Exception {
        mvc.perform(postJson("/api/v1/admin/host-profiles",
                        "{\"userId\":\"%s\",\"associationId\":\"%s\"}".formatted(userId, associationId)))
                .andExpect(status().isCreated());
    }

    private ResultActions search(String query) throws Exception {
        return getAsAdmin(SEARCH + query);
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
    }

    private ResultActions postAction(String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
    }

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body);
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
