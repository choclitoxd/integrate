package com.integrate.modules.catalog.service.impl;

import com.integrate.modules.catalog.dto.response.ExperienceDetailResponse;
import com.integrate.modules.catalog.dto.response.ExperienceSummaryResponse;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.dto.response.PlaceResponse;
import com.integrate.modules.catalog.dto.response.StopResponse;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import com.integrate.modules.catalog.entity.Department;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceStop;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.entity.Municipality;
import com.integrate.modules.catalog.repository.CommunityAssociationRepository;
import com.integrate.modules.catalog.repository.DepartmentRepository;
import com.integrate.modules.catalog.repository.ExperienceCategoryRepository;
import com.integrate.modules.catalog.repository.ExperienceCategoryRepository.ExperienceCategoryCode;
import com.integrate.modules.catalog.repository.ExperienceStopRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Proyecciones de experiencias a DTOs, resueltas por lotes (una consulta por tabla y página, sin N+1).
 * Las vistas públicas se arman con la asociación y el municipio; el anfitrión nunca aparece (R-05).
 * Participa en la transacción del invocador.
 */
@Component
@RequiredArgsConstructor
class ExperienceViews {

    private final ExperienceCategoryRepository experienceCategoryRepository;
    private final ExperienceStopRepository experienceStopRepository;
    private final MunicipalityRepository municipalityRepository;
    private final DepartmentRepository departmentRepository;
    private final HostProfileRepository hostProfileRepository;
    private final CommunityAssociationRepository associationRepository;

    List<HostExperienceResponse> forHost(List<Experience> experiences) {
        Map<UUID, List<String>> categories = categoryCodes(experiences);
        Map<UUID, List<StopResponse>> stops = stopsOf(experiences);
        return experiences.stream()
                .map(e -> HostExperienceResponse.from(e, categories.getOrDefault(e.getId(), List.of()),
                        stops.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    HostExperienceResponse forHost(Experience experience) {
        return forHost(List.of(experience)).getFirst();
    }

    List<ExperienceSummaryResponse> summaries(List<Experience> experiences) {
        if (experiences.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<String>> categories = categoryCodes(experiences);
        Map<UUID, List<StopResponse>> stops = stopsOf(experiences);
        Map<String, Municipality> municipalities = byKey(municipalityRepository.findAllById(
                experiences.stream().map(Experience::getMunicipalityCode).distinct().toList()), Municipality::getCode);
        Map<String, Department> departments = byKey(departmentRepository.findAllById(
                municipalities.values().stream().map(Municipality::getDepartmentCode).distinct().toList()),
                Department::getCode);
        Map<UUID, HostProfile> hosts = byKey(hostProfileRepository.findAllById(
                experiences.stream().map(Experience::getHostId).distinct().toList()), HostProfile::getId);
        Map<UUID, CommunityAssociation> associations = byKey(associationRepository.findAllById(
                hosts.values().stream().map(HostProfile::getAssociationId).distinct().toList()),
                CommunityAssociation::getId);

        return experiences.stream().map(e -> {
            Municipality municipality = municipalities.get(e.getMunicipalityCode());
            Department department = departments.get(municipality.getDepartmentCode());
            CommunityAssociation association = associations.get(hosts.get(e.getHostId()).getAssociationId());
            return new ExperienceSummaryResponse(e.getId(), e.getTitle(), e.getPhysicalIntensity(),
                    e.getDurationMinutes(), e.getRouteType(), e.getDistanceKm(), e.getRoundTripMinutes(),
                    stops.getOrDefault(e.getId(), List.of()).size(), ExperienceSummaryResponse.CURRENCY,
                    e.unitPriceCopValue(),
                    e.getMaxCapacityPerSlot(), new PlaceResponse(municipality.getCode(), municipality.getName()),
                    new PlaceResponse(department.getCode(), department.getName()), association.getLegalName(),
                    categories.getOrDefault(e.getId(), List.of()));
        }).toList();
    }

    ExperienceDetailResponse detail(Experience e) {
        return new ExperienceDetailResponse(summaries(List.of(e)).getFirst(), e.getDescription(),
                e.getSafetyGuidelines(), e.getVereda(), e.getMeetingPoint(), e.getLatitude(), e.getLongitude(),
                e.getTimezone(), stopsOf(List.of(e)).getOrDefault(e.getId(), List.of()),
                e.getMeetingPointCheckStatus().coordinatesTrusted());
    }

    private Map<UUID, List<StopResponse>> stopsOf(Collection<Experience> experiences) {
        if (experiences.isEmpty()) {
            return Map.of();
        }
        return experienceStopRepository
                .findByExperienceIdInOrderByExperienceIdAscPositionAsc(
                        experiences.stream().map(Experience::getId).toList()).stream()
                .collect(Collectors.groupingBy(ExperienceStop::getExperienceId,
                        Collectors.mapping(StopResponse::from, Collectors.toList())));
    }

    private Map<UUID, List<String>> categoryCodes(Collection<Experience> experiences) {
        if (experiences.isEmpty()) {
            return Map.of();
        }
        return experienceCategoryRepository
                .findCodesByExperienceIds(experiences.stream().map(Experience::getId).toList()).stream()
                .collect(Collectors.groupingBy(ExperienceCategoryCode::getExperienceId,
                        Collectors.mapping(ExperienceCategoryCode::getCode, Collectors.toList())));
    }

    private static <K, V> Map<K, V> byKey(List<V> values, Function<V, K> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
