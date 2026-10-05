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
        @DefaultValue Lockout lockout) {

    /** 5 fallos consecutivos en 10 minutos bloquean la cuenta 15 minutos. */
    public record Lockout(
            @DefaultValue("5") int maxFailures,
            @DefaultValue("10m") Duration window,
            @DefaultValue("15m") Duration duration) {
    }
}
