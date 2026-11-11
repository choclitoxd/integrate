package com.integrate.modules.auth.dto.response;

import java.time.Instant;

/** Token opaco de vida corta que habilita las ediciones de la cuenta propia (ADR-0058). */
public record ReauthResponse(String reauthToken, Instant expiresAt) {
    @Override public String toString() { return "ReauthResponse[redacted]"; }
}
