package com.integrate.modules.offline.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.offline.dto.response.IncidentResponse;
import com.integrate.modules.offline.dto.response.IncidentResponse.Severity;
import com.integrate.modules.offline.dto.response.IncidentResponse.Status;
import com.integrate.modules.offline.dto.response.IncidentResponse.Type;
import com.integrate.modules.offline.service.AdminIncidentService;
import com.integrate.modules.offline.service.AdminIncidentService.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/incidents")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminIncidentController {

    private final AdminIncidentService service;

    public record Resolution(@NotBlank @Size(max = 2000) String resolutionNotes) {
    }

    @GetMapping
    public ResponseEntity<PageResponse<IncidentResponse>> list(@RequestParam(required = false) Status status,
            @RequestParam(required = false) Severity severity, @RequestParam(required = false) Type type,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return noStore(service.list(status, severity, type, PageRequests.newestFirst(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> detail(@PathVariable UUID id) {
        return noStore(service.detail(id));
    }

    @PostMapping("/{id}/take")
    public ResponseEntity<IncidentResponse> take(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return noStore(service.transition(id, Action.TAKEN, UUID.fromString(jwt.getSubject()), null));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<IncidentResponse> resolve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Resolution body) {
        return noStore(service.transition(id, Action.RESOLVED, UUID.fromString(jwt.getSubject()),
                body.resolutionNotes().strip()));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<IncidentResponse> dismiss(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Resolution body) {
        return noStore(service.transition(id, Action.DISMISSED, UUID.fromString(jwt.getSubject()),
                body.resolutionNotes().strip()));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
