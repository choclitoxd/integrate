package com.integrate.modules.catalog.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.catalog.dto.request.AssociationRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administración del catálogo (RF-03 mínimo, RF-07, RF-08; ADR-0006 §3-§4). Solo {@code ROLE_ADMIN}. */
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

    @PostMapping("/experiences/{id}/approve")
    public HostExperienceResponse approve(@PathVariable UUID id) {
        return moderationService.approve(id);
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
}
