package com.integrate.modules.payment.service.model;

import java.time.Instant;
import java.util.UUID;

/**
 * La reserva se canceló (ADR-0034 §2). Se publica dentro de la transacción de la cancelación y se atiende en el
 * mismo hilo y la misma transacción: el voucher se revoca con ella, nunca después.
 */
public record BookingCancelledEvent(UUID bookingId, Instant at, String reason) {
}