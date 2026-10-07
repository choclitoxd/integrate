package com.integrate.modules.catalog.controller;

import com.integrate.modules.catalog.dto.request.AssistedHostRegistrationRequest;
import com.integrate.modules.catalog.dto.response.AssistedHostRegistrationResponse;
import com.integrate.modules.catalog.service.AssistedEnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/register-assisted} del contrato (ADR-0007 §1). Vive en {@code catalog} porque
 * orquesta perfiles y asignaciones; usa la interfaz pública de {@code auth} para la cuenta y el consentimiento.
 */
@RestController
@PreAuthorize("hasAnyRole('LINK_MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedRegistrationController {

    private final AssistedEnrollmentService enrollmentService;

    @PostMapping("/api/v1/auth/register-assisted")
    public ResponseEntity<AssistedHostRegistrationResponse> register(
            @AuthenticationPrincipal Jwt jwt, Authentication authentication,
            @Valid @RequestBody AssistedHostRegistrationRequest request, HttpServletRequest http) {
        AssistedHostRegistrationResponse created = enrollmentService.register(Actors.subject(jwt),
                Actors.isAdmin(authentication), request, RequestClients.from(http));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.userId())).body(created);
    }
}
