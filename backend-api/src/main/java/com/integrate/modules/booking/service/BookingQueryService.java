package com.integrate.modules.booking.service;

import com.integrate.modules.booking.dto.response.BookingLockResponse;
import com.integrate.modules.booking.dto.response.MeetingPointResponse;
import java.util.UUID;

/** Consulta de una reserva por su dueño ({@code GET /api/v1/bookings/{id}}, ADR-0008 §1). */
public interface BookingQueryService {

    /** @throws com.integrate.common.exception.BusinessRuleException 404 si no existe o pertenece a otro turista */
    BookingLockResponse get(UUID touristId, UUID bookingId);

    /**
     * Punto de encuentro exacto de la reserva confirmada de su dueño (ADR-0046 §1 y §4).
     *
     * @throws com.integrate.common.exception.BusinessRuleException 404 si no existe, es ajena o no está CONFIRMED
     */
    MeetingPointResponse meetingPoint(UUID touristId, UUID bookingId);
}
