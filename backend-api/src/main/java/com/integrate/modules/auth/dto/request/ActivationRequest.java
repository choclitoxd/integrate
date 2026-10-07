package com.integrate.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Activación asistida (ADR-0007 §6): el anfitrión canjea el código que le entregó su gestor por una
 * contraseña propia. Ni el código ni la contraseña se registran en logs.
 */
public record ActivationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 20) String code,
        // La política de longitud (12–128) la aplica PasswordPolicy.
        @NotNull String newPassword) {

    @Override
    public String toString() {
        return "ActivationRequest[email=" + email + "]";
    }
}
