package com.integrate.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PayoutBatchResponse(UUID id, UUID associationId, String associationName, Status status,
                                  BigDecimal grossCop, BigDecimal gatewayFeeCop, BigDecimal platformFeeCop,
                                  BigDecimal netCommunityCop, int disbursementCount, OffsetDateTime createdAt,
                                  OffsetDateTime submittedAt, OffsetDateTime paidAt, String failureReason,
                                  UUID receiptId) {

    public enum Status { OPEN, SUBMITTED, PAID, FAILED }
}
