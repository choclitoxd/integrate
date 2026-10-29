package com.integrate.modules.voucher.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de canje (ADR-0032 §4): la única regla que consume un voucher y ejecuta su reserva. La usan el canje manual
 * de contingencia y la sincronización por lotes. Interfaz pública de {@code voucher}.
 */
public interface VoucherRedemption {

    /**
     * Pasa el voucher {@code ISSUED → CONSUMED} y su reserva {@code CONFIRMED → EXECUTED} en la transacción en curso.
     * El invocador ya bloqueó la fila del voucher.
     *
     * @throws IllegalStateException si el voucher no estaba {@code ISSUED} o la reserva no estaba {@code CONFIRMED}
     */
    void redeem(UUID voucherId, UUID bookingId, Instant at);
}
