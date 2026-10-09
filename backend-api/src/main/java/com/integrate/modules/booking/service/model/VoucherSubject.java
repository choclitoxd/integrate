package com.integrate.modules.booking.service.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Reserva vista por el emisor de vouchers (ADR-0010 §8). Desde {@code CONFIRMED} los participantes son inmutables
 * ({@code fn_participant_guard}), así que estos datos sirven para volver a derivar el token firmado.
 *
 * @param status           valor de {@code booking.bookings.status}
 * @param totalAmountCop   total pactado de la reserva (resumen del correo, ADR-0011 §2)
 * @param participantNames nombres de todos los participantes, titular primero
 */
public record VoucherSubject(UUID bookingId, UUID touristId, String status, UUID scheduleId, int slots,
                             BigDecimal totalAmountCop, Holder holder, List<String> participantNames) {

    public static final String CONFIRMED = "CONFIRMED";

    public VoucherSubject {
        participantNames = List.copyOf(participantNames);
    }

    public boolean isConfirmed() {
        return CONFIRMED.equals(status);
    }

    /** Titular cuyo documento se imprime en el voucher y se verifica en campo (R-03). */
    public record Holder(String fullName, String docType, String docNumber) {
    }
}
