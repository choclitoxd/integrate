package com.integrate.modules.offline.service;

import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest;
import com.integrate.modules.offline.dto.response.OfflineSyncBatchResponse;
import java.util.UUID;

/** Sincronización por lotes de las validaciones hechas sin conexión (RF-18, ADR-0033). */
public interface SyncBatchService {

    /**
     * @throws com.integrate.common.exception.BusinessRuleException 403 si el dispositivo no es del invocador o no está
     *         activo, 409 {@code SYNC_BATCH_PAYLOAD_MISMATCH} si el {@code syncBatchId} ya se usó con otro contenido,
     *         422 si el lote es inválido
     */
    OfflineSyncBatchResponse sync(UUID userId, String userAgent, OfflineSyncBatchRequest request);
}