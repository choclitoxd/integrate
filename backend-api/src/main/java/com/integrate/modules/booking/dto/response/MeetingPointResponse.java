package com.integrate.modules.booking.dto.response;

import com.integrate.modules.catalog.service.model.OperationalLocation;
import java.math.BigDecimal;

/**
 * Punto de encuentro exacto de una reserva confirmada, para su titular (ADR-0046 §1 y §4). Se sirve con
 * {@code Cache-Control: no-store}; nunca se incluye en listados ni en la proyección pública.
 */
public record MeetingPointResponse(String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                   boolean coordinatesTrusted) {

    public static MeetingPointResponse from(OperationalLocation location) {
        return new MeetingPointResponse(location.meetingPoint(), location.latitude(), location.longitude(),
                location.coordinatesTrusted());
    }
}
