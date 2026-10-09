package com.integrate.modules.booking.service.model;

import com.integrate.modules.booking.dto.response.BookingLockResponse;

/**
 * @param replayed {@code true} si la respuesta reproduce una reserva creada antes con la misma clave
 *                 (200 + {@code Idempotency-Replayed: true}); {@code false} si se creó ahora (201)
 */
public record LockOutcome(BookingLockResponse booking, boolean replayed) {
}
