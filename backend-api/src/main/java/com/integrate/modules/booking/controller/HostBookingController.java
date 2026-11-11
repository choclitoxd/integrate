package com.integrate.modules.booking.controller;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.booking.service.HostBookingService;
import com.integrate.modules.booking.service.HostBookingService.Filter;
import com.integrate.modules.booking.service.HostBookingService.HolderEps;
import com.integrate.modules.booking.service.HostBookingService.Period;
import com.integrate.modules.booking.service.HostBookingService.Summary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/host/bookings")
@PreAuthorize("hasRole('HOST')")
@RequiredArgsConstructor
public class HostBookingController {
    private final HostBookingService service;

    @GetMapping
    public PageResponse<Summary> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_SIZE) int size,
            @RequestParam(required = false) Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Size(max = 200) String experience,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return service.list(UUID.fromString(jwt.getSubject()), page, size, new Filter(period, date, experience));
    }

    @GetMapping("/{bookingId}/holder-eps")
    public HolderEps holderEps(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId,
            HttpServletRequest request, HttpServletResponse response) throws UnknownHostException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return service.holderEps(UUID.fromString(jwt.getSubject()), bookingId,
                new ClientInfo(InetAddress.getByName(request.getRemoteAddr()), request.getHeader(HttpHeaders.USER_AGENT)));
    }
}
