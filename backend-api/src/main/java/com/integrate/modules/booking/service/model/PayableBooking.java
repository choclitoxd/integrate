package com.integrate.modules.booking.service.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva bloqueada para una operación de pago (ADR-0009 §2). El estado viaja como texto para que {@code payment}
 * no dependa de las entidades de {@code booking}.
 *
 * @param status valor de {@code booking.bookings.status} ({@code PENDING}, {@code CONFIRMED}, …)
 */
public record PayableBooking(UUID bookingId, UUID touristId, UUID scheduleId, String status, int slots,
                             BigDecimal totalAmountCop, Instant expiresAt) {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";

    public boolean isPending() {
        return PENDING.equals(status);
    }

    /** Admite un cobro: sigue {@code PENDING} y su TTL no ha vencido en {@code now}. */
    public boolean isPayableAt(Instant now) {
        return isPending() && expiresAt.isAfter(now);
    }
}
