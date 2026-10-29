package com.integrate.modules.offline.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.modules.booking.service.BookingConflicts;
import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest.Channel;
import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest.Validation;
import com.integrate.modules.offline.dto.response.OfflineSyncBatchResponse;
import com.integrate.modules.offline.repository.OfflineQueries;
import com.integrate.modules.offline.repository.OfflineQueries.StoredValidation;
import com.integrate.modules.offline.repository.OfflineQueries.VoucherFacts;
import com.integrate.modules.offline.service.model.SyncDecision;
import com.integrate.modules.offline.service.model.SyncDecision.Effect;
import com.integrate.modules.payment.service.CommunityProtection;
import com.integrate.modules.voucher.service.VoucherRedemption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la decisión del resolvedor (ADR-0033): evidencia append-only, incidente y llamadas a los puertos. No decide
 * nada; sin transacción propia (corre dentro de la del lote).
 */
@Component
@RequiredArgsConstructor
class SyncDecisionApplier {

    private final OfflineQueries queries;
    private final VoucherRedemption redemption;
    private final BookingConflicts bookingConflicts;
    private final CommunityProtection communityProtection;

    /** Contexto fijo del lote para todos sus ítems. */
    record BatchContext(UUID batchId, UUID deviceId, UUID userId, Instant receivedAt) {
    }

    /**
     * @param result        resultado del ítem para la respuesta
     * @param accepted      el ítem canjeó el voucher
     * @param conflictMarked la reserva quedó en {@code SYNC_CONFLICT} con este ítem
     * @param audits        entradas de auditoría que el lote escribe al final
     */
    record Applied(OfflineSyncBatchResponse.Item result, boolean accepted, boolean conflictMarked,
                   java.util.List<AuditEntry> audits) {
    }

    /** Resultado de un ítem ya guardado antes (otro lote o el mismo escaneo físico): no se repiten efectos. */
    Applied alreadyStored(Validation item, StoredValidation stored) {
        String bookingStatus = queries.bookingStatusOfVoucher(stored.voucherId()).orElse(null);
        return new Applied(new OfflineSyncBatchResponse.Item(item.localValidationId(), item.voucherId(),
                statusOf(stored.serverOutcome()), stored.serverOutcome(), bookingStatus, stored.incidentType(),
                stored.incidentId(), messageOf(stored.serverOutcome())), "ACCEPTED".equals(stored.serverOutcome()),
                false, java.util.List.of());
    }

    Applied apply(BatchContext ctx, Validation item, VoucherFacts voucher, SyncDecision decision,
                  boolean chargeFinancialImpact) {
        java.util.List<AuditEntry> audits = new java.util.ArrayList<>();
        String bookingStatus = voucher == null ? null : voucher.bookingStatus();

        if (voucher == null || !decision.effects().contains(Effect.PERSIST_EVIDENCE)) {
            if (voucher == null || "REJECTED_UNKNOWN_VOUCHER".equals(decision.serverOutcome())) {
                audits.add(new AuditEntry("offline", "voucher", item.voucherId().toString(),
                        "OFFLINE_SYNC_UNKNOWN_VOUCHER", AuditEntry.Severity.WARNING, ctx.userId(), null, null,
                        Map.of("batchId", ctx.batchId().toString(), "localValidationId",
                                item.localValidationId().toString())));
            }
            return new Applied(item(item, decision, bookingStatus, null), false, false, audits);
        }

        Optional<Long> validationId = queries.insertSyncValidation(voucher.voucherId(), item.channel().name(),
                ctx.batchId(), ctx.deviceId(), ctx.userId(), item.scannedAt(),
                item.channel() == Channel.OFFLINE_PWA ? item.signatureHash().toLowerCase(Locale.ROOT) : null,
                decision.serverOutcome(), item.localValidationId());
        if (validationId.isEmpty()) {
            // Mismo voucher, dispositivo e instante que otro ítem: es el mismo escaneo físico y comparte su resultado.
            StoredValidation same = queries.findValidationByScan(voucher.voucherId(), ctx.deviceId(), item.scannedAt())
                    .orElseThrow();
            return alreadyStored(item, same);
        }

        boolean accepted = decision.effects().contains(Effect.REDEEM);
        boolean conflictMarked = decision.effects().contains(Effect.MARK_SYNC_CONFLICT);
        if (accepted) {
            redemption.redeem(voucher.voucherId(), voucher.bookingId(), ctx.receivedAt());
            bookingStatus = "EXECUTED";
        }
        BigDecimal impact = null;
        if (conflictMarked) {
            bookingConflicts.markSyncConflict(voucher.bookingId());
            bookingStatus = "SYNC_CONFLICT";
            if (decision.effects().contains(Effect.PROTECT_COMMUNITY_SHARE)) {
                communityProtection.release(voucher.bookingId(), ctx.receivedAt());
            }
            if (chargeFinancialImpact) {
                BigDecimal refunded = communityProtection.refundedAmount(voucher.bookingId());
                impact = refunded.signum() > 0 ? refunded : null;
            }
        }

        UUID incidentId = null;
        if (decision.incidentType() != null) {
            incidentId = queries.insertIncident(validationId.get(), decision.incidentType(),
                    decision.incidentSeverity(), impact).orElse(null);
            if (incidentId != null) {
                Map<String, Object> details = new HashMap<>();
                details.put("incidentType", decision.incidentType());
                details.put("batchId", ctx.batchId().toString());
                details.put("voucherId", voucher.voucherId().toString());
                if (impact != null) {
                    details.put("financialImpactCop", impact.toPlainString());
                }
                audits.add(new AuditEntry("offline", "sync_incident", incidentId.toString(), "SYNC_INCIDENT_OPENED",
                        "HIGH".equals(decision.incidentSeverity()) ? AuditEntry.Severity.HIGH
                                : AuditEntry.Severity.WARNING, ctx.userId(), null, null, details));
            }
        }
        return new Applied(item(item, decision, bookingStatus, incidentId), accepted, conflictMarked, audits);
    }

    private static OfflineSyncBatchResponse.Item item(Validation item, SyncDecision decision, String bookingStatus,
                                                      UUID incidentId) {
        return new OfflineSyncBatchResponse.Item(item.localValidationId(), item.voucherId(), decision.status(),
                decision.serverOutcome(), bookingStatus, decision.incidentType(), incidentId, decision.message());
    }

    static String statusOf(String serverOutcome) {
        return switch (serverOutcome) {
            case "ACCEPTED" -> "CONFIRMED";
            case "REJECTED_OUT_OF_DATE", "REJECTED_UNKNOWN_VOUCHER" -> "REJECTED";
            default -> "CONFLICT";
        };
    }

    static String messageOf(String serverOutcome) {
        return switch (serverOutcome) {
            case "ACCEPTED" -> "Canje exitoso";
            case "REJECTED_CLOCK_SKEW" -> "La hora del escaneo supera la del servidor en más de 5 minutos.";
            case "REJECTED_OUT_OF_DATE" -> "El escaneo no corresponde a la fecha válida del voucher.";
            case "REJECTED_REVOKED" -> "Voucher revocado en servidor; 85% comunitario protegido y derivado a arbitraje.";
            case "REJECTED_DUPLICATE" -> "El voucher ya fue canjeado.";
            default -> "Voucher no encontrado.";
        };
    }
}