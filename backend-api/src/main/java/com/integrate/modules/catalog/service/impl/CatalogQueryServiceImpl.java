package com.integrate.modules.catalog.service.impl;

import static com.integrate.modules.catalog.repository.ExperienceSpecifications.inCategory;
import static com.integrate.modules.catalog.repository.ExperienceSpecifications.inDepartment;
import static com.integrate.modules.catalog.repository.ExperienceSpecifications.inMunicipality;
import static com.integrate.modules.catalog.repository.ExperienceSpecifications.publicCatalog;
import static com.integrate.modules.catalog.repository.ExperienceSpecifications.titleContains;
import static com.integrate.modules.catalog.repository.ExperienceSpecifications.withBookableSchedule;
import static com.integrate.modules.catalog.repository.ExperienceSpecifications.withIntensity;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.response.CategoryResponse;
import com.integrate.modules.catalog.dto.response.ExperienceAvailabilityResponse;
import com.integrate.modules.catalog.dto.response.ExperienceAvailabilityResponse.ScheduleAvailability;
import com.integrate.modules.catalog.dto.response.ExperienceDetailResponse;
import com.integrate.modules.catalog.dto.response.ExperienceSummaryResponse;
import com.integrate.modules.catalog.dto.response.PlaceResponse;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.repository.CategoryRepository;
import com.integrate.modules.catalog.repository.DepartmentRepository;
import com.integrate.modules.catalog.repository.ExperienceRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.repository.ScheduleRepository;
import com.integrate.modules.catalog.service.CatalogQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryServiceImpl implements CatalogQueryService {

    /** Rango máximo de consulta de disponibilidad (contrato §4). */
    static final int MAX_RANGE_DAYS = 60;
    private static final ZoneId ZONE = ZoneId.of(Experience.TIMEZONE);

    private final ExperienceRepository experienceRepository;
    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final MunicipalityRepository municipalityRepository;
    private final ExperienceViews views;
    private final Clock clock;

    @Override
    public PageResponse<ExperienceSummaryResponse> search(SearchCriteria c, Pageable pageable) {
        Specification<Experience> spec = publicCatalog();
        if (c.municipalityCode() != null) {
            spec = spec.and(inMunicipality(c.municipalityCode()));
        }
        if (c.departmentCode() != null) {
            spec = spec.and(inDepartment(c.departmentCode()));
        }
        if (c.category() != null) {
            spec = spec.and(inCategory(c.category()));
        }
        if (c.intensity() != null) {
            spec = spec.and(withIntensity(c.intensity()));
        }
        if (c.q() != null && !c.q().isBlank()) {
            spec = spec.and(titleContains(c.q()));
        }
        if (c.filtersByAvailability()) {
            LocalDate today = LocalDate.now(clock.withZone(ZONE));
            LocalDate from = c.dateFrom() != null ? c.dateFrom() : today;
            LocalDate to = c.dateTo() != null ? c.dateTo() : from.plusDays(MAX_RANGE_DAYS);
            requireRange(from, to);
            spec = spec.and(withBookableSchedule(clock.instant(), startOf(from), startOf(to.plusDays(1)),
                    c.minSlots() != null ? c.minSlots() : 1));
        }
        Page<Experience> page = experienceRepository.findAll(spec, pageable);
        return PageResponse.from(page, views.summaries(page.getContent()));
    }

    @Override
    public ExperienceDetailResponse detail(UUID experienceId) {
        Experience experience = experienceRepository.findById(experienceId)
                .filter(Experience::isPublic)
                .orElseThrow(() -> CatalogErrors.notFound("Experience"));
        return views.detail(experience);
    }

    @Override
    public ExperienceAvailabilityResponse availability(UUID experienceId, LocalDate dateFrom, LocalDate dateTo,
                                                       Integer minSlots, boolean privilegedViewer) {
        Experience experience = experienceRepository.findById(experienceId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> CatalogErrors.notFound("Experience"));
        if (experience.getStatus() != ExperienceStatus.APPROVED) {
            // Al público no se le revela que la experiencia existe; el panel sí necesita saber por qué no hay datos.
            throw privilegedViewer ? CatalogErrors.notApproved(experience.getStatus())
                    : CatalogErrors.notFound("Experience");
        }
        requireRange(dateFrom, dateTo);
        // Sin minSlots se incluyen las franjas agotadas: la PWA las muestra como "Agotado" (RF-04).
        short min = (short) (minSlots == null ? 0 : minSlots);
        List<ScheduleAvailability> schedules = scheduleRepository
                .findBookable(experienceId, clock.instant(), startOf(dateFrom), startOf(dateTo.plusDays(1)), min)
                .stream().map(s -> ScheduleAvailability.from(s, ZONE)).toList();
        return new ExperienceAvailabilityResponse(experience.getId(), experience.getTitle(), experience.getTimezone(),
                ExperienceSummaryResponse.CURRENCY, experience.unitPriceCopValue(), experience.getMaxCapacityPerSlot(),
                dateFrom, dateTo, schedules);
    }

    @Override
    public List<CategoryResponse> categories() {
        return categoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getCode(), c.getName(), c.getDescription()))
                .toList();
    }

    @Override
    public List<PlaceResponse> departments() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(d -> new PlaceResponse(d.getCode(), d.getName()))
                .toList();
    }

    @Override
    public List<PlaceResponse> municipalities(String departmentCode) {
        if (!departmentRepository.existsById(departmentCode)) {
            throw CatalogErrors.notFound("Department");
        }
        return municipalityRepository.findByDepartmentCodeOrderByNameAsc(departmentCode).stream()
                .map(m -> new PlaceResponse(m.getCode(), m.getName()))
                .toList();
    }

    /** {@code dateTo ≥ dateFrom} y a lo sumo 60 días entre ambas (contrato §4 → 422). */
    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw CatalogErrors.invalidField("dateTo", "must be on or after dateFrom");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw CatalogErrors.invalidField("dateTo", "range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
