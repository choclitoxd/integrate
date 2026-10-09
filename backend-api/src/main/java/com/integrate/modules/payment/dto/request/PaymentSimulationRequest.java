package com.integrate.modules.payment.dto.request;

import com.integrate.modules.payment.gateway.GatewayStatus;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del simulador de pagos de {@code dev} (ADR-0009 §1, punto C). */
public record PaymentSimulationRequest(@NotNull GatewayStatus status) {
}
