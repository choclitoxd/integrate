package com.integrate.modules.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle público de una experiencia aprobada (RF-04, ADR-0039). La referencia es municipal y
 * las coordenadas, cuando se publican, son aproximadas. Se conserva la forma del DTO para los clientes v1.
 */
public record ExperienceDetailResponse(ExperienceSummaryResponse summary, String description, String safetyGuidelines,
                                       String vereda, String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                       String timezone, List<StopResponse> stops,
                                       boolean meetingPointCoordinatesTrusted) {
}
