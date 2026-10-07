package com.integrate.modules.catalog.service.impl;

import com.integrate.common.payload.InvalidParam;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.request.ExperienceRequest;
import com.integrate.modules.catalog.dto.request.ScheduleBatchRequest;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.dto.response.ScheduleResponse;
import com.integrate.modules.catalog.entity.Category;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceCategory;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import com.integrate.modules.catalog.repository.CategoryRepository;
import com.integrate.modules.catalog.repository.ExperienceCategoryRepository;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.repository.StatusReasonContext;
import com.integrate.modules.catalog.service.HostExperienceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HostExperienceServiceImpl implements HostExperienceService {

    private static final ZoneId ZONE = ZoneId.of(Experience.TIMEZONE);
    /** Estados en que el anfitrión puede programar franjas (ADR-0006 §5). */
    private static final Set<ExperienceStatus> SCHEDULABLE = EnumSet.of(ExperienceStatus.DRAFT,
            ExperienceStatus.PENDING_REVIEW, ExperienceStatus.REJECTED, ExperienceStatus.APPROVED);

    private final HostProfileRepository hostProfileRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceCategoryRepository experienceCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final ScheduleRepository scheduleRepository;
    private final StatusReasonContext statusReason;
    private final ExperienceViews views;
    private final Clock clock;

    @Override
    @Transactional
    public HostExperienceResponse create(UUID hostUserId, ExperienceRequest request) {
        HostProfile host = requireHost(hostUserId);
        List<Category> categories = validate(request);
        Experience experience = experienceRepository.saveAndFlush(Experience.draft(host.getId(), request.toContent()));
        replaceCategories(experience.getId(), categories);
        return views.forHost(experience);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HostExperienceResponse> listMine(UUID hostUserId, Pageable pageable) {
        HostProfile host = requireHost(hostUserId);
        Page<Experience> page = experienceRepository.findByHostIdAndDeletedAtIsNull(host.getId(), pageable);
        return PageResponse.from(page, views.forHost(page.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public HostExperienceResponse getMine(UUID hostUserId, UUID experienceId) {
        return views.forHost(ownedExperience(requireHost(hostUserId), experienceId, false));
    }

    @Override
    @Transactional
    public HostExperienceResponse update(UUID hostUserId, UUID experienceId, ExperienceRequest request) {
        Experience experience = ownedExperience(requireHost(hostUserId), experienceId, true);
        if (!experience.getStatus().isEditableByHost()) {
            throw CatalogErrors.invalidTransition(experience.getStatus(), "edit");
        }
        List<Category> categories = validate(request);
        // Reducir el aforo por debajo de franjas futuras ya publicadas lo rechazaría el trigger: se anticipa con 422.
        short maxOpen = scheduleRepository.findMaxOpenFutureSlots(experienceId, clock.instant());
        if (request.maxCapacityPerSlot() < maxOpen) {
            throw CatalogErrors.invalidField("maxCapacityPerSlot",
                    "future open schedules already offer " + maxOpen + " slots");
        }
        boolean wasRejected = experience.getStatus() == ExperienceStatus.REJECTED;
        if (wasRejected) {
            statusReason.set("Edited by host after rejection");
        }
        experience.edit(request.toContent());
        experienceRepository.flush();
        replaceCategories(experienceId, categories);
        return views.forHost(experience);
    }

    @Override
    @Transactional
    public HostExperienceResponse submit(UUID hostUserId, UUID experienceId) {
        Experience experience = ownedExperience(requireHost(hostUserId), experienceId, true);
        if (!experience.getStatus().canTransitionTo(ExperienceStatus.PENDING_REVIEW)) {
            throw CatalogErrors.invalidTransition(experience.getStatus(), "submit");
        }
        statusReason.set("Submitted for review by host");
        experience.transitionTo(ExperienceStatus.PENDING_REVIEW);
        experienceRepository.flush();
        return views.forHost(experience);
    }

    @Override
    @Transactional
    public List<ScheduleResponse> createSchedules(UUID hostUserId, UUID experienceId, ScheduleBatchRequest request) {
        Experience experience = ownedExperience(requireHost(hostUserId), experienceId, true);
        if (!SCHEDULABLE.contains(experience.getStatus())) {
            throw CatalogErrors.invalidTransition(experience.getStatus(), "add schedules to");
        }
        Instant now = clock.instant();
        List<InvalidParam> invalid = new ArrayList<>();
        List<Schedule> schedules = new ArrayList<>();
        Map<Instant, Integer> startsInBatch = new HashMap<>();
        List<InvalidParam> duplicates = new ArrayList<>();
        for (int i = 0; i < request.schedules().size(); i++) {
            ScheduleBatchRequest.Slot slot = request.schedules().get(i);
            String prefix = "schedules[" + i + "].";
            Instant start = slot.localStart().atZone(ZONE).toInstant();
            Instant end = slot.localEnd().atZone(ZONE).toInstant();
            if (!end.isAfter(start)) {
                invalid.add(new InvalidParam(prefix + "localEnd", "must be after localStart"));
            }
            if (!start.isAfter(now)) {
                invalid.add(new InvalidParam(prefix + "localStart", "must be in the future"));
            }
            if (slot.totalSlots() > experience.getMaxCapacityPerSlot()) {
                invalid.add(new InvalidParam(prefix + "totalSlots",
                        "must not exceed maxCapacityPerSlot " + experience.getMaxCapacityPerSlot()));
            }
            if (startsInBatch.putIfAbsent(start, i) != null) {
                duplicates.add(new InvalidParam(prefix + "localStart", "duplicated within the batch"));
            }
            schedules.add(Schedule.open(experienceId, start, end, slot.totalSlots()));
        }
        if (!invalid.isEmpty()) {
            throw CatalogErrors.invalid(invalid);
        }
        if (scheduleRepository.existsByExperienceIdAndStartTimeIn(experienceId, startsInBatch.keySet())) {
            startsInBatch.forEach((start, i) -> {
                if (scheduleRepository.existsByExperienceIdAndStartTimeIn(experienceId, Set.of(start))) {
                    duplicates.add(new InvalidParam("schedules[" + i + "].localStart",
                            "a schedule already starts at this time"));
                }
            });
        }
        if (!duplicates.isEmpty()) {
            throw CatalogErrors.scheduleConflict("Schedules overlap existing start times", duplicates);
        }
        try {
            return scheduleRepository.saveAllAndFlush(schedules).stream()
                    .map(s -> ScheduleResponse.from(s, ZONE))
                    .toList();
        } catch (DataIntegrityViolationException e) {
            // Carrera con otro lote concurrente: el índice único decide.
            throw CatalogErrors.scheduleConflict("Schedules overlap existing start times", List.of());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(UUID hostUserId, UUID experienceId, LocalDate dateFrom,
                                                LocalDate dateTo) {
        ownedExperience(requireHost(hostUserId), experienceId, false);
        if (dateTo.isBefore(dateFrom)) {
            throw CatalogErrors.invalidField("dateTo", "must be on or after dateFrom");
        }
        return scheduleRepository.findByExperienceIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        experienceId, dateFrom.atStartOfDay(ZONE).toInstant(),
                        dateTo.plusDays(1).atStartOfDay(ZONE).toInstant())
                .stream().map(s -> ScheduleResponse.from(s, ZONE)).toList();
    }

    @Override
    @Transactional
    public ScheduleResponse cancelSchedule(UUID hostUserId, UUID scheduleId, String reason) {
        HostProfile host = requireHost(hostUserId);
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> CatalogErrors.notFound("Schedule"));
        ownedExperience(host, schedule.getExperienceId(), true);
        if (schedule.getStatus() == ScheduleStatus.CANCELLED || !schedule.getStartTime().isAfter(clock.instant())) {
            throw CatalogErrors.scheduleConflict("Only future, non-cancelled schedules can be cancelled", List.of());
        }
        if (schedule.hasBookings()) {
            // El flujo de contingencia y reembolso llega en la Fase 3; mientras tanto el admin suspende la experiencia.
            throw CatalogErrors.scheduleConflict("The schedule has bookings and cannot be cancelled yet", List.of());
        }
        schedule.cancel(reason.strip());
        scheduleRepository.flush();
        return ScheduleResponse.from(schedule, ZONE);
    }

    // ---------------------------------------------------------------- helpers

    private HostProfile requireHost(UUID userId) {
        return hostProfileRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> CatalogErrors.forbidden("No active host profile for this account"));
    }

    /** Experiencia del anfitrión; una ajena responde 404 igual que una inexistente. */
    private Experience ownedExperience(HostProfile host, UUID experienceId, boolean forUpdate) {
        return (forUpdate ? experienceRepository.findActiveByIdForUpdate(experienceId)
                : experienceRepository.findById(experienceId).filter(e -> e.getDeletedAt() == null))
                .filter(e -> e.getHostId().equals(host.getId()))
                .orElseThrow(() -> CatalogErrors.notFound("Experience"));
    }

    /** Reglas que la validación de Bean Validation no cubre: referencias y coordenadas en pareja. */
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

    private void replaceCategories(UUID experienceId, List<Category> categories) {
        experienceCategoryRepository.deleteByExperienceId(experienceId);
        experienceCategoryRepository.saveAll(categories.stream()
                .map(c -> new ExperienceCategory(experienceId, c.getId()))
                .toList());
    }
}
