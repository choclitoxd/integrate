package com.integrate.security.jwt;

import com.integrate.modules.auth.service.AccessTokenIssuer;
import com.integrate.modules.auth.service.AuthProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Access token del contrato ({@code bearerAuth}): JWT RS256 con {@code kid} en el header y los
 * claims {@code iss}, {@code sub}, {@code roles}, {@code iat}, {@code exp} y {@code jti}.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;

    @Override
    public IssuedToken issue(UUID userId, List<String> roleClaims, Instant issuedAt) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(authProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(ROLES_CLAIM, List.copyOf(roleClaims))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, authProperties.accessTokenTtl().toSeconds());
    }
}
