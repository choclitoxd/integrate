package com.integrate.modules.offline.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Canje manual aceptado (ADR-0032 §4). */
public record RedeemResponse(UUID voucherId, UUID bookingId, int slotsCount, String holderName,
                             String experienceTitle, Instant redeemedAt) {
}
