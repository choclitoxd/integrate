package com.integrate.modules.catalog.controller;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.catalog.dto.request.AdminUserCreateRequest;
import com.integrate.modules.catalog.dto.request.AdminUserUpdateRequest;
import com.integrate.modules.catalog.dto.response.AdminHostResponse;
import com.integrate.modules.catalog.dto.response.AdminLinkManagerResponse;
import com.integrate.modules.catalog.dto.response.AdminUserDetailResponse;
import com.integrate.modules.catalog.dto.response.AdminUserResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.service.AdminPeopleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de personas para la consola de administración (ADR-0043 §1): búsqueda exacta, detalle y los listados de
 * gestores y de anfitriones. Solo {@code ROLE_ADMIN}. Las respuestas llevan datos personales: no se almacenan en
 * cachés.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPeopleController {

    private final AdminPeopleService service;

    /** Exactamente uno de {@code email} o {@code document} (este con su {@code docType}); si no, 400. */
    @GetMapping("/users/search")
    public ResponseEntity<PageResponse<AdminUserResponse>> search(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(required = false) @Size(max = 254)
            @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String email,
            @RequestParam(required = false) DocType docType,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z0-9]{4,20}$") String document) {
        boolean byEmail = email != null;
        boolean byDocument = document != null || docType != null;
        if (byEmail == byDocument || (byDocument && (document == null || docType == null))) {
            throw new BusinessRuleException(ErrorCode.MALFORMED_REQUEST,
                    "Provide either email, or document together with docType.");
        }
        UUID adminId = Actors.subject(jwt);
        return noStore(byEmail
                ? service.searchByEmail(email, adminId, RequestClients.from(request))
                : service.searchByDocument(docType, document, adminId, RequestClients.from(request)));
    }

    /**
     * Cuentas abiertas paginadas, opcionalmente por rol vigente (ADR-0048 §1). Sin {@code role} lista todas.
     */
    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> listUsers(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return noStore(service.listUsers(role, PageRequests.newestFirst(page, size), Actors.subject(jwt),
                RequestClients.from(request)));
    }

    /** Alta de una cuenta de turista por el administrador (ADR-0048 §2). */
    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody AdminUserCreateRequest request) {
        AdminUserResponse created = service.createUser(request, Actors.subject(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.userId()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetailResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                          HttpServletRequest request, @PathVariable UUID id) {
        return noStore(service.detail(id, Actors.subject(jwt), RequestClients.from(request)));
    }

    /** Actualización de los datos de una persona por el administrador (ADR-0057 §1). */
    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID id,
                                                        @Valid @RequestBody AdminUserUpdateRequest request) {
        return noStore(service.updateUser(id, request, Actors.subject(jwt)));
    }

    @GetMapping("/link-managers")
    public ResponseEntity<PageResponse<AdminLinkManagerResponse>> listLinkManagers(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(required = false) AccreditationStatus status,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return noStore(service.listLinkManagers(status, PageRequests.newestFirst(page, size),
                Actors.subject(jwt), RequestClients.from(request)));
    }

    @GetMapping("/host-profiles")
    public ResponseEntity<PageResponse<AdminHostResponse>> listHosts(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(required = false) UUID associationId,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return noStore(service.listHosts(associationId, PageRequests.newestFirst(page, size),
                Actors.subject(jwt), RequestClients.from(request)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
