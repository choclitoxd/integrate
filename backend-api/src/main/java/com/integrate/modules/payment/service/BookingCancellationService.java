package com.integrate.modules.payment.service;

import com.integrate.modules.payment.dto.response.CancellationResponse;
import java.util.UUID;

/** Cancelación de una reserva por su turista con la regla de 72 horas (RF-13, RF-14, ADR-0034 §2). */
public interface BookingCancellationService {

    /**
     * @throws com.integrate.common.exception.BusinessRuleException 404 si la reserva no existe o es ajena,
     *         409 {@code BOOKING_NOT_CANCELLABLE} si no está {@code CONFIRMED}, 409 {@code BOOKING_ALREADY_STARTED}
     *         si la franja ya empezó
     */
    CancellationResponse cancel(UUID touristId, UUID bookingId, String reason);
}