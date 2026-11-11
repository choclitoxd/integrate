package com.integrate.modules.catalog.controller;

import com.integrate.modules.catalog.dto.request.ReasonRequest;
import com.integrate.modules.catalog.dto.response.HostStatusResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.service.HostAdminService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Baja, suspensión y reactivación de anfitriones (ADR-0070); el alta sigue en {@code AdminCatalogController}. */
@RestController
@RequestMapping("/api/v1/admin/host-profiles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminHostController {

    private final HostAdminService service;

    @PostMapping("/{id}/suspend")
    public HostStatusResponse suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody ReasonRequest request) {
        return service.changeStatus(id, AccreditationStatus.SUSPENDED, request.reason(), Actors.subject(jwt));
    }

    @PostMapping("/{id}/reinstate")
    public HostStatusResponse reinstate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody ReasonRequest request) {
        return service.changeStatus(id, AccreditationStatus.ACTIVE, request.reason(), Actors.subject(jwt));
    }

    @PostMapping("/{id}/revoke")
    public HostStatusResponse revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody ReasonRequest request) {
        return service.changeStatus(id, AccreditationStatus.REVOKED, request.reason(), Actors.subject(jwt));
    }
}
