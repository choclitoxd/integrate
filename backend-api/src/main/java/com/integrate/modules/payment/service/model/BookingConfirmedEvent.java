package com.integrate.modules.payment.service.model;

import java.util.UUID;

/**
 * Reserva confirmada por un pago aprobado (ADR-0009 §6). Se publica dentro de la transacción del webhook y los
 * oyentes lo reciben con {@code @TransactionalEventListener(AFTER_COMMIT)}. Es una señal de baja latencia: la
 * durabilidad la da el escaneo de reservas {@code CONFIRMED} sin voucher (T-1.8).
 */
public record BookingConfirmedEvent(UUID bookingId, UUID paymentId) {
}
