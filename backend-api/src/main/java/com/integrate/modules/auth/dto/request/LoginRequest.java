package com.integrate.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrato {@code LoginRequest}. La contraseña nunca se registra en logs. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotEmpty @Size(max = 128) @Pattern(regexp = "^[^\\x00]+$", message = "must not contain null characters")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
