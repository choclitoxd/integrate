package com.integrate.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Llave de firma de la sesión (ADR-0005 §2). {@code signingKey} es una llave privada RSA ≥ 2048
 * en PKCS#8 PEM codificada en Base64 en una sola línea ({@code JWT_SIGNING_KEY}); {@code keyId}
 * es el {@code kid} del header ({@code JWT_SIGNING_KEY_ID}). Ambas son obligatorias.
 */
@ConfigurationProperties("integrate.jwt")
public record JwtProperties(String signingKey, String keyId, @DefaultValue("integrate-api") String issuer) {

    @Override
    public String toString() {
        // Nunca imprimir la llave.
        return "JwtProperties[keyId=" + keyId + ", issuer=" + issuer + "]";
    }
}
