package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.entity.PhysicalIntensity;
import com.integrate.modules.catalog.entity.RouteType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Experiencia vista por su anfitrión o por el administrador: incluye estado y motivo de rechazo. */
public record HostExperienceResponse(UUID id, UUID hostId, String title, String description, String safetyGuidelines,
                                     PhysicalIntensity physicalIntensity, int durationMinutes, String municipalityCode,
                                     String vereda, String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                     RouteType routeType, BigDecimal distanceKm, Integer roundTripMinutes,
                                     List<StopResponse> stops,
                                     int maxCapacityPerSlot, long unitPriceCop, String timezone,
                                     List<String> categoryCodes, ExperienceStatus status, String rejectionReason,
                                     Instant createdAt) {

    public static HostExperienceResponse from(Experience e, List<String> categoryCodes, List<StopResponse> stops) {
        return new HostExperienceResponse(e.getId(), e.getHostId(), e.getTitle(), e.getDescription(),
                e.getSafetyGuidelines(), e.getPhysicalIntensity(), e.getDurationMinutes(), e.getMunicipalityCode(),
                e.getVereda(), e.getMeetingPoint(), e.getLatitude(), e.getLongitude(), e.getRouteType(),
                e.getDistanceKm(), e.getRoundTripMinutes(), List.copyOf(stops), e.getMaxCapacityPerSlot(),
                e.unitPriceCopValue(), e.getTimezone(), List.copyOf(categoryCodes), e.getStatus(),
                e.getRejectionReason(), e.getCreatedAt());
    }
}
