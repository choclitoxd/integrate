package com.integrate.modules.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.integrate.common.audit.AuditTrail;
import com.integrate.common.storage.VoucherDocumentStorage;
import com.integrate.modules.notification.service.NotificationOutbox;
import com.integrate.modules.payment.gateway.PayoutGateway;
import com.integrate.modules.payment.repository.PayoutQueries;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PayoutConcurrencyTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aWorkerThatLosesTheTransitionDoesNotRepeatEffects(boolean completed) {
        PayoutQueries queries = mock(PayoutQueries.class);
        PayoutGateway gateway = mock(PayoutGateway.class);
        ReceiptPdfRenderer renderer = mock(ReceiptPdfRenderer.class);
        VoucherDocumentStorage storage = mock(VoucherDocumentStorage.class);
        NotificationOutbox outbox = mock(NotificationOutbox.class);
        AuditTrail audit = mock(AuditTrail.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var batch = new PayoutQueries.SubmittedBatch(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), BigDecimal.valueOf(8500));
        when(queries.submittedBatches(anyInt())).thenReturn(List.of(batch));
        when(gateway.payout("payout:" + batch.id(), batch.id(), batch.payoutAccountId(), 8500))
                .thenReturn(completed ? PayoutGateway.Result.completed("provider-reference")
                        : PayoutGateway.Result.rejected("Rejected"));
        PayoutProcessor processor = new PayoutProcessor(queries, gateway, renderer, storage, outbox,
                audit, transactions, Clock.systemUTC());

        assertThat(processor.sweepOnce()).isZero();

        verify(queries, never()).payShares(any());
        verify(queries, never()).detachShares(any());
        verify(queries, never()).totals(any());
        verifyNoInteractions(renderer, storage, outbox, audit);
    }
}
