package com.integrate.modules.payment.controller;

import com.integrate.modules.payment.dto.request.CancelBookingRequest;
import com.integrate.modules.payment.dto.response.CancellationResponse;
import com.integrate.modules.payment.service.BookingCancellationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cancelación de una reserva propia con la regla de 72 horas (ADR-0034 §2). */
@RestController
@PreAuthorize("hasRole('TOURIST')")
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingCancellationService cancellationService;

    @PostMapping("/api/v1/bookings/{bookingId}/cancel")
    public ResponseEntity<CancellationResponse> cancel(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID bookingId,
                                                       @Valid @RequestBody CancelBookingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(cancellationService.cancel(UUID.fromString(jwt.getSubject()), bookingId, request.reason()));
    }
}