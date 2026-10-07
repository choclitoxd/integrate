package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Contrato {@code ExperienceAvailabilityResponse} ({@code GET /experiences/{id}/availability}). */
public record ExperienceAvailabilityResponse(UUID experienceId, String title, String timezone, String currency,
                                             long unitPriceCop, int maxCapacityPerSlot, LocalDate dateFrom,
                                             LocalDate dateTo, List<ScheduleAvailability> schedules) {

    /** Contrato {@code ScheduleAvailability}: solo franjas {@code OPEN}. */
    public record ScheduleAvailability(UUID scheduleId, LocalDate localDate, Instant startTime, Instant endTime,
                                       String localStartTime, String localEndTime, int totalSlots,
                                       int availableSlots, ScheduleStatus status) {

        public static ScheduleAvailability from(Schedule s, ZoneId zone) {
            return new ScheduleAvailability(s.getId(), s.getStartTime().atZone(zone).toLocalDate(), s.getStartTime(),
                    s.getEndTime(), ScheduleResponse.LOCAL.format(s.getStartTime().atZone(zone)),
                    ScheduleResponse.LOCAL.format(s.getEndTime().atZone(zone)), s.getTotalSlots(),
                    s.getAvailableSlots(), s.getStatus());
        }
    }
}
