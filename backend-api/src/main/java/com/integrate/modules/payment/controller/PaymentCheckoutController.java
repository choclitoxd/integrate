package com.integrate.modules.payment.controller;

import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.payment.dto.request.PaymentStartRequest;
import com.integrate.modules.payment.dto.response.PaymentCheckoutResponse;
import com.integrate.modules.payment.service.PaymentCheckoutService;
import com.integrate.modules.payment.service.model.CheckoutOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Inicio del cobro de una reserva propia (ADR-0009 §1, §3). */
@RestController
@PreAuthorize("hasRole('TOURIST')")
@RequiredArgsConstructor
public class PaymentCheckoutController {

    private final PaymentCheckoutService checkoutService;

    @PostMapping("/api/v1/bookings/{bookingId}/payment")
    public ResponseEntity<PaymentCheckoutResponse> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId,
                                                         @Valid @RequestBody PaymentStartRequest request,
                                                         HttpServletRequest http) {
        CheckoutOutcome outcome = checkoutService.start(UUID.fromString(jwt.getSubject()), bookingId,
                request.paymentMethod(), clientOf(http));
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(outcome.payment());
    }

    /** IP de {@code getRemoteAddr()}, nunca {@code X-Forwarded-For} leído a mano (mismo criterio que {@code auth}). */
    static ClientInfo clientOf(HttpServletRequest request) {
        try {
            // getRemoteAddr() devuelve un literal IP: getByName no hace resolución DNS.
            return new ClientInfo(InetAddress.getByName(request.getRemoteAddr()),
                    request.getHeader(HttpHeaders.USER_AGENT));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Servlet container returned a non-literal remote address", e);
        }
    }
}
