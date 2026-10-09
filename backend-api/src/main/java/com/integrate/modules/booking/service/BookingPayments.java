package com.integrate.modules.booking.service;

import com.integrate.modules.booking.service.model.PayableBooking;
import java.util.Optional;
import java.util.UUID;

/**
 * Transiciones de la reserva originadas por el pago (ADR-0009 §2). Interfaz pública de {@code booking}: el módulo
 * {@code payment} nunca modifica reservas por su cuenta. Salvo {@link #find}, las operaciones exigen una transacción
 * abierta por quien llama ({@code propagation = MANDATORY}). Orden de bloqueos: reserva → pago → franja.
 */
public interface BookingPayments {

    /** Lectura sin bloqueo (respuestas de webhooks duplicados, dueño de un pago); vacío si no existe. */
    Optional<PayableBooking> find(UUID bookingId);

    /** Bloquea la reserva con {@code SELECT … FOR UPDATE}; vacío si no existe. */
    Optional<PayableBooking> lockForPayment(UUID bookingId);

    /** {@code PENDING → CONFIRMED}. La reserva debe estar bloqueada por esta transacción. */
    void confirm(UUID bookingId);

    /** {@code PENDING → PAYMENT_FAILED} y devolución de sus cupos. */
    void failPayment(UUID bookingId);

    /** {@code PENDING → CANCELLED_REFUNDED} (pago anulado) y devolución de sus cupos. */
    void voidPending(UUID bookingId);
}
