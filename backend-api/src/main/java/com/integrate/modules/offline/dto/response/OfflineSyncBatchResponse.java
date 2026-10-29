package com.integrate.modules.offline.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Resultado del lote (contrato §7.2): siempre 200 si el lote es válido, con un resultado por ítem y en el mismo orden. */
public record OfflineSyncBatchResponse(UUID syncBatchId, String deviceId, boolean replayed, Instant deviceSentAt,
                                       Instant serverReceivedAt, Instant processedAt, long clockSkewSeconds,
                                       Summary summary, List<Item> items) {

    public record Summary(int received, int confirmed, int conflicts, int rejected) {
    }

    /** @param status {@code CONFIRMED | CONFLICT | REJECTED} */
    public record Item(UUID localValidationId, UUID voucherId, String status, String serverOutcome,
                       String bookingStatus, String incidentType, UUID incidentId, String message) {
    }

    public OfflineSyncBatchResponse asReplay() {
        return new OfflineSyncBatchResponse(syncBatchId, deviceId, true, deviceSentAt, serverReceivedAt, processedAt,
                clockSkewSeconds, summary, items);
    }
}