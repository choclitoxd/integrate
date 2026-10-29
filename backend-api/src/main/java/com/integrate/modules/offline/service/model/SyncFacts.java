package com.integrate.modules.offline.service.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Hechos de un ítem del lote que alimentan al resolvedor (ADR-0033 §3).
 *
 * @param receivedAt      hora del servidor al recibir el lote
 * @param voucherKnown    el voucher existe, es de la asociación del invocador y el {@code shortCode} coincide
 * @param voucherStatus   {@code voucher.vouchers.status}; nulo si {@code voucherKnown} es falso
 * @param bookingStatus   {@code booking.bookings.status}; nulo si {@code voucherKnown} es falso
 * @param validDate       fecha local (Bogotá) de la franja; nula si {@code voucherKnown} es falso
 * @param alreadyAccepted existe una validación aceptada del voucher (de otro lote, del canje manual o de un ítem
 *                        anterior de este lote) o el voucher ya está {@code CONSUMED}
 */
public record SyncFacts(Instant receivedAt, Instant scannedAt, boolean voucherKnown, String voucherStatus,
                        String bookingStatus, LocalDate validDate, boolean alreadyAccepted) {
}
