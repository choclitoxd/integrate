package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.RefreshToken;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.RefreshTokenRepository;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.AccessTokenIssuer;
import com.integrate.modules.auth.service.AuthProperties;
import com.integrate.modules.auth.service.model.AuthenticatedUser;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.Session;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Emite una sesión completa para un usuario ya autenticado: persiste el refresh token (solo su
 * hash), firma el access token con los roles vigentes y calcula el estado de consentimiento.
 * Participa en la transacción del invocador (login o rotación).
 */
@Component
@RequiredArgsConstructor
class SessionIssuer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final ConsentLedger consentLedger;
    private final AuthProperties properties;

    IssuedSession issue(User user, ClientInfo client, Instant now) {
        List<RoleCode> roles = userRoleRepository.findActiveRoleCodes(user.getId());
        List<String> claims = roles.stream().map(RoleCode::claim).toList();

        String refreshToken = RefreshTokenCodec.newToken();
        RefreshToken stored = refreshTokenRepository.save(RefreshToken.issue(user.getId(),
                RefreshTokenCodec.sha256Hex(refreshToken), now, now.plus(properties.refreshTokenTtl()),
                client.ipAddress(), client.userAgent()));

        AccessTokenIssuer.IssuedToken access = accessTokenIssuer.issue(user.getId(), claims, now);
        ConsentStatus consent = consentLedger.status(user.getId(), roles, consentLedger.currentPolicy());
        Session session = new Session(access.value(), access.expiresInSeconds(),
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), claims), consent,
                refreshToken, properties.refreshTokenTtl());
        return new IssuedSession(session, stored.getId());
    }

    record IssuedSession(Session session, UUID refreshTokenId) {
    }
}
