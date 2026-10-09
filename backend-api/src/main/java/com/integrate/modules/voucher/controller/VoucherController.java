package com.integrate.modules.voucher.controller;

import com.integrate.modules.voucher.dto.response.VoucherResponse;
import com.integrate.modules.voucher.service.VoucherQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Voucher de una reserva propia (ADR-0010 §1). La URL del PDF es prefirmada y vence pronto: no se cachea. */
@RestController
@PreAuthorize("hasRole('TOURIST')")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherQueryService queryService;

    @GetMapping("/api/v1/bookings/{bookingId}/voucher")
    public ResponseEntity<VoucherResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(queryService.get(UUID.fromString(jwt.getSubject()), bookingId));
    }
}
