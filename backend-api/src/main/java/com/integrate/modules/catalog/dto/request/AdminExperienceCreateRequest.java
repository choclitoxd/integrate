package com.integrate.modules.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Alta de una experiencia por el administrador a nombre de un anfitrión (ADR-0048 §2). */
public record AdminExperienceCreateRequest(
        @NotNull UUID hostProfileId,
        @NotNull @Valid ExperienceRequest experience) {
}

