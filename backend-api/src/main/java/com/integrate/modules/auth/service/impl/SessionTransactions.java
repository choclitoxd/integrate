package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.modules.auth.entity.CredentialStatus;
import com.integrate.modules.auth.entity.LoginAttempt;
import com.integrate.modules.auth.entity.LoginFailureReason;
import com.integrate.modules.auth.entity.RefreshToken;
import com.integrate.modules.auth.entity.RevokeReason;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserStatus;
import com.integrate.modules.auth.repository.LoginAttemptRepository;
import com.integrate.modules.auth.repository.RefreshTokenRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.AuthProperties;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.Session;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacciones cortas de la sesión (ADR-0005 §4-§5). Los rechazos de negocio <b>confirman</b> la
 * transacción ({@code noRollbackFor}): el intento fallido, el bloqueo o la revocación por
 * reutilización deben quedar persistidos aunque la petición termine en 401/403/423.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SessionTransactions {

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionIssuer sessionIssuer;
    private final AuthProperties properties;
    private final Clock clock;

    /** Correo sin cuenta: se registra el intento y se responde igual que una contraseña errónea. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Session rejectUnknownUser(String email, ClientInfo client) {
        loginAttemptRepository.save(LoginAttempt.failure(null, email, client.ipAddress(), client.userAgent(),
                LoginFailureReason.UNKNOWN_USER, clock.instant()));
        throw AuthErrors.invalidCredentials();
    }

    /**
     * Cierra el login de una cuenta existente con la fila del usuario bloqueada ({@code FOR UPDATE}),
     * de modo que intentos concurrentes no superen el umbral de bloqueo.
     *
     * @param passwordMatches resultado de la verificación Argon2id, hecha fuera de la transacción
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Session completeLogin(UUID userId, String email, boolean passwordMatches, ClientInfo client) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AuthErrors::invalidCredentials);
        Instant now = clock.instant();

        if (user.isLockedAt(now)) {
            recordFailure(user, email, client, LoginFailureReason.ACCOUNT_LOCKED, now);
            throw AuthErrors.accountLocked(user.getLockedUntil(), now);
        }
        if (!passwordMatches) {
            recordFailure(user, email, client, LoginFailureReason.BAD_CREDENTIALS, now);
            if (reachedFailureThreshold(user, now)) {
                Instant lockedUntil = now.plus(properties.lockout().duration());
                user.lockUntil(lockedUntil);
                log.warn("Account {} locked until {} after repeated failed logins", user.getId(), lockedUntil);
                throw AuthErrors.accountLocked(lockedUntil, now);
            }
            throw AuthErrors.invalidCredentials();
        }
        // Contraseña correcta, pero la cuenta no puede iniciar sesión.
        if (user.isClosed() || user.getCredentialStatus() == CredentialStatus.PENDING_RESET) {
            recordFailure(user, email, client, LoginFailureReason.BAD_CREDENTIALS, now);
            throw AuthErrors.invalidCredentials();
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            recordFailure(user, email, client, LoginFailureReason.ACCOUNT_SUSPENDED, now);
            throw AuthErrors.accountSuspended();
        }

        loginAttemptRepository.save(LoginAttempt.success(user.getId(), email, client.ipAddress(),
                client.userAgent(), now));
        user.recordSuccessfulLogin(now);
        return sessionIssuer.issue(user, client, now).session();
    }

    /** Registra el intento sobre una cuenta ya bloqueada, sin haber verificado la contraseña. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Session rejectLocked(User user, String email, ClientInfo client) {
        Instant now = clock.instant();
        recordFailure(user, email, client, LoginFailureReason.ACCOUNT_LOCKED, now);
        throw AuthErrors.accountLocked(user.getLockedUntil(), now);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Session rotate(String presentedToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository
                .findByTokenSha256ForUpdate(RefreshTokenCodec.sha256Hex(presentedToken))
                .orElseThrow(AuthErrors::refreshTokenInvalid);

        if (current.isRevoked()) {
            handleRevokedPresentation(current, now);
            throw AuthErrors.refreshTokenInvalid();
        }
        if (current.isExpiredAt(now)) {
            throw AuthErrors.refreshTokenInvalid();
        }
        User user = userRepository.findById(current.getUserId()).orElseThrow(AuthErrors::refreshTokenInvalid);
        if (user.isClosed()) {
            throw AuthErrors.refreshTokenInvalid();
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw AuthErrors.accountSuspended();
        }

        SessionIssuer.IssuedSession next = sessionIssuer.issue(user, client, now);
        current.rotateTo(next.refreshTokenId(), now);
        return next.session();
    }

    @Transactional
    public void revoke(String presentedToken) {
        refreshTokenRepository.findByTokenSha256ForUpdate(RefreshTokenCodec.sha256Hex(presentedToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(RevokeReason.LOGOUT, clock.instant()));
    }

    /**
     * Un token ya rotado que vuelve a presentarse fuera de la ventana de gracia indica robo:
     * se revocan todas las sesiones del titular (no hay {@code family_id} en el esquema).
     */
    private void handleRevokedPresentation(RefreshToken token, Instant now) {
        if (token.getRevokeReason() != RevokeReason.ROTATED) {
            return;
        }
        Instant graceEnd = token.getRevokedAt().plus(properties.refreshReuseGrace());
        if (now.isAfter(graceEnd)) {
            int revoked = refreshTokenRepository.revokeAllActive(token.getUserId(), RevokeReason.REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected for user {}: {} active sessions revoked", token.getUserId(),
                    revoked);
        }
    }

    private void recordFailure(User user, String email, ClientInfo client, LoginFailureReason reason, Instant now) {
        loginAttemptRepository.save(LoginAttempt.failure(user.getId(), email, client.ipAddress(), client.userAgent(),
                reason, now));
    }

    /**
     * Fallos consecutivos ({@code BAD_CREDENTIALS} posteriores al último éxito) dentro de la
     * ventana. Los intentos durante un bloqueo no cuentan: al expirar, el contador ya salió de la ventana.
     */
    private boolean reachedFailureThreshold(User user, Instant now) {
        Instant windowStart = now.minus(properties.lockout().window());
        Instant since = loginAttemptRepository.findLastSuccessAt(user.getId())
                .filter(lastSuccess -> lastSuccess.isAfter(windowStart))
                .orElse(windowStart);
        long failures = loginAttemptRepository.countFailuresSince(user.getId(), LoginFailureReason.BAD_CREDENTIALS,
                since);
        return failures >= properties.lockout().maxFailures();
    }
}
