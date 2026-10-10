package com.integrate.modules.auth.service;

import com.integrate.modules.auth.service.model.ClientInfo;

/** Recuperación de contraseña por correo con un enlace de un solo uso (RF-01, ADR-0014). */
public interface PasswordResetService {

    /**
     * Si {@code email} pertenece a una cuenta activa con correo entregable y no superó el límite de solicitudes,
     * emite un token nuevo (revoca el anterior) y encola el correo con el enlace en la misma transacción. En
     * cualquier otro caso no hace nada: el invocador responde igual exista o no la cuenta.
     */
    void requestReset(String email, ClientInfo client);

    /**
     * Canjea el token por la contraseña nueva: cuenta activa y desbloqueada, token consumido, sesiones revocadas
     * ({@code PASSWORD_CHANGE}) y aviso por correo.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code VALIDATION_FAILED} (422) si la contraseña
     *         no cumple la política; {@code AUTH_RESET_TOKEN_INVALID} (401) si el token no existe, venció, ya se usó,
     *         fue reemplazado o la cuenta no puede restablecerse
     */
    void resetPassword(String token, String newPassword);
}
