package com.integrate.modules.payment.service;

import com.integrate.modules.payment.dto.response.WebhookAck;
import com.integrate.modules.payment.entity.PaymentProvider;
import com.integrate.modules.payment.gateway.GatewayStatus;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

/** Webhooks de las pasarelas y simulador de {@code dev} (ADR-0009 §4-§5). */
public interface PaymentWebhookService {

    /**
     * Verifica la firma del cuerpo crudo y procesa el evento una sola vez.
     *
     * @throws com.integrate.common.exception.BusinessRuleException 401 firma inválida; 404 proveedor sin configurar;
     *         400 cuerpo malformado; 422 proveedor cruzado, reserva inexistente o monto distinto
     */
    WebhookAck receive(PaymentProvider provider, byte[] rawBody, HttpHeaders headers);

    /**
     * Simula el evento de la pasarela Mock para un intento del turista, por el mismo procesador del webhook (solo se
     * expone en {@code dev}).
     */
    WebhookAck simulate(UUID touristId, UUID paymentId, GatewayStatus status);
}
