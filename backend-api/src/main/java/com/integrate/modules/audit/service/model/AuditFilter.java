package com.integrate.modules.audit.service.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros de la consulta de la bitácora (ADR-0035 §1); todos opcionales.
 *
 * @param subject identificador de un titular afectado: coincide con {@code details.subjects} (accesos sensibles)
 * @param from    inclusive
 * @param to      exclusivo
 */
public record AuditFilter(String module, String action, String entityName, String entityId, UUID actorId,
                          String severity, Instant from, Instant to, String subject) {
}