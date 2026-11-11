package com.integrate.modules.auth.controller;

import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.web.TraceIdFilter;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RegistrationService.AdminBootstrapCommand;
import com.integrate.security.jwt.JwtProperties;
import com.integrate.support.PostgresIntegrationTest;
import com.integrate.support.TestKeys;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato HTTP de {@code /api/v1/auth/**} y {@code /api/v1/me/consents} (ADR-0005 §6, §9) sobre la
 * cadena real de Spring Security y PostgreSQL: cookies, cabeceras, RBAC y 401/403 en RFC 9457.
 */
@AutoConfigureMockMvc
@Import(AuthApiIntegrationTest.ProbeController.class)
class AuthApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String ACTOR_SQL = "SELECT CAST(common.current_actor() AS text)";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    // --------------------------------------------------------------- register

    @Test
    void currentPrivacyPolicyIsPublicAndCacheable() throws Exception {
        mvc.perform(get("/api/v1/privacy-policies/current"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andExpect(jsonPath("$.version").value(DEV_POLICY))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.documentUrl", matchesPattern("^https://.+")))
                .andExpect(jsonPath("$.effectiveFrom").isNotEmpty())
                .andExpect(jsonPath("$.contentSha256", matchesPattern("^[0-9a-f]{64}$")));
    }

    @Test
    void currentPrivacyPolicyIs404WhenNoneIsPublished() throws Exception {
        jdbc.update("UPDATE auth.privacy_policies SET is_current = false WHERE is_current");
        try {
            mvc.perform(get("/api/v1/privacy-policies/current"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("PRIVACY_POLICY_NOT_PUBLISHED"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        } finally {
            jdbc.update("UPDATE auth.privacy_policies SET is_current = true WHERE version = ?", DEV_POLICY);
        }
    }

    @Test
    void registerReturns201WithLocationAndNoSession() throws Exception {
        String email = uniqueEmail("api.registro");

        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, uniqueDocNumber(), "[\"PERSONAL_DATA\"]")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/users/[0-9a-f-]{36}")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TOURIST"))
                .andReturn();

        String userId = JsonPath.read(result.getResponse().getContentAsString(), "$.userId");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith(userId);
    }

    @Test
    void registerValidationErrorsAre422WithInvalidParams() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("no-es-correo", "12", "[\"PERSONAL_DATA\"]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("email")))
                .andExpect(jsonPath("$.invalidParams[*].name", hasItem("docNumber")));
    }

    @Test
    void registerWithoutPersonalDataConsentIs422() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail("sinconsent"), uniqueDocNumber(), "[\"HEALTH_EMERGENCY\"]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("consent.purposes"));
    }

    @Test
    void registerIgnoresAnyRoleFieldAndDuplicateIs409() throws Exception {
        String email = uniqueEmail("rol");
        String doc = uniqueDocNumber();
        String withRole = registerBody(email, doc, "[\"PERSONAL_DATA\"]").replaceFirst("\\{", "{\"role\":\"ADMIN\",");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(withRole))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TOURIST"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email.toUpperCase(), uniqueDocNumber(), "[\"PERSONAL_DATA\"]")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_USER_ALREADY_EXISTS"));
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginReturnsContractBodyAndRefreshCookie() throws Exception {
        String email = registerTourist();

        mvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_TOURIST"))
                .andExpect(jsonPath("$.requiresConsentUpdate").value(false))
                .andExpect(jsonPath("$.pendingPolicyVersion").isEmpty())
                .andExpect(jsonPath("$.pendingPurposes").isEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        "refresh_token=", "Path=/api/v1/auth", "Max-Age=604800", "Secure", "HttpOnly",
                        "SameSite=Strict")));
    }

    @Test
    void wrongPasswordIs401ProblemWithTraceId() throws Exception {
        String email = registerTourist();

        MvcResult result = mvc.perform(login(email, "incorrecta-" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.type").value("https://api.integrate.co/problems/invalid-credentials"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
                .andReturn();
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.traceId"))
                .isEqualTo(result.getResponse().getHeader(TraceIdFilter.HEADER));
    }

    @Test
    void lockedAccountIs423WithLockedUntilAndRetryAfter() throws Exception {
        String email = registerTourist();
        for (int i = 0; i < 4; i++) {
            mvc.perform(login(email, "incorrecta-" + i)).andExpect(status().isUnauthorized());
        }

        mvc.perform(login(email, "incorrecta-final"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").value(clock.instant().plus(Duration.ofMinutes(15)).toString()))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));
    }

    @Test
    void nullCharacterInPasswordIsRejectedBeforeAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"abc\\u0000def\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("password"));
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRotatesTheCookieEvenWithAnExpiredBearerHeader() throws Exception {
        String email = registerTourist();
        MvcResult login = mvc.perform(login(email, PASSWORD)).andReturn();
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie cookie = login.getResponse().getCookie("refresh_token");
        clock.advance(Duration.ofMinutes(20)); // el access token ya expiró

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        assertThat(refreshed.getResponse().getCookie("refresh_token").getValue()).isNotEqualTo(cookie.getValue());
    }

    @Test
    void refreshWithoutOrWithInvalidCookieIs401AndClearsTheCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "falso")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void logoutIs204ClearsTheCookieAndRevokesTheSession() throws Exception {
        MvcResult login = mvc.perform(login(registerTourist(), PASSWORD)).andReturn();
        Cookie cookie = login.getResponse().getCookie("refresh_token");

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ me/consents

    @Test
    void consentsRequireAuthenticationAndReturnTheHolderStatus() throws Exception {
        mvc.perform(get("/api/v1/me/consents"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").isString());

        String token = accessToken(registerTourist());
        mvc.perform(get("/api/v1/me/consents").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPolicyVersion").value(DEV_POLICY))
                .andExpect(jsonPath("$.consents[0].purpose").value("PERSONAL_DATA"))
                .andExpect(jsonPath("$.consents[0].state").value("VALID"))
                .andExpect(jsonPath("$.consents[0].occurredAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.consents[1].purpose").value("HEALTH_EMERGENCY"))
                .andExpect(jsonPath("$.consents[1].state").value("MISSING"));

        mvc.perform(post("/api/v1/me/consents").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"" + DEV_POLICY + "\",\"purposes\":[\"HEALTH_EMERGENCY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[1].state").value("VALID"));
    }

    // ------------------------------------------------------------- RBAC / JWT

    @Test
    void roleInsufficientIs403ProblemAndAdminPasses() throws Exception {
        String touristToken = accessToken(registerTourist());

        mvc.perform(get("/api/v1/test/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + touristToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RBAC_FORBIDDEN"));

        String adminEmail = uniqueEmail("api.admin");
        jdbc.update("""
                UPDATE auth.user_roles SET revoked_at = now()
                 WHERE revoked_at IS NULL AND role_id = (SELECT id FROM auth.roles WHERE code = 'ADMIN')""");
        registrationService.bootstrapAdmin(new AdminBootstrapCommand(adminEmail, PASSWORD, "Admin de prueba",
                DocType.PA, uniqueDocNumber(), "+10000000000"));
        String adminAuthorization = "Bearer " + accessToken(adminEmail);
        mvc.perform(get("/api/v1/test/admin-only").header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("ADMIN_CONSENT_REQUIRED"));
        mvc.perform(post("/api/v1/me/consents").header(HttpHeaders.AUTHORIZATION, adminAuthorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyVersion":"%s","purposes":["PERSONAL_DATA"]}""".formatted(DEV_POLICY)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/admin-only").header(HttpHeaders.AUTHORIZATION, adminAuthorization))
                .andExpect(status().isOk());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        KeyPair attacker = TestKeys.generate(2048);
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) attacker.getPublic())
                .privateKey((RSAPrivateKey) attacker.getPrivate()).keyID(jwtProperties.keyId()).build();
        String forged = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtProperties.keyId()).build(),
                JwtClaimsSet.builder().issuer(jwtProperties.issuer()).subject(UUID.randomUUID().toString())
                        .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(900))
                        .claim("roles", List.of("ROLE_ADMIN")).build())).getTokenValue();

        mvc.perform(get("/api/v1/test/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void authenticatedTransactionsPropagateTheActorToPostgres() throws Exception {
        String token = accessToken(registerTourist());

        String body = mvc.perform(get("/api/v1/test/actor").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.actor")).isEqualTo(JsonPath.<String>read(body, "$.sub"));
        // Fuera de una petición autenticada (y en la misma conexión del pool), el actor es NULL.
        Object anonymous = transactionTemplate.execute(s -> entityManager
                .createNativeQuery(ACTOR_SQL).getSingleResult());
        assertThat(anonymous).isNull();
    }

    private static final String REAUTH_HEADER = "X-Reauth-Token";

    private String reauthToken(String bearer) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/me/reauth").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.reauthToken");
    }

    @Test
    void ownProfileIsAuthenticatedAndCannotSelectAnotherAccount() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        String first = accessToken(registerTourist());
        String second = accessToken(registerTourist());
        // Editar los datos propios no pide confirmar la contraseña (ADR-0062); cambiarla sí.
        mvc.perform(patch("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nombre editado\",\"phone\":\"+573001111111\"}"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.fullName").value("Nombre editado"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Carlos Restrepo"));
        mvc.perform(patch("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"phone\":\"bad\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void ownPasswordChangeRequiresRecentPasswordConfirmation() throws Exception {
        String first = accessToken(registerTourist());
        String second = accessToken(registerTourist());
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"nueva-clave-"
                + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/v1/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/me/reauth").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnprocessableContent());
        String token = reauthToken(first);
        mvc.perform(post("/api/v1/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .header(REAUTH_HEADER, "token-inventado").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + second)
                        .header(REAUTH_HEADER, token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .header(REAUTH_HEADER, token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void reauthIsLockedAfterFiveWrongPasswords() throws Exception {
        String token = accessToken(registerTourist());
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/me/reauth").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnprocessableContent());
        }
        mvc.perform(post("/api/v1/auth/me/reauth").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void ownPasswordRequiresCurrentValueAndRevokesRefresh() throws Exception {
        String email = registerTourist();
        MvcResult login = mvc.perform(login(email, PASSWORD)).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie cookie = login.getResponse().getCookie("refresh_token");
        String next = "nueva-clave-" + UUID.randomUUID();
        String reauth = reauthToken(token);
        mvc.perform(post("/api/v1/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(REAUTH_HEADER, reauth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorrecta\",\"newPassword\":\"" + next + "\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(REAUTH_HEADER, reauth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + next + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(login(email, PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(login(email, next)).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshCannotSurvivePasswordChange() throws Exception {
        String email = registerTourist();
        MvcResult login = mvc.perform(login(email, PASSWORD)).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie cookie = login.getResponse().getCookie("refresh_token");
        String next = "nueva-clave-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<MvcResult> refresh = CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                return mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andReturn();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        CompletableFuture<MvcResult> change = CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                return mvc.perform(post("/api/v1/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(REAUTH_HEADER, reauthToken(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + next + "\"}"))
                        .andReturn();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        start.countDown();
        MvcResult changed = change.get(30, TimeUnit.SECONDS);
        MvcResult refreshed = refresh.get(30, TimeUnit.SECONDS);
        assertThat(changed.getResponse().getStatus()).isEqualTo(204);
        assertThat(refreshed.getResponse().getStatus()).isIn(200, 401);
        if (refreshed.getResponse().getStatus() == 200) {
            mvc.perform(post("/api/v1/auth/refresh").cookie(refreshed.getResponse().getCookie("refresh_token")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private String registerTourist() throws Exception {
        String email = uniqueEmail("api");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, uniqueDocNumber(), "[\"PERSONAL_DATA\"]")))
                .andExpect(status().isCreated());
        return email;
    }

    private String accessToken(String email) throws Exception {
        return JsonPath.read(mvc.perform(login(email, PASSWORD)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static RequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "MockMvc")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static String registerBody(String email, String docNumber, String purposes) {
        return """
                {"email":"%s","password":"%s","fullName":"Carlos Restrepo","docType":"CC","docNumber":"%s",
                 "phone":"+573001234567","consent":{"policyVersion":"%s","purposes":%s}}"""
                .formatted(email, PASSWORD, docNumber, DEV_POLICY, purposes);
    }

    /** Todas las subcadenas (atributos de la cookie, en cualquier orden). */
    private static Matcher<String> allOf(String... fragments) {
        List<Matcher<? super String>> matchers = Arrays.stream(fragments)
                .<Matcher<? super String>>map(Matchers::containsString).toList();
        return Matchers.allOf(matchers);
    }

    /** Endpoints solo de prueba (importados, no escaneados): RBAC y actor propagado a PostgreSQL. */
    @RestController
    static class ProbeController {

        private final TransactionTemplate tx;
        private final EntityManager em;

        ProbeController(TransactionTemplate tx, EntityManager em) {
            this.tx = tx;
            this.em = em;
        }

        @GetMapping("/api/v1/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "ok";
        }

        @GetMapping("/api/v1/test/actor")
        Map<String, Object> actor(@AuthenticationPrincipal Jwt jwt) {
            Object actor = tx.execute(s -> em.createNativeQuery(ACTOR_SQL).getSingleResult());
            return Map.of("sub", jwt.getSubject(), "actor", String.valueOf(actor));
        }
    }
}
