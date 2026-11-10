package com.integrate.modules.catalog.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.payload.InvalidParam;
import com.integrate.common.payload.PageResponse;
import com.integrate.common.persistence.StatusReasonContext;
import com.integrate.modules.catalog.dto.request.ExperienceRequest;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.entity.Category;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceCategory;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.entity.ExperienceStop;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import com.integrate.modules.catalog.repository.CategoryRepository;
import com.integrate.modules.catalog.repository.ExperienceCategoryRepository;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.ExperienceStopRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.service.CatalogModerationService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ExperienceCategoryRepository experienceCategoryRepository;
    private final ExperienceStopRepository experienceStopRepository;
    private final CategoryRepository categoryRepository;
    private final HostProfileRepository hostProfileRepository;
    private final MunicipalityRepository municipalityRepository;
    private final ScheduleRepository scheduleRepository;
    private final StatusReasonContext statusReason;
    private final ExperienceViews views;
    private final MeetingPointCheckService meetingPointChecks;
    private final AuditTrail auditTrail;
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
    public HostExperienceResponse create(UUID hostProfileId, ExperienceRequest request, UUID adminUserId) {
        HostProfile host = hostProfileRepository.findById(hostProfileId)
                .filter(h -> h.getDeletedAt() == null)
                .orElseThrow(() -> CatalogErrors.notFound("Host profile"));
        List<Category> categories = validate(request);
        Experience experience = experienceRepository.saveAndFlush(Experience.draft(host.getId(), request.toContent()));
        UUID experienceId = experience.getId();
        replaceCategories(experienceId, categories);
        replaceStops(experienceId, request.stopsOrEmpty());
        // El administrador la deja lista para decidir: pasa a revisión y se verifica el punto de encuentro.
        statusReason.set("Submitted for review by admin");
        experience.transitionTo(ExperienceStatus.PENDING_REVIEW);
        experienceRepository.flush();
        meetingPointChecks.onSubmit(experience);
        HostExperienceResponse response = views.forHost(experience);
        auditTrail.record(new AuditEntry("catalog", "experiences", experienceId.toString(),
                "EXPERIENCE_CREATED_BY_ADMIN", Severity.INFO, adminUserId, null, null,
                Map.of("title", experience.getTitle(), "hostProfileId", host.getId().toString())));
        return response;
    }

    @Override
    @Transactional
    public HostExperienceResponse update(UUID experienceId, ExperienceRequest request, UUID adminUserId) {
        Experience experience = lock(experienceId);
        if (experience.getStatus() == ExperienceStatus.RETIRED) {
            throw CatalogErrors.invalidTransition(experience.getStatus(), "update");
        }
        List<Category> categories = validate(request);
        short maxOpen = scheduleRepository.findMaxOpenFutureSlots(experienceId, clock.instant());
        if (request.maxCapacityPerSlot() < maxOpen) {
            throw CatalogErrors.invalidField("maxCapacityPerSlot",
                    "future open schedules already offer " + maxOpen + " slots");
        }
        boolean pointChanged = experience.editByAdmin(request.toContent());
        experienceRepository.flush();
        meetingPointChecks.onEdit(experience, pointChanged);
        replaceCategories(experienceId, categories);
        replaceStops(experienceId, request.stopsOrEmpty());
        HostExperienceResponse response = views.forHost(experience);
        auditTrail.record(new AuditEntry("catalog", "experiences", experienceId.toString(),
                "EXPERIENCE_UPDATED_BY_ADMIN", Severity.INFO, adminUserId, null, null,
                Map.of("title", experience.getTitle(), "status", experience.getStatus().name())));
        return response;
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

    private List<Category> validate(ExperienceRequest request) {
        List<InvalidParam> invalid = new ArrayList<>();
        if (!municipalityRepository.existsById(request.municipalityCode())) {
            invalid.add(new InvalidParam("municipalityCode", "unknown DIVIPOLA municipality code"));
        }
        if ((request.latitude() == null) != (request.longitude() == null)) {
            invalid.add(new InvalidParam("latitude", "latitude and longitude must be given together"));
        }
        Set<String> codes = request.categoryCodesOrEmpty();
        List<Category> categories = codes.isEmpty() ? List.of() : categoryRepository.findByCodeInAndDeletedAtIsNull(codes);
        if (categories.size() != codes.size()) {
            invalid.add(new InvalidParam("categoryCodes", "contains unknown category codes"));
        }
        if (!invalid.isEmpty()) {
            throw CatalogErrors.invalid(invalid);
        }
        return categories;
    }

    private void replaceStops(UUID experienceId, List<ExperienceRequest.Stop> stops) {
        experienceStopRepository.deleteByExperienceId(experienceId);
        List<ExperienceStop> rows = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            ExperienceRequest.Stop stop = stops.get(i);
            String description = stop.description() == null || stop.description().isBlank()
                    ? null : stop.description().strip();
            rows.add(new ExperienceStop(experienceId, (short) (i + 1), stop.name().strip(), description));
        }
        experienceStopRepository.saveAll(rows);
    }

    private void replaceCategories(UUID experienceId, List<Category> categories) {
        experienceCategoryRepository.deleteByExperienceId(experienceId);
        experienceCategoryRepository.saveAll(categories.stream()
                .map(c -> new ExperienceCategory(experienceId, c.getId()))
                .toList());
    }
}
