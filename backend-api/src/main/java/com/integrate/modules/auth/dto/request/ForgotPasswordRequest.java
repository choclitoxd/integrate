package com.integrate.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solicitud de recuperación de contraseña (ADR-0014 §1). La respuesta es la misma exista o no la cuenta. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {

    @Override
    public String toString() {
        return "ForgotPasswordRequest[redacted]";
    }
}
