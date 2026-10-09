package com.integrate.common.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * Bucket privado de vouchers (ADR-0010 §7). A diferencia de {@link DocumentStorage}, aquí el backend sí escribe el
 * binario (el PDF que genera) y entrega descargas por URL prefirmada de corta vigencia.
 */
public interface VoucherDocumentStorage {

    void put(String key, byte[] content, String contentType);

    boolean exists(String key);

    /** URL {@code GET} firmada para {@code key}, vigente durante {@code ttl}. */
    PresignedDownload presignDownload(String key, Duration ttl);

    record PresignedDownload(URI url, Instant expiresAt) {
    }
}
