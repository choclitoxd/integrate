package com.integrate.modules.payment.gateway;

/**
 * Estado reportado por la pasarela ({@code CanonicalPaymentEvent.status}). {@code PENDING} solo lo emite Wompi y se
 * acepta sin cambios en la reserva (decisión de cierre #5).
 */
public enum GatewayStatus {
    APPROVED, DECLINED, VOIDED, ERROR, PENDING
}
