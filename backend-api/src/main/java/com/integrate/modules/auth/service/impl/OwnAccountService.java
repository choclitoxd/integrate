package com.integrate.modules.auth.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.dto.request.ChangeOwnPasswordRequest;
import com.integrate.modules.auth.dto.request.UpdateOwnProfileRequest;
import com.integrate.modules.auth.dto.response.OwnProfileResponse;
import com.integrate.modules.auth.entity.RevokeReason;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserStatus;
import com.integrate.modules.auth.repository.CredentialActivationCodeRepository;
import com.integrate.modules.auth.repository.PasswordResetTokenRepository;
import com.integrate.modules.auth.repository.RefreshTokenRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnAccountService {
    private final UserRepository users;
    private final IdentityProtection identity;
    private final PasswordEncoder encoder;
    private final PasswordResetTokenRepository resets;
    private final CredentialActivationCodeRepository activations;
    private final RefreshTokenRepository refreshTokens;
    private final AuditTrail audit;
    private final SensitiveAccessRecorder access;
    private final Clock clock;
    // Límite por instancia, acotado y con expiración; reiniciar el proceso reinicia la ventana.
    private final Map<UUID, Deque<Instant>> attempts = new HashMap<>();

    @Transactional
    public OwnProfileResponse get(UUID id, ClientInfo client) {
        User user = active(users.findById(id).orElseThrow(AuthErrors::invalidCredentials));
        OwnProfileResponse response = new OwnProfileResponse(user.getFullName(), identity.readPhone(user));
        access.record(new SensitiveAccessRecorder.Access("auth", "users", id.toString(), "OWN_PROFILE_READ",
                List.of("fullName", "phone"), List.of(id.toString()), id, client.ipAddress(), client.userAgent()));
        return response;
    }

    @Transactional
    public OwnProfileResponse update(UUID id, UpdateOwnProfileRequest request, ClientInfo client) {
        User user = active(users.findByIdForUpdate(id).orElseThrow(AuthErrors::invalidCredentials));
        boolean nameChanged = !user.getFullName().equals(request.fullName());
        boolean phoneChanged = !Objects.equals(identity.readPhone(user), request.phone());
        user.updateFullName(request.fullName());
        identity.changePhone(user, request.phone());
        OwnProfileResponse result = new OwnProfileResponse(user.getFullName(), identity.readPhone(user));
        audit.record(new AuditEntry("auth", "users", id.toString(), "OWN_PROFILE_UPDATED", AuditEntry.Severity.INFO,
                id, client.ipAddress(), client.userAgent(), Map.of("fullNameChanged", nameChanged,
                        "phoneChanged", phoneChanged)));
        return result;
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void changePassword(UUID id, ChangeOwnPasswordRequest request, ClientInfo client) {
        User user = active(users.findByIdForUpdate(id).orElseThrow(AuthErrors::invalidCredentials));
        try {
            limit(id);
            PasswordPolicy.check(request.newPassword(), "newPassword");
        } catch (BusinessRuleException ex) {
            audit.record(new AuditEntry("auth", "users", id.toString(), "OWN_PASSWORD_CHANGE_FAILED",
                    AuditEntry.Severity.WARNING, id, client.ipAddress(), client.userAgent(),
                    Map.of("reason", ex.getErrorCode().name())));
            throw ex;
        }
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            audit.record(new AuditEntry("auth", "users", id.toString(), "OWN_PASSWORD_CHANGE_FAILED",
                    AuditEntry.Severity.WARNING, id, client.ipAddress(), client.userAgent(), Map.of()));
            throw AuthErrors.invalidField("currentPassword", "incorrect password");
        }
        Instant now = clock.instant();
        user.resetCredentials(encoder.encode(request.newPassword()));
        resets.findLiveForUpdate(id).ifPresent(token -> token.revoke(now));
        activations.findLiveForUpdate(id).ifPresent(code -> code.revoke(now));
        refreshTokens.revokeAllActive(id, RevokeReason.PASSWORD_CHANGE, now);
        audit.record(new AuditEntry("auth", "users", id.toString(), "OWN_PASSWORD_CHANGED", AuditEntry.Severity.INFO,
                id, client.ipAddress(), client.userAgent(), Map.of()));
    }

    private User active(User user) {
        if (user.isClosed() || user.getStatus() != UserStatus.ACTIVE) throw AuthErrors.invalidCredentials();
        return user;
    }
    private void limit(UUID id) {
        Instant now = clock.instant();
        synchronized (attempts) {
            attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                    || !entry.getValue().peekLast().isAfter(now.minusSeconds(60)));
            if (!attempts.containsKey(id) && attempts.size() >= 10000) {
                throw new BusinessRuleException(ErrorCode.AUTH_PASSWORD_CHANGE_RATE_LIMITED)
                        .withHeader(HttpHeaders.RETRY_AFTER, "60");
            }
            Deque<Instant> window = attempts.computeIfAbsent(id, ignored -> new ArrayDeque<>());
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minusSeconds(60))) window.removeFirst();
            if (window.size() >= 5) throw new BusinessRuleException(ErrorCode.AUTH_PASSWORD_CHANGE_RATE_LIMITED)
                    .withHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1,
                            window.peekFirst().plusSeconds(60).getEpochSecond() - now.getEpochSecond())));
            window.addLast(now);
        }
    }
}
