package com.integrate.modules.offline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Código de contingencia de 8 caracteres; se normaliza a mayúsculas en el servidor (ADR-0032 §4). */
public record RedeemRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{8}$") String shortCode) {
}
