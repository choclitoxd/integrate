package com.integrate.modules.payment.dto.response;

/**
 * Acuse de un webhook (contrato §6.2). {@code paymentStatus} y {@code bookingStatus} son los estados después de
 * procesar el evento, o los actuales si es un duplicado; ambos son {@code null} en un evento ignorado.
 */
public record WebhookAck(boolean received, boolean duplicate, String provider, String eventId, String paymentStatus,
                         String bookingStatus) {
}
