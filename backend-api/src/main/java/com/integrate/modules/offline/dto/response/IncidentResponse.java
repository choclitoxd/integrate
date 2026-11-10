package com.integrate.modules.offline.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentResponse(UUID id, Status status, Severity severity, Type type, BigDecimal financialImpactCop,
                               OffsetDateTime createdAt, UUID bookingId, UUID voucherId, String resolutionNotes,
                               UUID resolvedBy, OffsetDateTime resolvedAt) {

    public enum Status { OPEN, IN_REVIEW, RESOLVED, DISMISSED }

    public enum Severity { LOW, MEDIUM, HIGH }

    public enum Type { OVER_REDEMPTION_COMMUNITY_PROTECTED, DUPLICATE_OFFLINE_REDEMPTION, CLOCK_SKEW_FUTURE_SCAN }
}
