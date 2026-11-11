package com.integrate.modules.auth.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.dto.response.ReauthResponse;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserStatus;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayDeque;

/**
 * Confirmación de identidad antes de editar la cuenta propia (ADR-0058, enmendado el 2026-11-10).
 *
 * <p>La contraseña correcta emite un token opaco de 5 minutos ligado a una persona. Solo se guarda su
 * huella SHA-256; el token en claro se entrega una vez. Se usa un token opaco y no un JWT porque el
 * decodificador de acceso no valida {@code aud}: un JWT de confirmación firmado con la misma clave
 * podría usarse como token de sesión.
 *
 * <p>Estado en memoria del proceso, igual que el límite de cambio de contraseña: no requiere tabla
 * nueva. Con varias instancias, cada una lleva su propio conteo.
 */
@Service
@RequiredArgsConstructor
public class OwnReauthService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditTrail audit;
    private final Clock clock;
    private final Map<String, Grant> grants = new HashMap<>();
    private final Map<UUID, Deque<Instant>> failures = new HashMap<>();

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public ReauthResponse issue(UUID id, String password, ClientInfo client) {
        User user = users.findById(id).orElseThrow(AuthErrors::invalidCredentials);
        if (user.isClosed() || user.getStatus() != UserStatus.ACTIVE) throw AuthErrors.invalidCredentials();
        Instant now = clock.instant();
        synchronized (this) {
            checkNotLocked(id, now, client);
        }
        // Fuera del bloque sincronizado: la comparación de contraseñas es lenta y no debe frenar a otros.
        if (!encoder.matches(password, user.getPasswordHash())) {
            synchronized (this) {
                failures.computeIfAbsent(id, ignored -> new ArrayDeque<>()).addLast(now);
            }
            audit(id, "OWN_REAUTH_FAILED", AuditEntry.Severity.WARNING, client);
            throw AuthErrors.invalidField("password", "incorrect password");
        }
        String token = newToken();
        Instant expiresAt = now.plus(TOKEN_TTL);
        synchronized (this) {
            failures.remove(id);
            grants.values().removeIf(grant -> !grant.expiresAt().isAfter(now));
            grants.put(hash(token), new Grant(id, expiresAt));
        }
        audit(id, "OWN_REAUTH_OK", AuditEntry.Severity.INFO, client);
        return new ReauthResponse(token, expiresAt);
    }

    /** Lanza 403 si el token falta, venció o pertenece a otra persona. */
    public void require(UUID id, String token) {
        if (token == null || token.isBlank()) throw new BusinessRuleException(ErrorCode.AUTH_REAUTH_REQUIRED);
        Instant now = clock.instant();
        Grant grant;
        synchronized (this) {
            grant = grants.get(hash(token));
        }
        if (grant == null || !grant.userId().equals(id) || !grant.expiresAt().isAfter(now)) {
            throw new BusinessRuleException(ErrorCode.AUTH_REAUTH_REQUIRED);
        }
    }

    private void checkNotLocked(UUID id, Instant now, ClientInfo client) {
        Deque<Instant> window = failures.get(id);
        if (window == null) return;
        while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(FAILURE_WINDOW))) window.removeFirst();
        if (window.isEmpty()) {
            failures.remove(id);
            return;
        }
        if (window.size() >= MAX_FAILURES) {
            long retry = Math.max(1, window.peekFirst().plus(FAILURE_WINDOW).getEpochSecond() - now.getEpochSecond());
            audit(id, "OWN_REAUTH_LOCKED", AuditEntry.Severity.WARNING, client);
            throw new BusinessRuleException(ErrorCode.AUTH_REAUTH_RATE_LIMITED)
                    .withHeader(HttpHeaders.RETRY_AFTER, Long.toString(retry));
        }
    }

    private void audit(UUID id, String action, AuditEntry.Severity severity, ClientInfo client) {
        audit.record(new AuditEntry("auth", "users", id.toString(), action, severity, id,
                client.ipAddress(), client.userAgent(), Map.of()));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private record Grant(UUID userId, Instant expiresAt) {
    }
}
