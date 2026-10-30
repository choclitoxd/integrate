package com.integrate.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de la cancelación (ADR-0034 §2).
 *
 * @param policyApplied     {@code EARLY_REFUND | LATE_NO_REFUND}
 * @param refundStatus      estado de la orden de reembolso, o {@code null} si no hay reembolso
 * @param slotsReleased     los cupos volvieron al catálogo (solo en la cancelación anticipada)
 */
public record CancellationResponse(UUID bookingId, String status, String policyApplied, BigDecimal hoursBeforeStart,
                                   BigDecimal touristRefundPct, long refundAmountCop, String refundStatus,
                                   boolean slotsReleased, Instant cancelledAt) {
}