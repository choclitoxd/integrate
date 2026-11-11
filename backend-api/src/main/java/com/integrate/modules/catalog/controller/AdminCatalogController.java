package com.integrate.modules.catalog.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.catalog.dto.request.AdminExperienceCreateRequest;
import com.integrate.modules.catalog.dto.request.AssociationRequest;
import com.integrate.modules.catalog.dto.request.ExperienceRequest;
import com.integrate.modules.catalog.dto.request.HostProfileRequest;
import com.integrate.modules.catalog.dto.request.ReasonRequest;
import com.integrate.modules.catalog.dto.response.AssociationResponse;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.dto.response.HostProfileResponse;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.service.CatalogAdminService;
import com.integrate.modules.catalog.service.CatalogModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administración del catálogo (RF-03 mínimo, RF-07, RF-08; ADR-0006 §3-§4, ADR-0047). Solo {@code ROLE_ADMIN}. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final CatalogAdminService adminService;
    private final CatalogModerationService moderationService;

    @PostMapping("/associations")
    public ResponseEntity<AssociationResponse> createAssociation(@Valid @RequestBody AssociationRequest request) {
        AssociationResponse created = adminService.createAssociation(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/associations/" + created.id())).body(created);
    }

    @PutMapping("/associations/{id}")
    public AssociationResponse updateAssociation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                 @Valid @RequestBody AssociationRequest request) {
        return adminService.updateAssociation(id, request, subject(jwt));
    }

    @PostMapping("/associations/{id}/suspend")
    public AssociationResponse suspendAssociation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return adminService.suspendAssociation(id, subject(jwt));
    }

    @PostMapping("/associations/{id}/activate")
    public AssociationResponse activateAssociation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return adminService.activateAssociation(id, subject(jwt));
    }

    @GetMapping("/associations")
    public PageResponse<AssociationResponse> listAssociations(
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return adminService.listAssociations(PageRequests.newestFirst(page, size));
    }

    @PostMapping("/host-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public HostProfileResponse createHostProfile(@Valid @RequestBody HostProfileRequest request) {
        return adminService.createHostProfile(request);
    }

    @GetMapping("/experiences")
    public PageResponse<HostExperienceResponse> listExperiences(
            @RequestParam(required = false) ExperienceStatus status,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return moderationService.list(status, PageRequests.newestFirst(page, size));
    }

    @PostMapping("/experiences")
    public ResponseEntity<HostExperienceResponse> createExperience(@AuthenticationPrincipal Jwt jwt,
                                                                   @Valid @RequestBody AdminExperienceCreateRequest request) {
        HostExperienceResponse created = moderationService.create(request.hostProfileId(), request.experience(),
                subject(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/experiences/" + created.id())).body(created);
    }

    @PutMapping("/experiences/{id}")
    public HostExperienceResponse updateExperience(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                   @Valid @RequestBody ExperienceRequest request) {
        return moderationService.update(id, request, subject(jwt));
    }

    @PostMapping("/experiences/{id}/approve")
    public HostExperienceResponse approve(@PathVariable UUID id) {
        return moderationService.approve(id);
    }

    /** Acepta un punto de encuentro dudoso (REVIEW o MISMATCH); queda en el historial (ADR-0030 §4). */
    @PostMapping("/experiences/{id}/meeting-point/confirm")
    public HostExperienceResponse confirmMeetingPoint(@PathVariable UUID id) {
        return moderationService.confirmMeetingPoint(id);
    }

    @PostMapping("/experiences/{id}/reject")
    public HostExperienceResponse reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return moderationService.reject(id, request.reason());
    }

    @PostMapping("/experiences/{id}/suspend")
    public HostExperienceResponse suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return moderationService.suspend(id, request.reason());
    }

    @PostMapping("/experiences/{id}/reinstate")
    public HostExperienceResponse reinstate(@PathVariable UUID id) {
        return moderationService.reinstate(id);
    }

    @PostMapping("/experiences/{id}/retire")
    public HostExperienceResponse retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return moderationService.retire(id, request.reason());
    }

    private static UUID subject(Jwt jwt) {
        return jwt == null || jwt.getSubject() == null ? null : UUID.fromString(jwt.getSubject());
    }
}
