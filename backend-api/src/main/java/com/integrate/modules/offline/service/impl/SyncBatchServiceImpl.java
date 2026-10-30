package com.integrate.modules.offline.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest;
import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest.Channel;
import com.integrate.modules.offline.dto.request.OfflineSyncBatchRequest.Validation;
import com.integrate.modules.offline.dto.response.OfflineSyncBatchResponse;
import com.integrate.modules.offline.repository.OfflineQueries;
import com.integrate.modules.offline.repository.OfflineQueries.BatchRow;
import com.integrate.modules.offline.repository.OfflineQueries.DeviceRow;
import com.integrate.modules.offline.repository.OfflineQueries.ManagerScope;
import com.integrate.modules.offline.repository.OfflineQueries.StoredValidation;
import com.integrate.modules.offline.repository.OfflineQueries.VoucherFacts;
import com.integrate.modules.offline.service.SyncBatchService;
import com.integrate.modules.offline.service.impl.SyncDecisionApplier.Applied;
import com.integrate.modules.offline.service.impl.SyncDecisionApplier.BatchContext;
import com.integrate.modules.offline.service.model.SyncDecision;
import com.integrate.modules.offline.service.model.SyncFacts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sincronización por lotes (T-2.3, ADR-0033 §2): una transacción por lote, idempotente por {@code syncBatchId} y por
 * contenido. Los vouchers se procesan en orden de {@code jti} para que dos lotes concurrentes bloqueen siempre en el
 * mismo orden; dentro de cada voucher gana el escaneo más temprano. La respuesta conserva el orden del lote.
 */
