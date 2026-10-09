package com.integrate.common.audit;

import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de {@code audit.audit_trail} (RF-20, ADR-0008 §6). {@code id}, {@code prev_hash} y {@code row_hash}
 * los asigna el trigger de encadenamiento. {@code details} nunca lleva datos sensibles en claro: solo
 * referencias e importes.
 *
 * @param actorId   usuario que origina la operación, o {@code null} para un proceso del sistema
 * @param actorIp   IP de la terminal del actor, o {@code null}
 * @param userAgent agente de usuario del actor, o {@code null}
 */
public record AuditEntry(String module, String entityName, String entityId, String action, Severity severity,
                         UUID actorId, InetAddress actorIp, String userAgent, Map<String, Object> details) {

    public AuditEntry {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(severity, "severity");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** Valores de {@code ck_audit_trail_severity}. */
    public enum Severity {
        INFO, WARNING, HIGH, CRITICAL
    }
}
