package com.integrate.modules.booking.entity;

import com.integrate.common.persistence.PgDomains;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Hecho jurídico de la cancelación total de una reserva y la política aplicada ({@code booking.booking_cancellations},
 * append-only, RF-13/RF-14, ADR-0034 §2). Solo se inserta.
 */
@Entity
@Table(name = "booking_cancellations", schema = "booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingCancellation {

    public static final String EARLY_REFUND = "EARLY_REFUND";
    public static final String LATE_NO_REFUND = "LATE_NO_REFUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "actor_type", nullable = false, length = 10, updatable = false)
    private String actorType;

    @Column(name = "cancelled_by", updatable = false)
    private UUID cancelledBy;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "hours_before_start", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal hoursBeforeStart;

    @Column(name = "policy_applied", nullable = false, length = 30, updatable = false)
    private String policyApplied;

    @Column(name = "tourist_refund_pct", nullable = false, precision = 5, scale = 2, updatable = false,
            columnDefinition = PgDomains.PERCENTAGE)
    private BigDecimal touristRefundPct;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant cancelledAt;

    public static BookingCancellation byTourist(UUID bookingId, UUID touristId, String reason,
                                                BigDecimal hoursBeforeStart, boolean early, BigDecimal refundPct,
                                                Instant cancelledAt) {
        BookingCancellation c = new BookingCancellation();
        c.bookingId = bookingId;
        c.actorType = "TOURIST";
        c.cancelledBy = touristId;
        c.reason = reason;
        c.hoursBeforeStart = hoursBeforeStart;
        c.policyApplied = early ? EARLY_REFUND : LATE_NO_REFUND;
        c.touristRefundPct = refundPct;
        c.cancelledAt = cancelledAt;
        return c;
    }
}