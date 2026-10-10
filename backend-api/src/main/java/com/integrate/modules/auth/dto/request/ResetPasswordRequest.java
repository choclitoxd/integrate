package com.integrate.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Restablecimiento con el token del enlace (ADR-0014 §4). Ni el token ni la contraseña se registran en logs;
 * la política de longitud (12–128) la aplica {@code PasswordPolicy}.
 */
public record ResetPasswordRequest(@NotBlank @Size(max = 128) String token, @NotNull String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[redacted]";
    }
}
