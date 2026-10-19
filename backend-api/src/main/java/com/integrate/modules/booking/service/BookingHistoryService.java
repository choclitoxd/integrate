package com.integrate.modules.booking.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.booking.dto.response.MyBookingSummary;
import java.util.UUID;

/** "Mis experiencias" del turista autenticado (ADR-0023 §1). */
public interface BookingHistoryService {

    enum Scope {
        /** {@code CONFIRMED} o {@code PENDING} aún pagable, con la franja sin terminar; la más próxima primero. */
        UPCOMING,
        /** Todo lo demás: realizadas, pasadas, canceladas y fallidas; la más reciente primero. */
        PAST
    }

    PageResponse<MyBookingSummary> list(UUID touristId, Scope scope, int page, int size);
}
