package com.integrate.modules.offline.service.model;

import java.util.Set;

/**
 * Resultado de la tabla de decisión (ADR-0033 §3). {@code incidentType} e {@code incidentSeverity} son nulos si el
 * ítem no abre incidente. El catálogo de {@link Effect} es cerrado a propósito: no existe un efecto de reembolso ni
 * de reversión de la dispersión, de modo que ninguna decisión puede debitar el 85 % de la comunidad.
 */
public record SyncDecision(String serverOutcome, String status, String incidentType, String incidentSeverity,
                           Set<Effect> effects, String message) {

    public SyncDecision {
        effects = Set.copyOf(effects);
    }

    public enum Effect {
        /** Guardar la validación en {@code offline.offline_validations}. */
        PERSIST_EVIDENCE,
        /** Voucher a {@code CONSUMED} y reserva a {@code EXECUTED}. */
        REDEEM,
        /** Reserva cancelada a {@code SYNC_CONFLICT}. */
        MARK_SYNC_CONFLICT,
        /** Dispersión comunitaria {@code CANCELLED → RELEASED}. */
        PROTECT_COMMUNITY_SHARE
    }
}
