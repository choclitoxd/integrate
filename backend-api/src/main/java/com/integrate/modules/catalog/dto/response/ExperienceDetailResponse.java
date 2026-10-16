package com.integrate.modules.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Detalle público de una experiencia aprobada (RF-04). Sin datos del anfitrión (R-05). */
public record ExperienceDetailResponse(ExperienceSummaryResponse summary, String description, String safetyGuidelines,
                                       String vereda, String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                       String timezone, List<StopResponse> stops) {
}
