package com.integrate.modules.catalog.service.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Datos de una franja para liquidar un pago (ADR-0009 §2).
 *
 * @param associationId asociación del anfitrión al momento de la venta: beneficiaria de la dispersión (D7)
 * @param endTime       fin de la franja; la dispersión se libera {@code payout_grace_hours} después
 */
public record ScheduleSettlement(UUID scheduleId, UUID associationId, Instant endTime) {
}
