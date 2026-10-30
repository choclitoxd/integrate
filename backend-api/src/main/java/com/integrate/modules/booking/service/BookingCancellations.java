package com.integrate.modules.booking.service;

import com.integrate.modules.booking.service.model.CancellableBooking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cancelación de una reserva confirmada por el turista (ADR-0034 §2). Interfaz pública de {@code booking}; la usa el
 * servicio de cancelación de {@code payment}. Ambas operaciones exigen una transacción abierta por quien llama.
 */
public interface BookingCancellations {

    /** Bloquea la reserva con {@code SELECT … FOR UPDATE}; vacío si no existe. */
    Optional<CancellableBooking> lock(UUID bookingId);

    /**
     * {@code CONFIRMED → CANCELLED_REFUNDED} (anticipada, devuelve los cupos) o {@code CANCELLED_NO_REFUND} (tardía,
     * los conserva) y registra el hecho en {@code booking_cancellations}.
     *
     * @return identificador del registro de cancelación (referencia del reembolso)
     */
    long cancelByTourist(UUID bookingId, UUID touristId, String reason, boolean early, BigDecimal hoursBeforeStart,
                         BigDecimal touristRefundPct, Instant cancelledAt);
}