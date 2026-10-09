package com.integrate.modules.notification.service.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Correo a encolar (ADR-0011 §2).
 *
 * @param templateCode plantilla de {@code resources/mail/} (p. ej. {@code voucher-issued})
 * @param payload      variables de la plantilla, ya formateadas; nunca datos sensibles en claro (EPS, víctima)
 * @param attachments  adjuntos guardados en S3
 */
public record EmailRequest(String aggregateType, String aggregateId, String eventType, UUID recipientUserId,
                           String recipientAddress, String templateCode, Map<String, Object> payload,
                           List<Attachment> attachments) {

    public EmailRequest {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(recipientAddress, "recipientAddress");
        Objects.requireNonNull(templateCode, "templateCode");
        payload = Map.copyOf(payload);
        attachments = List.copyOf(attachments);
    }

    /**
     * @param bucket {@code vouchers}: el único bucket desde el que se adjunta hoy
     * @param key    clave S3 del objeto
     */
    public record Attachment(String bucket, String key, String filename, String contentType) {

        public static final String VOUCHERS = "vouchers";
    }
}
