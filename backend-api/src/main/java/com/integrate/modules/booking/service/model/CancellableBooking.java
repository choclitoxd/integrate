package com.integrate.modules.booking.service.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Reserva bloqueada para cancelarla (ADR-0034 §2); nunca expone la EPS ni el contacto de emergencia. */
public record CancellableBooking(UUID bookingId, UUID touristId, UUID scheduleId, String status, int slots,
                                 BigDecimal totalAmountCop) {

    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }
}