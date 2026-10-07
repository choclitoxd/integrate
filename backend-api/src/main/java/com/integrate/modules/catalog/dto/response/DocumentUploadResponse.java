package com.integrate.modules.catalog.dto.response;

import java.net.URI;
import java.time.Instant;

/**
 * URL prefirmada para subir el PDF directamente a S3 (ADR-0007 §5). El cliente hace {@code PUT uploadUrl}
 * con la cabecera {@code Content-Type: contentType} antes de {@code expiresAt} y luego envía {@code s3Key}
 * en el registro asistido.
 */
public record DocumentUploadResponse(String s3Key, URI uploadUrl, String contentType, long maxSizeBytes,
                                     Instant expiresAt) {
}
