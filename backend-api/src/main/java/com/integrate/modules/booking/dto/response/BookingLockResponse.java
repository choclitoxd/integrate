package com.integrate.modules.booking.dto.response;

import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.entity.BookingStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva vista por su dueño (contrato §5.2). En el 201 {@code status} es siempre {@code PENDING}; en la
 * reproducción idempotente y en {@code GET /bookings/{id}} es el estado actual (ADR-0008 §5).
 * {@code ttlSecondsRemaining} se recalcula en cada respuesta y es 0 si la reserva ya no está {@code PENDING}.
 */
public record BookingLockResponse(UUID bookingId, BookingStatus status, UUID scheduleId, UUID experienceId,
                                  int lockedSlots, long unitPriceCop, long totalAmountCop, String currency,
                                  Instant createdAt, Instant expiresAt, long ttlSecondsRemaining) {

    public static final String CURRENCY = "COP";

    public static BookingLockResponse from(Booking booking, UUID experienceId, Instant now) {
        long remaining = booking.getStatus() == BookingStatus.PENDING
                ? Math.max(0, Duration.between(now, booking.getExpiresAt()).toSeconds()) : 0;
        return new BookingLockResponse(booking.getId(), booking.getStatus(), booking.getScheduleId(), experienceId,
                booking.getSlotsCount(), booking.getUnitPriceCop().longValueExact(),
                booking.getTotalAmountCop().longValueExact(), CURRENCY, booking.getCreatedAt(),
                booking.getExpiresAt(), remaining);
    }
}
