package com.integrate.modules.catalog.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.catalog.dto.response.CategoryResponse;
import com.integrate.modules.catalog.dto.response.ExperienceAvailabilityResponse;
import com.integrate.modules.catalog.dto.response.ExperienceDetailResponse;
import com.integrate.modules.catalog.dto.response.ExperienceSummaryResponse;
import com.integrate.modules.catalog.dto.response.PlaceResponse;
import com.integrate.modules.catalog.entity.PhysicalIntensity;
import com.integrate.modules.catalog.service.CatalogQueryService;
import com.integrate.modules.catalog.service.CatalogQueryService.SearchCriteria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta pública del catálogo (RF-04, ADR-0006 §6). Sin JWT; un JWT válido solo cambia el 404/409 de availability. */
@RestController
@RequiredArgsConstructor
public class PublicCatalogController {

    /** Roles de panel que pueden saber que una experiencia existe aunque no esté publicada (contrato §4). */
    private static final Set<String> PANEL_ROLES = Set.of("ROLE_ADMIN", "ROLE_LINK_MANAGER");

    private final CatalogQueryService queryService;

    @GetMapping("/api/v1/experiences")
    public PageResponse<ExperienceSummaryResponse> search(
            @RequestParam(required = false) @Pattern(regexp = "^[0-9]{2}$") String departmentCode,
            @RequestParam(required = false) @Pattern(regexp = "^[0-9]{5}$") String municipalityCode,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{3,40}$") String category,
            @RequestParam(required = false) PhysicalIntensity intensity,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) @Min(1) @Max(30) Integer minSlots,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return queryService.search(
                new SearchCriteria(departmentCode, municipalityCode, category, intensity, q, dateFrom, dateTo, minSlots),
                PageRequests.newestFirst(page, size));
    }

    @GetMapping("/api/v1/experiences/{id}")
    public ExperienceDetailResponse detail(@PathVariable UUID id) {
        return queryService.detail(id);
    }

    @GetMapping("/api/v1/experiences/{id}/availability")
    public ExperienceAvailabilityResponse availability(@PathVariable UUID id, @RequestParam LocalDate dateFrom,
                                                       @RequestParam LocalDate dateTo,
                                                       @RequestParam(required = false) @Min(1) @Max(30) Integer minSlots,
                                                       Authentication authentication) {
        return queryService.availability(id, dateFrom, dateTo, minSlots, isPanelViewer(authentication));
    }

    @GetMapping("/api/v1/catalog/categories")
    public List<CategoryResponse> categories() {
        return queryService.categories();
    }

    @GetMapping("/api/v1/catalog/departments")
    public List<PlaceResponse> departments() {
        return queryService.departments();
    }

    @GetMapping("/api/v1/catalog/departments/{code}/municipalities")
    public List<PlaceResponse> municipalities(@PathVariable @Pattern(regexp = "^[0-9]{2}$") String code) {
        return queryService.municipalities(code);
    }

    private static boolean isPanelViewer(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(PANEL_ROLES::contains);
    }
}
