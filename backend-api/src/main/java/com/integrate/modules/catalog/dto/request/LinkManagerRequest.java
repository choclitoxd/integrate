package com.integrate.modules.catalog.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Acreditación de un usuario existente como gestor de enlace de una asociación (ADR-0007 §2). */
public record LinkManagerRequest(@NotNull UUID userId, @NotNull UUID associationId, @NotNull LocalDate accreditedOn) {
}
