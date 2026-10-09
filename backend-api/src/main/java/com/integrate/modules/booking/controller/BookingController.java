package com.integrate.modules.booking.controller;

import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.booking.dto.request.BookingLockRequest;
import com.integrate.modules.booking.dto.response.BookingLockResponse;
import com.integrate.modules.booking.service.BookingLockService;
import com.integrate.modules.booking.service.BookingQueryService;
import com.integrate.modules.booking.service.model.LockOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reservas del turista (contrato §5, ADR-0008 §1). */
@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasRole('TOURIST')")
@RequiredArgsConstructor
public class BookingController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final BookingLockService lockService;
    private final BookingQueryService queryService;

    @PostMapping("/lock")
    public ResponseEntity<BookingLockResponse> lock(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestHeader(IDEMPOTENCY_KEY) UUID idempotencyKey,
                                                    @Valid @RequestBody BookingLockRequest request,
                                                    HttpServletRequest http) {
        LockOutcome outcome = lockService.lock(UUID.fromString(jwt.getSubject()), idempotencyKey, request,
                clientOf(http));
        BookingLockResponse booking = outcome.booking();
        if (outcome.replayed()) {
            return ResponseEntity.ok().header(IDEMPOTENCY_REPLAYED, "true").body(booking);
        }
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.bookingId())).body(booking);
    }

    @GetMapping("/{bookingId}")
    public BookingLockResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return queryService.get(UUID.fromString(jwt.getSubject()), bookingId);
    }

    /** IP de {@code getRemoteAddr()}, nunca {@code X-Forwarded-For} leído a mano (mismo criterio que {@code auth}). */
    private static ClientInfo clientOf(HttpServletRequest request) {
        try {
            // getRemoteAddr() devuelve un literal IP: getByName no hace resolución DNS.
            return new ClientInfo(InetAddress.getByName(request.getRemoteAddr()),
                    request.getHeader(HttpHeaders.USER_AGENT));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Servlet container returned a non-literal remote address", e);
        }
    }
}
