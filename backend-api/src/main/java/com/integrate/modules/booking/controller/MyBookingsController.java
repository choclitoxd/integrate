package com.integrate.modules.booking.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.booking.dto.response.MyBookingSummary;
import com.integrate.modules.booking.service.BookingHistoryService;
import com.integrate.modules.booking.service.BookingHistoryService.Scope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Mis experiencias" del turista (ADR-0023 §1). Siempre filtra por el {@code sub} del JWT. */
@RestController
@PreAuthorize("hasRole('TOURIST')")
@RequiredArgsConstructor
public class MyBookingsController {

    private final BookingHistoryService historyService;

    @GetMapping("/api/v1/me/bookings")
    public PageResponse<MyBookingSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "upcoming") @Pattern(regexp = "^(upcoming|past)$") String scope,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(PageResponse.MAX_SIZE) Integer size) {
        return historyService.list(UUID.fromString(jwt.getSubject()), Scope.valueOf(scope.toUpperCase(Locale.ROOT)),
                page == null ? 0 : page, size == null ? PageResponse.DEFAULT_SIZE : size);
    }
}
