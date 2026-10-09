package com.integrate.modules.catalog.service.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Franja y experiencia descritas para el turista (ADR-0010 §8).
 *
 * @param timezone zona en la que se muestran las horas ({@code America/Bogota}); define también la fecha válida
 *                 del voucher
 */
public record ScheduleSummary(UUID scheduleId, UUID experienceId, String title, String meetingPoint,
                              String municipality, String department, Instant startTime, Instant endTime,
                              String timezone) {
}
