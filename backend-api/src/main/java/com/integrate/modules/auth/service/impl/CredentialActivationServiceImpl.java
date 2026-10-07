package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.CredentialActivationCode;
import com.integrate.modules.auth.entity.CredentialStatus;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.CredentialActivationCodeRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.AuthProperties;
import com.integrate.modules.auth.service.CredentialActivationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CredentialActivationServiceImpl implements CredentialActivationService {

    private final UserRepository userRepository;
    private final CredentialActivationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedActivationCode issue(UUID userId, UUID issuedBy) {
        User user = userRepository.findById(userId).filter(u -> !u.isClosed())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (user.getCredentialStatus() != CredentialStatus.PENDING_RESET) {
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
                .filter(u -> !u.isClosed() && u.getCredentialStatus() == CredentialStatus.PENDING_RESET);
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
        user.activateCredentials(passwordEncoder.encode(newPassword));
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ErrorCode.AUTH_ACTIVATION_INVALID);
    }
}
