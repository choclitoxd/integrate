package com.integrate.modules.booking.controller;

import com.integrate.modules.booking.service.HostMetricsService;
import com.integrate.modules.booking.service.HostMetricsService.Metrics;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('HOST')")
@RequiredArgsConstructor
public class HostMetricsController {
    private final HostMetricsService service;

    @GetMapping("/api/v1/host/metrics")
    public ResponseEntity<Metrics> get(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(UUID.fromString(jwt.getSubject()), month));
    }
}
