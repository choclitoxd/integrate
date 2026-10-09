package com.integrate.modules.payment.dto.request;

import com.integrate.modules.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/bookings/{bookingId}/payment} (ADR-0009 §3). */
public record PaymentStartRequest(@NotNull PaymentMethod paymentMethod) {
}
