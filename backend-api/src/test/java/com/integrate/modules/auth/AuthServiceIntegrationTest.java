package com.integrate.modules.auth;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.dto.request.LoginRequest;
import com.integrate.modules.auth.service.AuthService;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.model.RegisteredUser;
import com.integrate.modules.auth.service.model.Session;
import com.integrate.support.PostgresIntegrationTest;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Login, bloqueo, rotación y logout (RF-01, ADR-0005 §4-§5) contra PostgreSQL real. */
class AuthServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbc;

    private String email;
    private UUID userId;

    @BeforeEach
    void registerUser() {
        email = uniqueEmail("login");
        RegisteredUser user = registrationService.registerTourist(tourist(email), CLIENT);
        userId = user.userId();
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginIssuesRs256AccessTokenAndHashedRefreshToken() {
        Session session = authService.login(new LoginRequest(email.toUpperCase(), PASSWORD), CLIENT);

        assertThat(session.expiresInSeconds()).isEqualTo(900);
        assertThat(session.user().id()).isEqualTo(userId);
        assertThat(session.user().roles()).containsExactly("ROLE_TOURIST");
        assertThat(session.consent().requiresConsentUpdate()).isFalse();
        assertThat(session.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));

        Jwt jwt = jwtDecoder.decode(session.accessToken());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "session-test");
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_TOURIST");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("integrate-api");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(900));

        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT token_sha256, expires_at - issued_at AS ttl FROM auth.refresh_tokens WHERE user_id = ?", userId);
        assertThat(stored.get("token_sha256")).isEqualTo(sha256(session.refreshToken()))
                .isNotEqualTo(session.refreshToken());
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM auth.users WHERE id = ?",
                Boolean.class, userId)).isTrue();
        assertThat(attempts()).containsExactly("OK");
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() {
        assertError(() -> authService.login(new LoginRequest(uniqueEmail("nadie"), PASSWORD), CLIENT),
                ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertError(() -> authService.login(new LoginRequest(email, "otra-clave-incorrecta"), CLIENT),
                ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThat(attempts()).containsExactly("BAD_CREDENTIALS");
    }

    @Test
    void fifthConsecutiveFailureLocksForFifteenMinutes() {
        for (int i = 0; i < 4; i++) {
            assertError(() -> authService.login(new LoginRequest(email, "incorrecta-" + UUID.randomUUID()), CLIENT),
                    ErrorCode.AUTH_INVALID_CREDENTIALS);
            clock.advance(Duration.ofSeconds(30));
        }
        Instant fifth = clock.instant();

        BusinessRuleException locked = loginFails(email, "incorrecta-final");

        assertThat(locked.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED);
        Instant lockedUntil = fifth.plus(Duration.ofMinutes(15));
        assertThat(locked.getExtensions()).containsEntry("lockedUntil", lockedUntil.toString());
        assertThat(locked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
        assertThat(lockedUntil())
                .isEqualTo(lockedUntil);

        // Durante el bloqueo, ni la contraseña correcta entra (y no se verifica).
        clock.advance(Duration.ofMinutes(14));
        assertError(() -> authService.login(new LoginRequest(email, PASSWORD), CLIENT), ErrorCode.AUTH_ACCOUNT_LOCKED);

        // Al expirar, el contador ya salió de la ventana: un fallo más no vuelve a bloquear.
        clock.advance(Duration.ofMinutes(2));
        assertError(() -> authService.login(new LoginRequest(email, "incorrecta-tras-bloqueo"), CLIENT),
                ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThat(authService.login(new LoginRequest(email, PASSWORD), CLIENT).user().id()).isEqualTo(userId);
        assertThat(lockedUntil())
                .isNull();
    }

    @Test
    void failuresOutsideTheWindowOrBeforeASuccessDoNotCount() {
        for (int i = 0; i < 4; i++) {
            loginFails(email, "incorrecta-" + i);
        }
        clock.advance(Duration.ofMinutes(11));
        loginFails(email, "fuera-de-ventana");      // 1 dentro de la ventana
        authService.login(new LoginRequest(email, PASSWORD), CLIENT);
        for (int i = 0; i < 4; i++) {
            assertThat(loginFails(email, "tras-exito-" + i).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }

    @Test
    void suspendedAccountGets403OnlyWithCorrectPassword() {
        jdbc.update("UPDATE auth.users SET status = 'SUSPENDED' WHERE id = ?", userId);

        assertError(() -> authService.login(new LoginRequest(email, "incorrecta"), CLIENT),
                ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertError(() -> authService.login(new LoginRequest(email, PASSWORD), CLIENT),
                ErrorCode.AUTH_ACCOUNT_SUSPENDED);
    }

    @Test
    void pendingResetCredentialCannotLogIn() {
        jdbc.update("UPDATE auth.users SET credential_status = 'PENDING_RESET' WHERE id = ?", userId);

        assertError(() -> authService.login(new LoginRequest(email, PASSWORD), CLIENT),
                ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRotatesAndLinksTheReplacedToken() {
        Session first = authService.login(new LoginRequest(email, PASSWORD), CLIENT);
        clock.advance(Duration.ofMinutes(5));

        Session second = authService.refresh(first.refreshToken(), CLIENT);

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(jwtDecoder.decode(second.accessToken()).getSubject()).isEqualTo(userId.toString());
        Map<String, Object> old = tokenRow(first.refreshToken());
        assertThat(old.get("revoke_reason")).isEqualTo("ROTATED");
        assertThat(old.get("replaced_by_id")).isEqualTo(tokenRow(second.refreshToken()).get("id"));
        assertThat(tokenRow(second.refreshToken()).get("revoked_at")).isNull();
    }

    @Test
    void reuseWithinGraceWindowIsRejectedWithoutRevokingTheNewSession() {
        Session first = authService.login(new LoginRequest(email, PASSWORD), CLIENT);
        Session second = authService.refresh(first.refreshToken(), CLIENT);
        clock.advance(Duration.ofSeconds(5));

        assertError(() -> authService.refresh(first.refreshToken(), CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);

        assertThat(authService.refresh(second.refreshToken(), CLIENT)).isNotNull();
    }

    @Test
    void reuseAfterGraceWindowRevokesEverySession() {
        Session first = authService.login(new LoginRequest(email, PASSWORD), CLIENT);
        Session otherDevice = authService.login(new LoginRequest(email, PASSWORD), CLIENT);
        Session second = authService.refresh(first.refreshToken(), CLIENT);
        clock.advance(Duration.ofSeconds(11));

        assertError(() -> authService.refresh(first.refreshToken(), CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);

        assertThat(tokenRow(second.refreshToken()).get("revoke_reason")).isEqualTo("REUSE_DETECTED");
        assertThat(tokenRow(otherDevice.refreshToken()).get("revoke_reason")).isEqualTo("REUSE_DETECTED");
        assertError(() -> authService.refresh(second.refreshToken(), CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void expiredOrUnknownRefreshTokenIsInvalid() {
        Session session = authService.login(new LoginRequest(email, PASSWORD), CLIENT);

        assertError(() -> authService.refresh("token-inexistente", CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        assertError(() -> authService.refresh(" ", CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        clock.advance(Duration.ofDays(7));
        assertError(() -> authService.refresh(session.refreshToken(), CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void refreshOfSuspendedAccountIsForbidden() {
        Session session = authService.login(new LoginRequest(email, PASSWORD), CLIENT);
        jdbc.update("UPDATE auth.users SET status = 'SUSPENDED' WHERE id = ?", userId);

        assertError(() -> authService.refresh(session.refreshToken(), CLIENT), ErrorCode.AUTH_ACCOUNT_SUSPENDED);
    }

    // ----------------------------------------------------------------- logout

    @Test
    void logoutRevokesTheSessionAndIsIdempotent() {
        Session session = authService.login(new LoginRequest(email, PASSWORD), CLIENT);

        authService.logout(session.refreshToken());
        authService.logout(session.refreshToken());
        authService.logout(null);
        authService.logout("token-inexistente");

        assertThat(tokenRow(session.refreshToken()).get("revoke_reason")).isEqualTo("LOGOUT");
        assertError(() -> authService.refresh(session.refreshToken(), CLIENT), ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    // ---------------------------------------------------------------- helpers

    private BusinessRuleException loginFails(String mail, String password) {
        try {
            authService.login(new LoginRequest(mail, password), CLIENT);
        } catch (BusinessRuleException e) {
            return e;
        }
        throw new AssertionError("login should have failed");
    }

    private List<String> attempts() {
        return jdbc.queryForList("""
                SELECT CASE WHEN succeeded THEN 'OK' ELSE failure_reason END
                  FROM auth.login_attempts WHERE user_id = ? ORDER BY id""", String.class, userId);
    }

    private Instant lockedUntil() {
        OffsetDateTime value = jdbc.queryForObject("SELECT locked_until FROM auth.users WHERE id = ?",
                OffsetDateTime.class, userId);
        return value == null ? null : value.toInstant();
    }

    private Map<String, Object> tokenRow(String token) {
        return jdbc.queryForMap("SELECT * FROM auth.refresh_tokens WHERE token_sha256 = ?", sha256(token));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
