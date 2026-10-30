package com.integrate.modules.offline.service.impl;

import com.integrate.modules.offline.service.model.SyncDecision;
import com.integrate.modules.offline.service.model.SyncDecision.Effect;
import com.integrate.modules.offline.service.model.SyncFacts;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * Resolución determinista de conflictos (T-2.4, ADR-0033 §3): función pura de los hechos de un ítem a una decisión.
 * Sin I/O ni reloj propio. Las reglas siguen el orden del contrato y el primer acierto decide.
 */
public final class SyncConflictResolver {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

    private static final Set<String> CANCELLED_BOOKING = Set.of("CANCELLED_REFUNDED", "CANCELLED_NO_REFUND",
            "CANCELLED_BY_PLATFORM", "SYNC_CONFLICT");
    private static final Set<String> REDEEMABLE_BOOKING = Set.of("CONFIRMED", "NO_SHOW");

    private SyncConflictResolver() {
    }

    public static SyncDecision decide(SyncFacts f) {
        // 1. Reloj del dispositivo adelantado.
        if (f.scannedAt().isAfter(f.receivedAt().plus(CLOCK_TOLERANCE))) {
            return new SyncDecision("REJECTED_CLOCK_SKEW", "CONFLICT", "CLOCK_SKEW_FUTURE_SCAN", "MEDIUM",
                    f.voucherKnown() ? Set.of(Effect.PERSIST_EVIDENCE) : Set.of(),
                    "La hora del escaneo supera la del servidor en más de 5 minutos.");
        }
        // 2. Voucher inexistente, ajeno o con código que no coincide.
        if (!f.voucherKnown()) {
            return new SyncDecision("REJECTED_UNKNOWN_VOUCHER", "REJECTED", null, null, Set.of(),
                    "Voucher no encontrado.");
        }
        // 3. Fecha local distinta de la válida, o voucher vencido.
        LocalDate scanDay = f.scannedAt().atZone(BOGOTA).toLocalDate();
        if ("EXPIRED".equals(f.voucherStatus()) || !scanDay.equals(f.validDate())) {
            return new SyncDecision("REJECTED_OUT_OF_DATE", "REJECTED", null, null, Set.of(Effect.PERSIST_EVIDENCE),
                    "El escaneo no corresponde a la fecha válida del voucher.");
        }
        // 4. Revocado o cancelado: el 85 % comunitario es irrenunciable.
        if ("REVOKED".equals(f.voucherStatus()) || CANCELLED_BOOKING.contains(f.bookingStatus())) {
            return new SyncDecision("REJECTED_REVOKED", "CONFLICT", "OVER_REDEMPTION_COMMUNITY_PROTECTED", "HIGH",
                    Set.of(Effect.PERSIST_EVIDENCE, Effect.MARK_SYNC_CONFLICT, Effect.PROTECT_COMMUNITY_SHARE),
                    "Voucher revocado en servidor; 85% comunitario protegido y derivado a arbitraje.");
        }
        // 5. Ya canjeado: gana el escaneo más temprano ya aceptado.
        if (f.alreadyAccepted() || "CONSUMED".equals(f.voucherStatus()) || "EXECUTED".equals(f.bookingStatus())) {
            return new SyncDecision("REJECTED_DUPLICATE", "CONFLICT", "DUPLICATE_OFFLINE_REDEMPTION", "MEDIUM",
                    Set.of(Effect.PERSIST_EVIDENCE), "El voucher ya fue canjeado.");
        }
        // 6. Canje válido; cualquier otro estado de reserva no debería tener voucher y se trata como desconocido.
        if (REDEEMABLE_BOOKING.contains(f.bookingStatus())) {
            return new SyncDecision("ACCEPTED", "CONFIRMED", null, null, Set.of(Effect.PERSIST_EVIDENCE, Effect.REDEEM),
                    "Canje exitoso");
        }
        return new SyncDecision("REJECTED_UNKNOWN_VOUCHER", "REJECTED", null, null, Set.of(), "Voucher no encontrado.");
    }
}
