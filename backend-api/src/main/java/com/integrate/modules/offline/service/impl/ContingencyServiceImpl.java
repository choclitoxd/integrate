package com.integrate.modules.offline.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.offline.dto.response.RedeemResponse;
import com.integrate.modules.offline.repository.OfflineQueries;
import com.integrate.modules.offline.repository.OfflineQueries.ManagerScope;
import com.integrate.modules.offline.repository.OfflineQueries.VoucherLock;
import com.integrate.modules.offline.service.ContingencyService;
import com.integrate.modules.offline.service.model.RedeemOutcome;
import com.integrate.modules.voucher.service.VoucherRedemption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canje manual de contingencia (ADR-0032 §4). Una transacción que bloquea la fila del voucher, aplica la tabla de
 * verificaciones en orden y deja evidencia append-only de cada intento; la auditoría es la última sentencia.
 */
@Service
@RequiredArgsConstructor
class ContingencyServiceImpl implements ContingencyService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final OfflineQueries queries;
    private final OfflineScopes scopes;
    private final VoucherRedemption redemption;
    private final AuditTrail audit;
    private final Clock clock;

    @Override
    @Transactional
    public RedeemOutcome redeem(UUID userId, String shortCode, String deviceIdentifier, String userAgent) {
        ManagerScope manager = scopes.requireManager(userId);
        UUID deviceId = deviceIdentifier == null ? null : scopes.resolveDevice(manager, deviceIdentifier, userAgent);
        Instant now = clock.instant();

        VoucherLock voucher = queries.lockVoucher(shortCode.toUpperCase(Locale.ROOT), manager.associationId())
                .orElse(null);
        if (voucher == null) {
            // Código inexistente o de otra asociación: misma respuesta; sin voucher no hay FK donde dejar la fila.
            audit.record(new AuditEntry("offline", "voucher", "unknown", "CONTINGENCY_CODE_UNKNOWN",
                    AuditEntry.Severity.WARNING, userId, null, userAgent,
                    Map.of("managerId", manager.managerId().toString())));
            return RedeemOutcome.rejected(ErrorCode.VOUCHER_NOT_FOUND);
        }

        if ("REVOKED".equals(voucher.status())) {
            return reject(voucher, deviceId, userId, now, "REJECTED_REVOKED", "REJECTED_REVOKED", RedeemOutcome.rejected(ErrorCode.VOUCHER_REVOKED));
        }
        if ("CONSUMED".equals(voucher.status())) {
            return reject(voucher, deviceId, userId, now, "REJECTED_DUPLICATE", "REJECTED_DUPLICATE",
                    RedeemOutcome.alreadyRedeemed(voucher.consumedAt()));
        }
        if ("EXPIRED".equals(voucher.status()) || voucher.expiresAt().isBefore(now)) {
            return reject(voucher, deviceId, userId, now, "REJECTED_WRONG_DATE", "REJECTED_OUT_OF_DATE",
                    RedeemOutcome.rejected(ErrorCode.VOUCHER_EXPIRED));
        }
        if (!LocalDate.ofInstant(voucher.scheduleStart(), BOGOTA).equals(LocalDate.ofInstant(now, BOGOTA))) {
            return reject(voucher, deviceId, userId, now, "REJECTED_WRONG_DATE", "REJECTED_OUT_OF_DATE",
                    RedeemOutcome.rejected(ErrorCode.VOUCHER_WRONG_DATE));
        }
        if (!"CONFIRMED".equals(voucher.bookingStatus())) {
            return reject(voucher, deviceId, userId, now, "REJECTED_REVOKED", "REJECTED_REVOKED", RedeemOutcome.rejected(ErrorCode.BOOKING_NOT_REDEEMABLE));
        }

        redemption.redeem(voucher.voucherId(), voucher.bookingId(), now);
        queries.insertManualValidation(voucher.voucherId(), deviceId, userId, now, "ACCEPTED", "ACCEPTED");
        Map<String, Object> details = new HashMap<>();
        details.put("bookingId", voucher.bookingId().toString());
        details.put("slots", voucher.slotsCount());
        if (deviceId != null) {
            details.put("deviceId", deviceId.toString());
        }
        audit.record(new AuditEntry("offline", "voucher", voucher.voucherId().toString(), "CONTINGENCY_REDEEMED",
                AuditEntry.Severity.INFO, userId, null, userAgent, details));
        return RedeemOutcome.accepted(new RedeemResponse(voucher.voucherId(), voucher.bookingId(),
                voucher.slotsCount(), voucher.holderName(), voucher.experienceTitle(), now));
    }

    private RedeemOutcome reject(VoucherLock voucher, UUID deviceId, UUID userId, Instant now, String localResult,
                                 String serverOutcome, RedeemOutcome outcome) {
        queries.insertManualValidation(voucher.voucherId(), deviceId, userId, now, localResult, serverOutcome);
        return outcome;
    }
}
