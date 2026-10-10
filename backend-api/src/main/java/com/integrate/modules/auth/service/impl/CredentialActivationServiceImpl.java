package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.CredentialActivationCode;
import com.integrate.modules.auth.entity.CredentialStatus;
import com.integrate.modules.auth.entity.RevokeReason;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.CredentialActivationCodeRepository;
import com.integrate.modules.auth.repository.RefreshTokenRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.AuthProperties;
import com.integrate.modules.auth.service.CredentialActivationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CredentialActivationServiceImpl implements CredentialActivationService {

    private static final String SYNTHETIC_SUFFIX = "@" + AssistedAccountService.SYNTHETIC_EMAIL_DOMAIN;

    private final UserRepository userRepository;
    private final CredentialActivationCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedActivationCode issue(UUID userId, UUID issuedBy) {
        User user = userRepository.findById(userId).filter(u -> !u.isClosed())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (!canActivate(user)) {
            throw new BusinessRuleException(ErrorCode.CATALOG_INVALID_STATUS_TRANSITION,
                    "The account credentials are already active.")
                    .with("currentStatus", user.getCredentialStatus().name());
        }
        Instant now = clock.instant();
        codeRepository.findLiveForUpdate(userId).ifPresent(previous -> {
            previous.revoke(now);
            // El índice parcial de un solo código vivo exige que la revocación llegue antes del INSERT.
            codeRepository.flush();
        });
        String code = ActivationCodes.newCode();
        Instant expiresAt = now.plus(properties.activationCodeTtl());
        codeRepository.save(CredentialActivationCode.issue(userId,
                RefreshTokenCodec.sha256Hex(ActivationCodes.normalize(code)), issuedBy, now, expiresAt));
        return new IssuedActivationCode(code, expiresAt);
    }

    /**
     * {@code noRollbackFor}: un intento fallido debe quedar contado aunque la respuesta sea 401
     * (mismo criterio que el login, ADR-0005 §4).
     */
    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void activate(String email, String code, String newPassword) {
        PasswordPolicy.check(newPassword, "newPassword");
        Instant now = clock.instant();
        Optional<User> account = userRepository.findByEmailIgnoreCase(Emails.normalize(email))
                .filter(u -> !u.isClosed() && canActivate(u));
        if (account.isEmpty()) {
            throw invalid();
        }
        User user = account.get();
        CredentialActivationCode live = codeRepository.findLiveForUpdate(user.getId())
                .filter(c -> c.isUsableAt(now))
                .orElseThrow(CredentialActivationServiceImpl::invalid);
        String canonical = ActivationCodes.normalize(code);
        if (canonical == null || !MessageDigest.isEqual(
                RefreshTokenCodec.sha256Hex(canonical).getBytes(StandardCharsets.US_ASCII),
                live.getCodeSha256().getBytes(StandardCharsets.US_ASCII))) {
            live.recordFailedAttempt(now);
            throw invalid();
        }
        live.consume(now);
        boolean wasActive = user.getCredentialStatus() == CredentialStatus.ACTIVE;
        user.resetCredentials(passwordEncoder.encode(newPassword));
        if (wasActive) {
            // Recuperación de una cuenta sintética ya activa: la contraseña cambia, las sesiones previas caen.
            refreshTokenRepository.revokeAllActive(user.getId(), RevokeReason.PASSWORD_CHANGE, now);
        }
    }

    /**
     * Pendiente de activar, o con correo sintético: el anfitrión sin correo propio no puede usar la recuperación
     * por correo (ADR-0014 §4) y recupera el acceso con un código nuevo de su gestor. La cuenta no pasa a
     * {@code PENDING_RESET}: la contraseña anterior sigue valiendo hasta que se canjee el código.
     */
    private static boolean canActivate(User user) {
        return user.getCredentialStatus() == CredentialStatus.PENDING_RESET
                || user.getEmail().toLowerCase(Locale.ROOT).endsWith(SYNTHETIC_SUFFIX);
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ErrorCode.AUTH_ACTIVATION_INVALID);
    }
}
