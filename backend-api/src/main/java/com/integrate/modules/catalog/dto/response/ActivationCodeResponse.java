package com.integrate.modules.catalog.dto.response;

import java.time.Instant;

/** Código de activación emitido por el gestor (ADR-0007 §6). Se muestra una sola vez. */
public record ActivationCodeResponse(String code, Instant expiresAt) {

    @Override
    public String toString() {
        return "ActivationCodeResponse[expiresAt=" + expiresAt + "]";
    }
}
