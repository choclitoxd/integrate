package com.integrate.modules.payment.gateway;

/**
 * Evento con firma válida, listo para procesarse.
 *
 * @param signature   firma recibida ({@code X-Signature} o checksum de Wompi), para la bandeja de entrada
 * @param payloadJson cuerpo crudo como texto JSON, para auditoría y reprocesamiento
 */
public record VerifiedEvent(CanonicalPaymentEvent event, String signature, String payloadJson) {
}
