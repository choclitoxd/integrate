package com.integrate.modules.booking.service;

import com.integrate.modules.booking.service.model.VoucherSubject;
import java.util.Optional;
import java.util.UUID;

/**
 * Datos de la reserva que ampara un voucher (ADR-0010 §8). Interfaz pública de {@code booking}, de solo lectura:
 * nunca expone la EPS ni el contacto de emergencia.
 */
public interface BookingVouchers {

    /** Vacío si la reserva no existe. */
    Optional<VoucherSubject> subjectOf(UUID bookingId);
}
