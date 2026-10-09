package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.entity.Department;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.Municipality;
import com.integrate.modules.catalog.repository.DepartmentRepository;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.model.ScheduleSummary;
import java.util.Optional;
import java.util.UUID;
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
