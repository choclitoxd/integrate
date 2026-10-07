package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.response.CategoryResponse;
import com.integrate.modules.catalog.dto.response.ExperienceAvailabilityResponse;
import com.integrate.modules.catalog.dto.response.ExperienceDetailResponse;
import com.integrate.modules.catalog.dto.response.ExperienceSummaryResponse;
import com.integrate.modules.catalog.dto.response.PlaceResponse;
import com.integrate.modules.catalog.entity.PhysicalIntensity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Consulta pública del catálogo (RF-04, ADR-0006 §6). Solo experiencias {@code APPROVED}; sin datos del anfitrión. */
public interface CatalogQueryService {

    PageResponse<ExperienceSummaryResponse> search(SearchCriteria criteria, Pageable pageable);

    /** @throws com.integrate.common.exception.BusinessRuleException 404 si no existe o no está {@code APPROVED} */
    ExperienceDetailResponse detail(UUID experienceId);

    /**
     * Contrato {@code getExperienceAvailability}.
     *
     * @param privilegedViewer el invocador tiene {@code ROLE_ADMIN} o {@code ROLE_LINK_MANAGER}: una experiencia
     *                         no aprobada responde 409 en lugar de 404
     */
    ExperienceAvailabilityResponse availability(UUID experienceId, LocalDate dateFrom, LocalDate dateTo,
                                                Integer minSlots, boolean privilegedViewer);

    List<CategoryResponse> categories();

    List<PlaceResponse> departments();

    List<PlaceResponse> municipalities(String departmentCode);

    /**
     * Filtros opcionales. Si llega alguna fecha o {@code minSlots}, solo se listan experiencias con una
     * franja reservable en el rango (por defecto, los próximos 60 días).
     */
    record SearchCriteria(String departmentCode, String municipalityCode, String category, PhysicalIntensity intensity,
                          String q, LocalDate dateFrom, LocalDate dateTo, Integer minSlots) {

        public boolean filtersByAvailability() {
            return dateFrom != null || dateTo != null || minSlots != null;
        }
    }
}
