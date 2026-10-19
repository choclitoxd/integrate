package com.integrate.modules.catalog.service;

import com.integrate.modules.catalog.service.model.ScheduleSummary;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Datos descriptivos de una franja y su experiencia para documentos al turista, como el voucher (ADR-0010 §8).
 * Interfaz pública de {@code catalog}, de solo lectura y sin datos del anfitrión (R-05).
 */
public interface ScheduleDirectory {

    /** Vacío si la franja no existe. */
    Optional<ScheduleSummary> describe(UUID scheduleId);

    /**
     * Varias franjas en un solo lote, para listados paginados (ADR-0023 §1): una consulta por tabla, sin N+1. Las
     * franjas inexistentes no aparecen en el resultado.
     */
    Map<UUID, ScheduleSummary> describeAll(Collection<UUID> scheduleIds);
}
