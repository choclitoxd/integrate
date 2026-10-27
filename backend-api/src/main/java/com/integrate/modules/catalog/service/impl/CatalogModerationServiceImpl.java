package com.integrate.modules.catalog.service.impl;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.persistence.StatusReasonContext;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.service.CatalogModerationService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogModerationServiceImpl implements CatalogModerationService {

    private final ExperienceRepository experienceRepository;
    private final ScheduleRepository scheduleRepository;
    private final StatusReasonContext statusReason;
    private final ExperienceViews views;
    private final MeetingPointCheckService meetingPointChecks;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HostExperienceResponse> list(ExperienceStatus status, Pageable pageable) {
        Page<Experience> page = status == null
                ? experienceRepository.findByDeletedAtIsNull(pageable)
                : experienceRepository.findByStatusAndDeletedAtIsNull(status, pageable);
        return PageResponse.from(page, views.forHost(page.getContent()));
    }

    @Override
    @Transactional
    public HostExperienceResponse approve(UUID experienceId) {
        Experience experience = transition(experienceId, ExperienceStatus.APPROVED, "approve", "Approved by admin");
        return views.forHost(experience);
    }

    @Override
    @Transactional
    public HostExperienceResponse confirmMeetingPoint(UUID experienceId) {
        Experience experience = lock(experienceId);
        meetingPointChecks.confirm(experience);
        experienceRepository.flush();
        return views.forHost(experience);
    }

    @Override
    @Transactional
    public HostExperienceResponse reject(UUID experienceId, String reason) {
        Experience experience = lock(experienceId);
        requireTransition(experience, ExperienceStatus.REJECTED, "reject");
        // El trigger toma el motivo de integrate.status_reason; la columna guarda el último rechazo (RF-07).
        statusReason.set(reason.strip());
        experience.reject(reason.strip());
        experienceRepository.flush();
        return views.forHost(experience);
    }

    @Override
    @Transactional
    public HostExperienceResponse suspend(UUID experienceId, String reason) {
        Experience experience = transition(experienceId, ExperienceStatus.SUSPENDED, "suspend", reason.strip());
        int closed = scheduleRepository.moveFutureSchedules(experienceId, ScheduleStatus.OPEN, ScheduleStatus.CLOSED,
                clock.instant());
        log.info("Experience {} suspended: {} future schedules closed", experienceId, closed);
        return views.forHost(reload(experienceId));
    }

    @Override
    @Transactional
    public HostExperienceResponse reinstate(UUID experienceId) {
        transition(experienceId, ExperienceStatus.APPROVED, "reinstate", "Reinstated by admin");
        int reopened = scheduleRepository.moveFutureSchedules(experienceId, ScheduleStatus.CLOSED, ScheduleStatus.OPEN,
                clock.instant());
        log.info("Experience {} reinstated: {} future schedules reopened", experienceId, reopened);
        return views.forHost(reload(experienceId));
    }

    @Override
    @Transactional
    public HostExperienceResponse retire(UUID experienceId, String reason) {
        transition(experienceId, ExperienceStatus.RETIRED, "retire", reason.strip());
        scheduleRepository.moveFutureSchedules(experienceId, ScheduleStatus.OPEN, ScheduleStatus.CLOSED,
                clock.instant());
        return views.forHost(reload(experienceId));
    }

    /** La reactivación solo procede desde {@code SUSPENDED}; la aprobación, solo desde {@code PENDING_REVIEW}. */
    private Experience transition(UUID experienceId, ExperienceStatus target, String action, String reason) {
        Experience experience = lock(experienceId);
        boolean expectedSource = switch (action) {
            case "approve" -> experience.getStatus() == ExperienceStatus.PENDING_REVIEW;
            case "reinstate" -> experience.getStatus() == ExperienceStatus.SUSPENDED;
            default -> true;
        };
        if (!expectedSource) {
            throw CatalogErrors.invalidTransition(experience.getStatus(), action);
        }
        requireTransition(experience, target, action);
        if (action.equals("approve")) {
            meetingPointChecks.requireApprovable(experience);
        }
        statusReason.set(reason);
        experience.transitionTo(target);
        experienceRepository.flush();
        return experience;
    }

    private Experience lock(UUID experienceId) {
        return experienceRepository.findActiveByIdForUpdate(experienceId)
                .orElseThrow(() -> CatalogErrors.notFound("Experience"));
    }

    private static void requireTransition(Experience experience, ExperienceStatus target, String action) {
        if (!experience.getStatus().canTransitionTo(target)) {
            throw CatalogErrors.invalidTransition(experience.getStatus(), action);
        }
    }

    /** {@code moveFutureSchedules} limpia el contexto de persistencia: se relee la experiencia. */
    private Experience reload(UUID experienceId) {
        return experienceRepository.findById(experienceId).orElseThrow(() -> CatalogErrors.notFound("Experience"));
    }
}
