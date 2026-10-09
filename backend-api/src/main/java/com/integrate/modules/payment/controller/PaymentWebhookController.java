package com.integrate.modules.payment.controller;

import com.integrate.modules.payment.dto.response.WebhookAck;
import com.integrate.modules.payment.entity.PaymentProvider;
import com.integrate.modules.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhooks de las pasarelas (contrato §6, ADR-0009 §4). Públicos: la autenticidad la da la firma, calculada sobre
 * los bytes exactos del cuerpo, por eso se reciben crudos.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    @PostMapping("/mock")
    public WebhookAck mock(@RequestBody byte[] body, @RequestHeader HttpHeaders headers) {
        return webhookService.receive(PaymentProvider.MOCK, body, headers);
    }

    @PostMapping("/wompi")
    public WebhookAck wompi(@RequestBody byte[] body, @RequestHeader HttpHeaders headers) {
        return webhookService.receive(PaymentProvider.WOMPI, body, headers);
    }
}
