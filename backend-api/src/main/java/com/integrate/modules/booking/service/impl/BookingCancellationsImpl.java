package com.integrate.modules.booking.service.impl;

import com.integrate.common.persistence.StatusReasonContext;
import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.entity.BookingCancellation;
import com.integrate.modules.booking.repository.BookingCancellationRepository;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingCancellations;
import com.integrate.modules.booking.service.model.CancellableBooking;
import com.integrate.modules.catalog.service.SlotInventory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class BookingCancellationsImpl implements BookingCancellations {

    static final String REASON_EARLY = "TOURIST_CANCELLATION_EARLY";
    static final String REASON_LATE = "TOURIST_CANCELLATION_LATE";

    private final BookingRepository bookings;
    private final BookingCancellationRepository cancellations;
    private final SlotInventory slotInventory;
    private final StatusReasonContext statusReason;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CancellableBooking> lock(UUID bookingId) {
        return bookings.findByIdForUpdate(bookingId).map(b -> new CancellableBooking(b.getId(), b.getTouristId(),
                b.getScheduleId(), b.getStatus().name(), b.getSlotsCount(), b.getTotalAmountCop()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long cancelByTourist(UUID bookingId, UUID touristId, String reason, boolean early,
                                BigDecimal hoursBeforeStart, BigDecimal touristRefundPct, Instant cancelledAt) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " not found"));
        statusReason.set(early ? REASON_EARLY : REASON_LATE);
        if (early) {
            booking.cancelRefunded();
        } else {
            booking.cancelNoRefund();
        }
        bookings.flush();
        if (early) {
            slotInventory.release(Map.of(booking.getScheduleId(), (int) booking.getSlotsCount()));
        }
        return cancellations.saveAndFlush(BookingCancellation.byTourist(bookingId, touristId, reason,
                hoursBeforeStart, early, touristRefundPct, cancelledAt)).getId();
    }
}