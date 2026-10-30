package com.integrate.modules.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de la cancelación del turista; queda como evidencia en {@code booking_cancellations}. */
public record CancelBookingRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
}