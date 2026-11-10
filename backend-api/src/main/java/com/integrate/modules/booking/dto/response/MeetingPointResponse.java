package com.integrate.modules.booking.dto.response;

import com.integrate.modules.catalog.service.model.OperationalLocation;
import java.math.BigDecimal;
import java.util.List;

/**
 * Punto de encuentro exacto y paradas del recorrido de una reserva confirmada, para su titular (ADR-0046 §1 y §4,
 * ADR-0049). Se sirve con {@code Cache-Control: no-store}; nunca se incluye en listados ni en la proyección pública.
 */
public record MeetingPointResponse(String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                   boolean coordinatesTrusted, List<StopResponse> stops) {

    /** Parada del recorrido en orden; {@code description} puede ser {@code null}. */
    public record StopResponse(int position, String name, String description) {
    }

    public static MeetingPointResponse from(OperationalLocation location) {
        List<StopResponse> stops = location.stops().stream()
                .map(stop -> new StopResponse(stop.position(), stop.name(), stop.description()))
                .toList();
        return new MeetingPointResponse(location.meetingPoint(), location.latitude(), location.longitude(),
                location.coordinatesTrusted(), stops);
    }
}
