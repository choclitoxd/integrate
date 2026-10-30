package com.integrate.modules.booking.service;

import java.util.UUID;

/**
 * Conflicto de sincronización (ADR-0033 §4): una reserva cancelada cuyo voucher se usó en campo. Interfaz pública de
 * {@code booking}; la usa el motor de sincronización de {@code offline}.
 */
public interface BookingConflicts {

    /**
     * Pasa la reserva {@code CANCELLED_*} a {@code SYNC_CONFLICT} en la transacción en curso; no hace nada si ya lo
     * está.
     *
     * @throws IllegalStateException si la reserva no existe o no está cancelada
     */
    void markSyncConflict(UUID bookingId);
}