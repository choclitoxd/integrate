package com.integrate.modules.catalog.service;

import com.integrate.modules.catalog.service.model.ScheduleSummary;
import java.util.Optional;
import java.util.UUID;

/**
 * Datos descriptivos de una franja y su experiencia para documentos al turista, como el voucher (ADR-0010 §8).
 * Interfaz pública de {@code catalog}, de solo lectura y sin datos del anfitrión (R-05).
 */
public interface ScheduleDirectory {

    /** Vacío si la franja no existe. */
    Optional<ScheduleSummary> describe(UUID scheduleId);
}
