package com.integrate.modules.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle público de una experiencia aprobada (RF-04). Sin datos del anfitrión (R-05).
 * {@code meetingPointCoordinatesTrusted} es {@code false} cuando la verificación del punto de encuentro quedó en
 * {@code REVIEW} o {@code MISMATCH}: la PWA centra entonces el mapa por la dirección escrita (ADR-0030 §5).
 */
public record ExperienceDetailResponse(ExperienceSummaryResponse summary, String description, String safetyGuidelines,
                                       String vereda, String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                       String timezone, List<StopResponse> stops,
                                       boolean meetingPointCoordinatesTrusted) {
}
