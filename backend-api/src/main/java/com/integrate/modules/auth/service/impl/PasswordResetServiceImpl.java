package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.PasswordResetToken;
import com.integrate.modules.auth.entity.RevokeReason;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserStatus;
import com.integrate.modules.auth.repository.CredentialActivationCodeRepository;
import com.integrate.modules.auth.repository.PasswordResetTokenRepository;
import com.integrate.modules.auth.repository.RefreshTokenRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.AuthProperties;
import com.integrate.modules.auth.service.PasswordResetService;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final String SYNTHETIC_SUFFIX = "@" + AssistedAccountService.SYNTHETIC_EMAIL_DOMAIN;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final CredentialActivationCodeRepository activationCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetEmails emails;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void requestReset(String email, ClientInfo client) {
        Optional<User> account = userRepository.findByEmailIgnoreCase(Emails.normalize(email))
                .filter(PasswordResetServiceImpl::canRecoverByEmail);
        if (account.isEmpty()) {
            // Sin rastro en el log del correo consultado: la respuesta y el registro no revelan cuentas.
            log.info("password.reset.request ignored (no eligible account)");
            return;
        }
        User user = account.get();
        Instant now = clock.instant();
        AuthProperties.PasswordReset policy = properties.passwordReset();
        if (tokenRepository.countByUserIdAndRequestedAtAfter(user.getId(), now.minus(policy.window()))
                >= policy.maxRequestsPerWindow()) {
            log.warn("password.reset.request throttled userId={}", user.getId());
            return;
        }
        tokenRepository.findLiveForUpdate(user.getId()).ifPresent(previous -> {
            previous.revoke(now);
            // El índice parcial de un solo token vivo exige que la revocación llegue antes del INSERT.
            tokenRepository.flush();
        });
        String token = RefreshTokenCodec.newToken();
        tokenRepository.save(PasswordResetToken.issue(user.getId(), RefreshTokenCodec.sha256Hex(token), now,
                now.plus(policy.ttl()), client.ipAddress(), client.userAgent()));
        emails.enqueueResetLink(user, token, policy.ttl());
        log.info("password.reset.request issued userId={}", user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordPolicy.check(newPassword, "newPassword");
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        Instant now = clock.instant();
        PasswordResetToken reset = tokenRepository.findByTokenSha256ForUpdate(RefreshTokenCodec.sha256Hex(token.strip()))
                .filter(t -> t.isUsableAt(now))
                .orElseThrow(PasswordResetServiceImpl::invalid);
        User user = userRepository.findByIdForUpdate(reset.getUserId())
                .filter(PasswordResetServiceImpl::canRecoverByEmail)
                .orElseThrow(PasswordResetServiceImpl::invalid);

        user.resetCredentials(passwordEncoder.encode(newPassword));
        reset.consume(now);
        // Un código de activación pendiente (ADR-0007 §6) pierde sentido: la cuenta ya tiene contraseña propia.
        activationCodeRepository.findLiveForUpdate(user.getId()).ifPresent(code -> code.revoke(now));
        emails.enqueueChangedNotice(user, now);
        // Último paso: la consulta masiva vacía el contexto de persistencia tras escribir lo anterior.
        int revoked = refreshTokenRepository.revokeAllActive(user.getId(), RevokeReason.PASSWORD_CHANGE, now);
        log.info("password.reset.completed userId={} revokedSessions={}", user.getId(), revoked);
    }

    /** Cuenta activa, no suspendida ni cerrada, con un correo real al que llega el enlace. */
    private static boolean canRecoverByEmail(User user) {
        return !user.isClosed() && user.getStatus() == UserStatus.ACTIVE
                && !user.getEmail().toLowerCase(Locale.ROOT).endsWith(SYNTHETIC_SUFFIX);
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
    }
}
