package com.integrate.modules.auth.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros de sesión y bloqueo (RF-01, ADR-0005 §4-§5). Los valores por defecto son los del
 * contrato; solo las pruebas los cambian.
 *
 * @param accessTokenTtl    vida del access token (contrato: 900 s)
 * @param refreshTokenTtl   vida deslizante del refresh token (contrato: 7 días)
 * @param refreshReuseGrace ventana en la que reutilizar un token rotado no revoca las demás sesiones
 */
@ConfigurationProperties("integrate.auth")
public record AuthProperties(
        @DefaultValue("900s") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("10s") Duration refreshReuseGrace,
        @DefaultValue Lockout lockout,
        @DefaultValue("72h") Duration activationCodeTtl,
        @DefaultValue PasswordReset passwordReset) {

    /** Tokens de restablecimiento (ADR-0014 §2): 15 minutos y como máximo 3 solicitudes por cuenta y hora. */
    public record PasswordReset(
            @DefaultValue("15m") Duration ttl,
            @DefaultValue("3") int maxRequestsPerWindow,
            @DefaultValue("1h") Duration window) {
    }

    /** 5 fallos consecutivos en 10 minutos bloquean la cuenta 15 minutos. */
    public record Lockout(
            @DefaultValue("5") int maxFailures,
            @DefaultValue("10m") Duration window,
            @DefaultValue("15m") Duration duration) {
    }
}
