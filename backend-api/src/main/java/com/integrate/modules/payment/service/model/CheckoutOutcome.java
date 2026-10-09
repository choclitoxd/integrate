package com.integrate.modules.payment.service.model;

import com.integrate.modules.payment.dto.response.PaymentCheckoutResponse;

/** @param created {@code true} si se creó el intento (201); {@code false} si se reutilizó uno abierto (200) */
public record CheckoutOutcome(PaymentCheckoutResponse payment, boolean created) {
}
