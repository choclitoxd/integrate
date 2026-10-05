package com.integrate.modules.auth.controller;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Cookie {@code refresh_token} del contrato ({@code SetRefreshCookie}):
 * {@code HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth}. {@code Secure} también en local:
 * los navegadores tratan {@code localhost} como contexto seguro.
 */
final class RefreshCookies {

    static final String NAME = "refresh_token";
    static final String PATH = "/api/v1/auth";

    private RefreshCookies() {
    }

    static String issue(String token, Duration ttl) {
        return base(token).maxAge(ttl).build().toString();
    }

    /** Borra la cookie del navegador (logout o refresh token inválido). */
    static String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Strict").path(PATH);
    }
}
