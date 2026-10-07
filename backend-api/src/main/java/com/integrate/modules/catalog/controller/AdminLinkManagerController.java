package com.integrate.modules.catalog.controller;

import com.integrate.modules.catalog.dto.request.LinkManagerRequest;
import com.integrate.modules.catalog.dto.response.LinkManagerResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.service.LinkManagerAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Acreditación de gestores de enlace (RF-03, ADR-0007 §2). Solo {@code ROLE_ADMIN}. */
@RestController
@RequestMapping("/api/v1/admin/link-managers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminLinkManagerController {

    private final LinkManagerAdminService service;

    @PostMapping
    public ResponseEntity<LinkManagerResponse> accredit(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody LinkManagerRequest request) {
        LinkManagerResponse created = service.accredit(request, Actors.subject(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/link-managers/" + created.linkManagerId()))
                .body(created);
    }

    @PostMapping("/{id}/suspend")
    public LinkManagerResponse suspend(@PathVariable UUID id) {
        return service.changeStatus(id, AccreditationStatus.SUSPENDED);
    }

    @PostMapping("/{id}/reinstate")
    public LinkManagerResponse reinstate(@PathVariable UUID id) {
        return service.changeStatus(id, AccreditationStatus.ACTIVE);
    }

    @PostMapping("/{id}/revoke")
    public LinkManagerResponse revoke(@PathVariable UUID id) {
        return service.changeStatus(id, AccreditationStatus.REVOKED);
    }
}
