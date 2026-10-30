package com.integrate.modules.booking.service.model;

import java.time.Instant;
import java.util.UUID;

/**
 * La reserva se ejecutó: su voucher se canjeó en campo (ADR-0034 §4). Se publica dentro de la transacción del canje y
 * se atiende en ella (listener síncrono), de modo que la dispersión comunitaria se libera con el canje o no se libera.
 */
public record BookingExecutedEvent(UUID bookingId, Instant at) {
}