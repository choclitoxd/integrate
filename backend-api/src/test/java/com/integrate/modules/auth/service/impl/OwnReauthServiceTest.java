package com.integrate.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserStatus;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Reglas de la confirmación de identidad de ADR-0058 (sin base de datos). */
class OwnReauthServiceTest {

    private static final String PASSWORD = "clave-correcta";
    private static final Instant START = Instant.parse("2026-11-10T12:00:00Z");

    private final UUID id = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final MutableClock clock = new MutableClock(START);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final ClientInfo client = new ClientInfo(loopback(), "test");
    private OwnReauthService service;

    @BeforeEach
    void setUp() throws Exception {
        User user = mock(User.class);
        when(user.isClosed()).thenReturn(false);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getPasswordHash()).thenReturn("hash");
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(encoder.matches(anyString(), any())).thenAnswer(call -> PASSWORD.equals(call.getArgument(0)));
        service = new OwnReauthService(users, encoder, audit, clock);
    }

    @Test
    void correctPasswordIssuesTokenBoundToThePerson() {
        var grant = service.issue(id, PASSWORD, client);

        assertThat(grant.expiresAt()).isEqualTo(START.plus(OwnReauthService.TOKEN_TTL));
        service.require(id, grant.reauthToken());
        assertThatThrownBy(() -> service.require(other, grant.reauthToken())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void missingOrUnknownTokenIsRejected() {
        assertThatThrownBy(() -> service.require(id, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.require(id, "")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.require(id, "inventado")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void tokenExpiresAfterFiveMinutes() {
        var grant = service.issue(id, PASSWORD, client);
        clock.set(START.plus(OwnReauthService.TOKEN_TTL));

        assertThatThrownBy(() -> service.require(id, grant.reauthToken()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void wrongPasswordDoesNotIssueAndFivesFailuresLockTheAccount() {
        for (int attempt = 0; attempt < OwnReauthService.MAX_FAILURES; attempt++) {
            assertThatThrownBy(() -> service.issue(id, "incorrecta", client))
                    .isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> service.issue(id, PASSWORD, client))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_REAUTH_RATE_LIMITED));

        clock.set(START.plus(OwnReauthService.FAILURE_WINDOW));
        assertThat(service.issue(id, PASSWORD, client).reauthToken()).isNotBlank();
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Reloj controlable: la prueba adelanta el tiempo sin esperar. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant next) {
            this.now = next;
        }

        @Override public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }
}
