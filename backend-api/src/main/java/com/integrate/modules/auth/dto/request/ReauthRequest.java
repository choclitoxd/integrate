package com.integrate.modules.auth.dto.request;

import jakarta.validation.constraints.NotNull;

/** Confirmación de identidad para editar la cuenta propia (ADR-0058). */
public record ReauthRequest(@NotNull String password) {
    @Override public String toString() { return "ReauthRequest[redacted]"; }
}
