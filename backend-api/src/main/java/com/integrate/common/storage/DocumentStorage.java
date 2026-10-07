package com.integrate.common.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de almacenamiento de documentos sensibles en el bucket dedicado (ADR-0007 §5). El backend
 * no recibe binarios: entrega URL prefirmadas y verifica después lo que el cliente subió.
 */
public interface DocumentStorage {

    /** URL {@code PUT} firmada para {@code key}, atada a {@code contentType} y vigente durante {@code ttl}. */
    PresignedUpload presignUpload(String key, String contentType, Duration ttl);

    /** Metadatos del objeto, o vacío si no existe. */
    Optional<StoredObject> head(String key);

    record PresignedUpload(URI url, Instant expiresAt) {
    }

    record StoredObject(String contentType, long sizeBytes) {
    }
}
