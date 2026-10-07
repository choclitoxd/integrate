package com.integrate.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Firma y validación del access token de sesión (ADR-0005 §1-§2): RS256 con {@code kid}, una sola
 * llave, emisor {@code integrate-api}. Distinta de la llave de vouchers (T-1.8); no se publica JWKS.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    RsaSigningKeys.KeyPair sessionSigningKeys(JwtProperties properties) {
        if (properties.keyId() == null || properties.keyId().isBlank()) {
            throw new IllegalStateException("JWT_SIGNING_KEY_ID is not set (ADR-0005 §2)");
        }
        return RsaSigningKeys.load(properties.signingKey());
    }

    @Bean
    JwtEncoder jwtEncoder(RsaSigningKeys.KeyPair keys, JwtProperties properties) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(properties.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** Solo RS256 con la llave pública de sesión: un token con otro {@code alg} se rechaza. */
    @Bean
    JwtDecoder jwtDecoder(RsaSigningKeys.KeyPair keys, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
        return decoder;
    }
}
