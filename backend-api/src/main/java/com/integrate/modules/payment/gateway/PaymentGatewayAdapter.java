package com.integrate.modules.payment.gateway;

import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.entity.PaymentProvider;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

/**
 * Puerto hacia la pasarela de pagos (RF-12, ADR-0009 §2). Ninguna implementación hace llamadas de red en T-1.7: el
 * cobro lo completa el cliente con los datos de {@link #start} y el resultado llega por webhook.
 */
public interface PaymentGatewayAdapter {

    PaymentProvider provider();

    /** {@code false} si faltan sus secretos: el adaptador no se usa y su webhook responde 404. */
    boolean configured();

    /**
     * Datos para que el cliente pague. Se recalculan en cada llamada (p. ej. la firma de integridad de Wompi), así que
     * reutilizar un intento después de rotar un secreto no entrega datos vencidos.
     *
     * @return mapa serializable con al menos {@code type}
     */
    Map<String, Object> start(UUID paymentId, long amountCop, PaymentMethod method);

    /**
     * Verifica la firma en tiempo constante, valida la marca de tiempo y traduce el cuerpo al modelo canónico.
     *
     * @throws WebhookRejectedException firma inválida o marca de tiempo fuera de la ventana (401)
     * @throws com.integrate.common.exception.BusinessRuleException cuerpo malformado (400) o proveedor cruzado (422)
     */
    VerifiedEvent verify(byte[] rawBody, HttpHeaders headers);
}
