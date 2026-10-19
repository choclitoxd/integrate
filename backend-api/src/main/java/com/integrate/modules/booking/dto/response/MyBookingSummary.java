package com.integrate.modules.booking.dto.response;

import com.integrate.modules.booking.entity.BookingStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarjeta de "Mis experiencias" (ADR-0023 §1). Sin datos de los viajeros, EPS ni contacto de emergencia.
 *
 * @param hasVoucher {@code true} si la reserva fue pagada ({@code CONFIRMED} o {@code EXECUTED}): su voucher se
 *                   consulta en {@code GET /bookings/{id}/voucher}
 */
public record MyBookingSummary(UUID bookingId, BookingStatus status, UUID experienceId, String experienceTitle,
                               String municipality, Instant startTime, Instant endTime, String timezone, int slots,
                               long totalAmountCop, boolean hasVoucher) {

    public static boolean hasVoucher(BookingStatus status) {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.EXECUTED;
    }
}
