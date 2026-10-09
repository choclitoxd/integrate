package com.integrate.modules.payment.dto.response;

import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.entity.PaymentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Intento de cobro y datos para pagar (ADR-0009 §3).
 *
 * @param provider         nombre del contrato ({@code MOCK_GATEWAY} o {@code WOMPI})
 * @param bookingExpiresAt fin del TTL de la reserva: el pago debe completarse antes
 * @param checkout         datos propios de la pasarela; siempre incluye {@code type}
 */
public record PaymentCheckoutResponse(UUID paymentId, UUID bookingId, String provider, PaymentMethod paymentMethod,
                                      PaymentStatus status, long amountCop, String currency, Instant bookingExpiresAt,
                                      Map<String, Object> checkout) {
}
