package com.integrate.modules.payment.controller;

import com.integrate.modules.payment.dto.request.PaymentSimulationRequest;
import com.integrate.modules.payment.dto.response.WebhookAck;
import com.integrate.modules.payment.service.PaymentWebhookService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulador de la pasarela Mock (ADR-0009 §1, punto C): completa el flujo de pago desde la PWA o a mano sin firmar un
 * webhook. Solo existe en los perfiles {@code dev} y {@code test}; nunca en despliegue.
 */
@RestController
@Profile({"dev", "test"})
@PreAuthorize("hasRole('TOURIST')")
@RequiredArgsConstructor
public class DevPaymentSimulatorController {

    private final PaymentWebhookService webhookService;

    @PostMapping("/api/v1/dev/payments/{paymentId}/simulate")
    public WebhookAck simulate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId,
                               @Valid @RequestBody PaymentSimulationRequest request) {
        return webhookService.simulate(UUID.fromString(jwt.getSubject()), paymentId, request.status());
    }
}
