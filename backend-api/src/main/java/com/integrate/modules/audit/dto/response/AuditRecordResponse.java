package com.integrate.modules.audit.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Fila de la bitácora (ADR-0035). {@code details} nunca contiene datos sensibles en claro: se redactan al escribir. */
public record AuditRecordResponse(long id, Instant createdAt, String module, String entityName, String entityId,
                                  String action, String severity, UUID actorId, String actorIp, String userAgent,
                                  Map<String, Object> details) {
}