@Service
@RequiredArgsConstructor
class SyncBatchServiceImpl implements SyncBatchService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final OfflineQueries queries;
    private final SyncDecisionApplier applier;
    private final AuditTrail audit;
    private final JsonMapper json;
    private final Clock clock;

    @Override
    @Transactional
    public OfflineSyncBatchResponse sync(UUID userId, String userAgent, OfflineSyncBatchRequest request) {
        validate(request);
        Caller caller = caller(userId);
        DeviceRow device = queries.findDevice(request.deviceId())
                .filter(d -> "ACTIVE".equals(d.status()))
                .filter(d -> caller.owns(d))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.OFFLINE_DEVICE_NOT_AUTHORIZED));

        Instant receivedAt = clock.instant();
        String payloadSha = payloadHash(request);
        if (!queries.insertBatchIfAbsent(request.syncBatchId(), device.id(), request.validations().size(),
                request.deviceSentAt(), payloadSha, receivedAt)) {
            return replay(request, device, payloadSha);
        }

        BatchContext ctx = new BatchContext(request.syncBatchId(), device.id(), userId, receivedAt);
        List<Validation> items = request.validations();
        OfflineSyncBatchResponse.Item[] results = new OfflineSyncBatchResponse.Item[items.size()];
        List<AuditEntry> audits = new ArrayList<>();

        // Vouchers por jti ascendente; dentro de cada uno, por (scannedAt, localValidationId).
        Map<UUID, List<Integer>> byVoucher = new TreeMap<>();
        for (int i = 0; i < items.size(); i++) {
            byVoucher.computeIfAbsent(items.get(i).voucherId(), k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<UUID, List<Integer>> group : byVoucher.entrySet()) {
            List<Integer> indexes = group.getValue();
            indexes.sort(Comparator.<Integer, Instant>comparing(i -> items.get(i).scannedAt())
                    .thenComparing(i -> items.get(i).localValidationId()));
            Optional<VoucherFacts> voucher = queries.lockVoucherByJti(group.getKey(), caller.associationId(),
                    caller.hostId());
            boolean acceptedInBatch = false;
            boolean impactCharged = false;
            for (int index : indexes) {
                Validation item = items.get(index);
                Optional<StoredValidation> stored = queries.findValidationByLocalId(item.localValidationId());
                Applied applied;
                if (stored.isPresent()) {
                    applied = applier.alreadyStored(item, stored.get());
                } else {
                    boolean known = voucher.isPresent() && voucher.get().shortCode().equals(item.shortCode());
                    SyncFacts facts = new SyncFacts(receivedAt, item.scannedAt(), known,
                            known ? voucher.get().status() : null, known ? voucher.get().bookingStatus() : null,
                            known ? LocalDate.ofInstant(voucher.get().scheduleStart(), BOGOTA) : null,
                            known && (voucher.get().alreadyAccepted() || acceptedInBatch));
                    SyncDecision decision = SyncConflictResolver.decide(facts);
                    applied = applier.apply(ctx, item, known ? voucher.get() : null, decision, !impactCharged);
                }
                acceptedInBatch |= applied.accepted();
                impactCharged |= applied.conflictMarked();
                results[index] = applied.result();
                audits.addAll(applied.audits());
            }
        }

        List<OfflineSyncBatchResponse.Item> ordered = List.of(results);
        int confirmed = count(ordered, "CONFIRMED");
        int conflicts = count(ordered, "CONFLICT");
        int rejected = count(ordered, "REJECTED");
        Instant processedAt = clock.instant();
        OfflineSyncBatchResponse response = new OfflineSyncBatchResponse(request.syncBatchId(), request.deviceId(),
                false, request.deviceSentAt(), receivedAt, processedAt,
                Duration.between(request.deviceSentAt(), receivedAt).getSeconds(),
                new OfflineSyncBatchResponse.Summary(ordered.size(), confirmed, conflicts, rejected), ordered);
        queries.completeBatch(request.syncBatchId(), conflicts + rejected == 0 ? "PROCESSED" : "PARTIAL", processedAt,
                json.writeValueAsString(response));

        // La auditoría encadenada va al final de la transacción (AuditTrail).
        audits.add(new AuditEntry("offline", "sync_batch", request.syncBatchId().toString(),
                "OFFLINE_SYNC_PROCESSED", AuditEntry.Severity.INFO, userId, null, userAgent,
                Map.of("received", ordered.size(), "confirmed", confirmed, "conflicts", conflicts,
                        "rejected", rejected, "deviceId", device.id().toString())));
        audits.forEach(audit::record);
        return response;
    }

    private OfflineSyncBatchResponse replay(OfflineSyncBatchRequest request, DeviceRow device, String payloadSha) {
        BatchRow existing = queries.findBatch(request.syncBatchId()).orElseThrow();
        if (!payloadSha.equals(existing.payloadSha256()) || !device.id().equals(existing.deviceId())
                || existing.resultPayload() == null) {
            throw new BusinessRuleException(ErrorCode.SYNC_BATCH_PAYLOAD_MISMATCH);
        }
        return json.readValue(existing.resultPayload(), OfflineSyncBatchResponse.class).asReplay();
    }

    /** Reglas de forma que el contrato pide con 422 y que Bean Validation no cubre. */
    private static void validate(OfflineSyncBatchRequest request) {
        Set<UUID> seen = new HashSet<>();
        for (Validation v : request.validations()) {
            if (!seen.add(v.localValidationId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "localValidationId repetido en el lote: " + v.localValidationId());
            }
            if (v.channel() == Channel.OFFLINE_PWA && v.signatureHash() == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "signatureHash es obligatorio cuando el canal es OFFLINE_PWA");
            }
        }
    }

    /** Hash del contenido canónico: ítems por {@code localValidationId}; los metadatos del lote no entran. */
    private static String payloadHash(OfflineSyncBatchRequest request) {
        StringBuilder canonical = new StringBuilder(request.deviceId());
        request.validations().stream().sorted(Comparator.comparing(Validation::localValidationId)).forEach(v ->
                canonical.append('\n').append(v.localValidationId()).append('|').append(v.voucherId()).append('|')
                        .append(v.scannedAt()).append('|')
                        .append(v.signatureHash() == null ? "" : v.signatureHash().toLowerCase(Locale.ROOT))
                        .append('|').append(v.shortCode()).append('|').append(v.channel()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int count(List<OfflineSyncBatchResponse.Item> items, String status) {
        return (int) items.stream().filter(i -> status.equals(i.status())).count();
    }

    private Caller caller(UUID userId) {
        Optional<ManagerScope> manager = queries.activeManager(userId);
        if (manager.isPresent()) {
            return new Caller(manager.get().managerId(), null, manager.get().associationId(), null);
        }
        UUID host = queries.activeHost(userId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.ENROLLMENT_MANAGER_NOT_ACTIVE));
        return new Caller(null, host, null, host);
    }

    /** Dueño del dispositivo y alcance de los vouchers que puede sincronizar. */
    private record Caller(UUID managerId, UUID hostProfileId, UUID associationId, UUID hostId) {
        boolean owns(DeviceRow device) {
            return managerId != null ? managerId.equals(device.managerId()) : hostProfileId.equals(device.hostId());
        }
    }
}