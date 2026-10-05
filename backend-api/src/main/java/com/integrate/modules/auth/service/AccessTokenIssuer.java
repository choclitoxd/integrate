package com.integrate.modules.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida para emitir el access token de sesión (ADR-0005 §1-§2). Lo implementa
 * {@code security.jwt} con JWT RS256; el módulo {@code auth} no conoce el formato ni la llave.
 */
public interface AccessTokenIssuer {

    IssuedToken issue(UUID userId, List<String> roleClaims, Instant issuedAt);

    record IssuedToken(String value, long expiresInSeconds) {

        @Override
        public String toString() {
            return "IssuedToken[expiresIn=" + expiresInSeconds + "]";
        }
    }
}
