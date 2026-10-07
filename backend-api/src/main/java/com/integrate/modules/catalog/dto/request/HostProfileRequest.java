package com.integrate.modules.catalog.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Alta de anfitrión: un usuario existente pasa a ser prestador de una asociación (ADR-0006 §3). */
public record HostProfileRequest(@NotNull UUID userId, @NotNull UUID associationId) {
}
