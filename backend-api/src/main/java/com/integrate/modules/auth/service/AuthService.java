package com.integrate.modules.auth.service;

import com.integrate.modules.auth.dto.request.LoginRequest;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.Session;

/** Ciclo de vida de la sesión (RF-01, ADR-0005 §4-§5). */
public interface AuthService {

    /**
     * Autentica con correo y contraseña.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code AUTH_INVALID_CREDENTIALS} (401),
     *         {@code AUTH_ACCOUNT_SUSPENDED} (403) o {@code AUTH_ACCOUNT_LOCKED} (423 + {@code lockedUntil})
     */
    Session login(LoginRequest request, ClientInfo client);

    /**
     * Rota el refresh token presentado y emite una sesión nueva.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code AUTH_REFRESH_TOKEN_INVALID} (401)
     *         o {@code AUTH_ACCOUNT_SUSPENDED} (403)
     */
    Session refresh(String refreshToken, ClientInfo client);

    /** Revoca la sesión del refresh token presentado. Idempotente: un token ausente o inválido no es error. */
    void logout(String refreshToken);
}
