package com.integrate.modules.auth.service.model;

import java.time.Duration;

/**
 * Resultado de un login o una rotación: el access token va en el cuerpo y el refresh token
 * (opaco, en claro solo aquí) en la cookie {@code refresh_token} con vigencia {@code refreshTokenTtl}.
 */
public record Session(String accessToken, long expiresInSeconds, AuthenticatedUser user, ConsentStatus consent,
                      String refreshToken, Duration refreshTokenTtl) {

    @Override
    public String toString() {
        // Nunca imprimir tokens en logs.
        return "Session[user=" + user.id() + ", expiresIn=" + expiresInSeconds + "]";
    }
}
