package com.integrate.modules.catalog.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.common.web.PageRequests;
import com.integrate.modules.catalog.dto.request.ExperienceRequest;
import com.integrate.modules.catalog.dto.request.ReasonRequest;
import com.integrate.modules.catalog.dto.request.ScheduleBatchRequest;
import com.integrate.modules.catalog.dto.response.HostExperienceResponse;
import com.integrate.modules.catalog.dto.response.ScheduleResponse;
import com.integrate.modules.catalog.service.HostExperienceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
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

/** Panel del anfitrión (RF-05, ADR-0006 §4-§5). El titular es siempre el {@code sub} del JWT. */
@RestController
@RequestMapping("/api/v1/host")
@PreAuthorize("hasRole('HOST')")
@RequiredArgsConstructor
public class HostExperienceController {

    private final HostExperienceService service;

    @PostMapping("/experiences")
    public ResponseEntity<HostExperienceResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody ExperienceRequest request) {
        HostExperienceResponse created = service.create(subject(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/host/experiences/" + created.id())).body(created);
    }

    @GetMapping("/experiences")
    public PageResponse<HostExperienceResponse> listMine(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(required = false) @Min(0) Integer page,
                                                         @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
        return service.listMine(subject(jwt), PageRequests.newestFirst(page, size));
    }

    @GetMapping("/experiences/{id}")
    public HostExperienceResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.getMine(subject(jwt), id);
    }

    @PutMapping("/experiences/{id}")
    public HostExperienceResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody ExperienceRequest request) {
        return service.update(subject(jwt), id, request);
    }

    @PostMapping("/experiences/{id}/submit")
    public HostExperienceResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.submit(subject(jwt), id);
    }

    @PostMapping("/experiences/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ScheduleResponse> createSchedules(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                  @Valid @RequestBody ScheduleBatchRequest request) {
        return service.createSchedules(subject(jwt), id, request);
    }

    @GetMapping("/experiences/{id}/schedules")
    public List<ScheduleResponse> listSchedules(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return service.listSchedules(subject(jwt), id, dateFrom, dateTo);
    }

    @PostMapping("/schedules/{id}/cancel")
    public ScheduleResponse cancelSchedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @Valid @RequestBody ReasonRequest request) {
        return service.cancelSchedule(subject(jwt), id, request.reason());
    }

    private static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
