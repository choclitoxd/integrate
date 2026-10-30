package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.Disbursement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {

    /**
     * Reactiva la dispersión comunitaria {@code CANCELLED → RELEASED} (ADR-0033 §4). El trigger de la base solo lo
     * permite si la reserva está en {@code SYNC_CONFLICT}; el flush previo garantiza que ese estado ya está escrito.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE payments.disbursements SET status = 'RELEASED', released_at = :at
             WHERE status = 'CANCELLED'
               AND payment_id IN (SELECT id FROM payments.payment_transactions WHERE booking_id = :bookingId)""",
            nativeQuery = true)
    int releaseCancelledOfBooking(@Param("bookingId") UUID bookingId, @Param("at") Instant at);

    /** Cancelación anticipada (ADR-0034 §2): la comunidad no recibe nada; el motivo es obligatorio por el CHECK. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE payments.disbursements SET status = 'CANCELLED', cancellation_reason = :reason
             WHERE status IN ('SCHEDULED', 'ON_HOLD')
               AND payment_id IN (SELECT id FROM payments.payment_transactions WHERE booking_id = :bookingId)""",
            nativeQuery = true)
    int cancelOfBooking(@Param("bookingId") UUID bookingId, @Param("reason") String reason);

    @Query(value = """
            SELECT COALESCE(SUM(r.amount_cop), 0) FROM payments.refunds r
              JOIN payments.payment_transactions p ON p.id = r.payment_id
             WHERE p.booking_id = :bookingId AND r.status <> 'FAILED'""", nativeQuery = true)
    BigDecimal refundedAmountOfBooking(@Param("bookingId") UUID bookingId);
}