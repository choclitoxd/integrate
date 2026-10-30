package com.integrate.modules.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Protección del 85 % comunitario ante un conflicto de sincronización (ADR-0033 §4, RF-18). Interfaz pública de
 * {@code payment}. No reembolsa ni revierte nada: solo reactiva una dispersión cancelada.
 */
public interface CommunityProtection {

    /**
     * Pasa la dispersión del pago de la reserva de {@code CANCELLED} a {@code RELEASED}; no hace nada si la dispersión
     * no está cancelada o no existe. Exige que la reserva ya esté en {@code SYNC_CONFLICT} (lo impone también la base).
     */
    void release(UUID bookingId, Instant at);

    /** Reembolsos de la reserva que no fallaron: lo que la plataforma ya devolvió al turista y ahora absorbe. */
    BigDecimal refundedAmount(UUID bookingId);
}