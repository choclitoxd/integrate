package com.integrate.modules.offline.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lista delta de revocación (ADR-0032 §3).
 *
 * @param watermark mayor {@code revoked_at} devuelto, o el {@code since} recibido si no hay novedades
 */
public record RevocationsResponse(List<Item> items, Instant watermark) {

    public record Item(UUID voucherId, String shortCode, Instant revokedAt, String revocationReason) {
    }
}
