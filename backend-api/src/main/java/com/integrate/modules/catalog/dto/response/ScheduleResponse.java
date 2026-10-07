package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Franja vista por su anfitrión: cualquier estado. Las horas locales se formatean siempre con segundos
 * ({@code YYYY-MM-DDTHH:mm:ss}), como exige el patrón del contrato.
 */
public record ScheduleResponse(UUID id, LocalDate localDate, Instant startTime, Instant endTime, String localStartTime,
                               String localEndTime, int totalSlots, int availableSlots, ScheduleStatus status,
                               String cancellationReason) {

    public static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    public static ScheduleResponse from(Schedule s, ZoneId zone) {
        return new ScheduleResponse(s.getId(), s.getStartTime().atZone(zone).toLocalDate(), s.getStartTime(),
                s.getEndTime(), LOCAL.format(s.getStartTime().atZone(zone)), LOCAL.format(s.getEndTime().atZone(zone)),
                s.getTotalSlots(), s.getAvailableSlots(), s.getStatus(), s.getCancellationReason());
    }
}
