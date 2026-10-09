package com.integrate.modules.booking.service;

import com.integrate.modules.booking.service.model.ExpiredBatch;

/** Expiración de reservas {@code PENDING} sin pago y restitución de sus cupos (RF-11, ADR-0008 §7). */
public interface BookingExpirationService {

    /**
     * Expira en una sola transacción hasta {@code limit} reservas vencidas que ninguna otra transacción tenga
     * bloqueadas.
     *
     * @return reservas expiradas y cupos restituidos; {@code expired < limit} cuando no quedan más disponibles
     */
    ExpiredBatch expireBatch(int limit);
}
