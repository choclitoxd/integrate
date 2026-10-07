package com.integrate.modules.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obligatorio de rechazo, suspensión, retiro o cancelación; queda en el historial (RF-07, RF-08). */
public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
}
