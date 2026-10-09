package com.integrate.modules.payment.gateway;

import com.integrate.modules.payment.entity.PaymentProvider;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de la pasarela traducido al modelo de dominio (contrato §6.1). Lo produce un {@link PaymentGatewayAdapter}
 * después de verificar la firma.
 *
 * @param bookingId        reserva indicada por el evento (Mock), o {@code null} si llega por referencia (Wompi)
 * @param paymentReference referencia del intento ({@code gateway_reference} = id del pago), o {@code null} (Mock)
 * @param eventType        tipo de evento de la pasarela, para la bandeja de entrada
 */
public record CanonicalPaymentEvent(String eventId, PaymentProvider provider, String transactionId, UUID bookingId,
                                    String paymentReference, long amountCop, GatewayStatus status, Instant timestamp,
                                    String eventType) {

    public CanonicalPaymentEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(eventType, "eventType");
    }
}
