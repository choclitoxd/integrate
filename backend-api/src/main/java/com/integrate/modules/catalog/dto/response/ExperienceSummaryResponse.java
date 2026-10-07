package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.PhysicalIntensity;
import java.util.List;
import java.util.UUID;

/**
 * Tarjeta pública del catálogo (RF-04). Identifica la oferta por la asociación comunitaria y el
 * municipio; nunca por el anfitrión (R-05, ADR-0006 §6).
 */
public record ExperienceSummaryResponse(UUID id, String title, PhysicalIntensity physicalIntensity,
                                        int durationMinutes, String currency, long unitPriceCop,
                                        int maxCapacityPerSlot, PlaceResponse municipality, PlaceResponse department,
                                        String associationName, List<String> categoryCodes) {

    public static final String CURRENCY = "COP";
}
