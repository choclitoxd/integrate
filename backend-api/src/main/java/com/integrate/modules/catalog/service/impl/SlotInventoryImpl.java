package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.service.SlotInventory;
import com.integrate.modules.catalog.service.model.ReservedSlots;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlotInventoryImpl implements SlotInventory {

    private final ScheduleRepository scheduleRepository;
    private final ExperienceRepository experienceRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> experienceOf(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).map(Schedule::getExperienceId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReservedSlots reserve(UUID scheduleId, int slots, Instant now) {
        // Solo se bloquea la franja: la experiencia se lee sin bloqueo para no frenar su moderación.
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> CatalogErrors.notFound("Schedule"));
        Experience experience = experienceRepository.findById(schedule.getExperienceId())
                .filter(Experience::isPublic)
                .orElseThrow(() -> CatalogErrors.notFound("Schedule"));
        if (schedule.getStatus() != ScheduleStatus.OPEN || !schedule.getStartTime().isAfter(now)) {
            throw CatalogErrors.scheduleNotBookable(schedule.getStatus(), schedule.getStartTime());
        }
        if (slots > schedule.getAvailableSlots()
                || scheduleRepository.decrementAvailableSlots(scheduleId, slots) != 1) {
            throw CatalogErrors.insufficientSlots(slots, schedule.getAvailableSlots());
        }
        return new ReservedSlots(scheduleId, experience.getId(), experience.getUnitPriceCop(),
                schedule.getStartTime(), schedule.getAvailableSlots() - slots);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Map<UUID, Integer> slotsBySchedule) {
        new TreeMap<>(slotsBySchedule).forEach((scheduleId, slots) -> {
            if (slots > 0 && scheduleRepository.incrementAvailableSlots(scheduleId, slots) != 1) {
                throw new IllegalStateException("Schedule " + scheduleId + " not found while releasing slots");
            }
        });
    }
}
