package com.integrate.modules.booking.service;

import com.integrate.modules.booking.dto.response.BookingLockResponse;
import java.util.UUID;

/** Consulta de una reserva por su dueño ({@code GET /api/v1/bookings/{id}}, ADR-0008 §1). */
public interface BookingQueryService {

    /** @throws com.integrate.common.exception.BusinessRuleException 404 si no existe o pertenece a otro turista */
    BookingLockResponse get(UUID touristId, UUID bookingId);
}
