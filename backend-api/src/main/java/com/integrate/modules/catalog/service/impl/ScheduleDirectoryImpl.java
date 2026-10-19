package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.entity.Department;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.Municipality;
import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.repository.DepartmentRepository;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleDirectoryImpl implements ScheduleDirectory {

    private final ScheduleRepository scheduleRepository;
    private final ExperienceRepository experienceRepository;
    private final MunicipalityRepository municipalityRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ScheduleSummary> describeAll(Collection<UUID> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return Map.of();
        }
        List<Schedule> schedules = scheduleRepository.findAllById(Set.copyOf(scheduleIds));
        Map<UUID, Experience> experiences = experienceRepository
                .findAllById(schedules.stream().map(Schedule::getExperienceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Experience::getId, Function.identity()));
        Map<String, Municipality> municipalities = municipalityRepository
                .findAllById(experiences.values().stream().map(Experience::getMunicipalityCode).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Municipality::getCode, Function.identity()));
        Map<String, String> departments = departmentRepository
                .findAllById(municipalities.values().stream().map(Municipality::getDepartmentCode).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Department::getCode, Department::getName));
        Map<UUID, ScheduleSummary> result = new HashMap<>();
        for (Schedule schedule : schedules) {
            Experience experience = experiences.get(schedule.getExperienceId());
            if (experience == null) {
                throw new IllegalStateException("Schedule " + schedule.getId() + " has no experience");
            }
            Municipality municipality = municipalities.get(experience.getMunicipalityCode());
            result.put(schedule.getId(), new ScheduleSummary(schedule.getId(), experience.getId(), experience.getTitle(),
                    experience.getMeetingPoint(), municipality == null ? null : municipality.getName(),
                    municipality == null ? null : departments.get(municipality.getDepartmentCode()),
                    schedule.getStartTime(), schedule.getEndTime(), experience.getTimezone()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduleSummary> describe(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).map(schedule -> {
            Experience experience = experienceRepository.findById(schedule.getExperienceId())
                    .orElseThrow(() -> new IllegalStateException("Schedule " + scheduleId + " has no experience"));
            Optional<Municipality> municipality = municipalityRepository.findById(experience.getMunicipalityCode());
            String department = municipality.flatMap(m -> departmentRepository.findById(m.getDepartmentCode()))
                    .map(Department::getName).orElse(null);
            return new ScheduleSummary(scheduleId, experience.getId(), experience.getTitle(),
                    experience.getMeetingPoint(), municipality.map(Municipality::getName).orElse(null), department,
                    schedule.getStartTime(), schedule.getEndTime(), experience.getTimezone());
        });
    }
}
