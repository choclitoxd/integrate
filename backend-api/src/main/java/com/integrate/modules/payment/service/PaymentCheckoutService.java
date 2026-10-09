package com.integrate.modules.payment.service;

import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.service.model.CheckoutOutcome;
import java.util.UUID;

/** Inicio del cobro de una reserva (ADR-0009 §3). */
public interface PaymentCheckoutService {

    /**
     * Crea el intento de cobro o reutiliza el abierto ({@code INITIATED | PENDING}) de la reserva.
     *
     * @throws com.integrate.common.exception.BusinessRuleException 404 si la reserva no existe o es de otro turista;
     *         409 {@code PAYMENT_BOOKING_NOT_PAYABLE} si no está {@code PENDING} o venció su TTL
     */
    CheckoutOutcome start(UUID touristId, UUID bookingId, PaymentMethod method, ClientInfo client);
}
