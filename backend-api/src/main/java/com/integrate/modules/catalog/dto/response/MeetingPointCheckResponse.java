package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.meetingpoint.MeetingPointState;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado de la verificación del punto de encuentro (ADR-0030). Lo ven el anfitrión dueño y el administrador;
 * la sugerencia nunca se aplica sola.
 */
public record MeetingPointCheckResponse(MeetingPointState status, Integer distanceMeters, BigDecimal suggestedLatitude,
                                        BigDecimal suggestedLongitude, Instant checkedAt) {

    public static MeetingPointCheckResponse from(Experience e) {
        return new MeetingPointCheckResponse(e.getMeetingPointCheckStatus(), e.getMeetingPointCheckDistanceM(),
                e.getMeetingPointSuggestedLat(), e.getMeetingPointSuggestedLng(), e.getMeetingPointCheckedAt());
    }
}
