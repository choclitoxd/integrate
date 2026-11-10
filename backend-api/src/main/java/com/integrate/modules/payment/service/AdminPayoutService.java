package com.integrate.modules.payment.service;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.common.storage.VoucherDocumentStorage;
import com.integrate.common.storage.VoucherDocumentStorage.PresignedDownload;
import com.integrate.modules.payment.gateway.PayoutGateway;
import com.integrate.modules.payment.gateway.PayoutGateway.Result;
import com.integrate.modules.payment.repository.AdminPayoutQueries;
import com.integrate.modules.payment.dto.response.PayoutBatchResponse;
import com.integrate.modules.payment.repository.AdminPayoutQueries.Disbursement;
import com.integrate.modules.payment.dto.response.PayoutBatchResponse.Status;
import com.integrate.modules.payment.service.impl.PayoutProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AdminPayoutService {

    public record Detail(PayoutBatchResponse batch, List<Disbursement> disbursements) {
    }

    public enum LookupStatus { CHECKED, UNAVAILABLE, ALREADY_CLOSED }

    public record Reconciliation(PayoutBatchResponse batch, LookupStatus lookupStatus) {
    }

    private final AdminPayoutQueries queries;
    private final PayoutGateway gateway;
    private final PayoutProcessor processor;
    private final VoucherDocumentStorage storage;
    private final AuditTrail audit;
    private final PlatformTransactionManager transactions;

    @Transactional(readOnly = true)
    public PageResponse<PayoutBatchResponse> list(Status status, UUID associationId, Pageable page) {
        return queries.list(status, associationId, page);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id) {
        return new Detail(find(id), queries.disbursements(id));
    }

    public PresignedDownload receipt(UUID id) {
        String key = queries.receiptKey(id).orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND));
        return storage.presignDownload(key, Duration.ofMinutes(5));
    }

    public Reconciliation reconcile(UUID id, UUID actor) {
        PayoutBatchResponse before = find(id);
        var lookup = queries.lookup(id);
        Optional<Result> result = lookup.flatMap(ref -> gateway.lookup(ref.idempotencyKey(), ref.gatewayPayoutId()));
        LookupStatus lookupStatus = lookup.isEmpty() ? LookupStatus.ALREADY_CLOSED
                : result.isPresent() ? LookupStatus.CHECKED : LookupStatus.UNAVAILABLE;
        return new TransactionTemplate(transactions).execute(status -> {
            result.ifPresent(value -> processor.applyLookup(lookup.orElseThrow().batch(), value));
            PayoutBatchResponse after = find(id);
            audit.record(new AuditEntry("payments", "payout_batches", id.toString(), "PAYOUT_BATCH_RECONCILED",
                    after.status() == Status.FAILED ? AuditEntry.Severity.WARNING : AuditEntry.Severity.INFO,
                    actor, null, null, Map.of("previousStatus", before.status().name(), "status", after.status().name(),
                            "lookupStatus", lookupStatus.name())));
            return new Reconciliation(after, lookupStatus);
        });
    }

    private PayoutBatchResponse find(UUID id) {
        return queries.find(id).orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
