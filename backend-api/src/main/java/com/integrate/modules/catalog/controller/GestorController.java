package com.integrate.modules.catalog.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.catalog.dto.request.DocumentUploadRequest;
import com.integrate.modules.catalog.dto.response.ActivationCodeResponse;
import com.integrate.modules.catalog.dto.response.DocumentUploadResponse;
import com.integrate.modules.catalog.dto.response.ManagedHostResponse;
import com.integrate.modules.catalog.service.AssistedEnrollmentService;
import com.integrate.modules.catalog.service.GestorHostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones del Gestor de Enlace (ADR-0007 §1). La acreditación {@code ACTIVE} se exige en el servicio:
 * el rol del JWT no basta.
 */
@RestController
@RequestMapping("/api/v1/gestor")
@RequiredArgsConstructor
public class GestorController {

    private final AssistedEnrollmentService enrollmentService;
    private final GestorHostService hostService;

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LINK_MANAGER', 'ADMIN')")
    public DocumentUploadResponse presignUpload(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
                                                @Valid @RequestBody DocumentUploadRequest request) {
        return enrollmentService.presignUpload(Actors.subject(jwt), Actors.isAdmin(authentication), request.kind());
    }

    @GetMapping("/hosts")
    @PreAuthorize("hasRole('LINK_MANAGER')")
    public PageResponse<ManagedHostResponse> listHosts(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam(required = false) @Min(0) Integer page,
                                                       @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return hostService.listHosts(Actors.subject(jwt), PageRequests.newestFirst(page, size));
    }

    /** El código solo existe en esta respuesta: no se guarda en cachés intermedias. */
    @PostMapping("/hosts/{hostProfileId}/activation-code")
    @PreAuthorize("hasRole('LINK_MANAGER')")
    public ResponseEntity<ActivationCodeResponse> issueActivationCode(@AuthenticationPrincipal Jwt jwt,
                                                                      @PathVariable UUID hostProfileId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(hostService.issueActivationCode(Actors.subject(jwt), hostProfileId));
    }
}
