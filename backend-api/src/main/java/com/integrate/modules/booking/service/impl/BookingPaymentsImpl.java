package com.integrate.modules.booking.service.impl;

import com.integrate.common.persistence.StatusReasonContext;
import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.booking.service.model.PayableBooking;
import com.integrate.modules.catalog.service.SlotInventory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingPaymentsImpl implements BookingPayments {

    static final String REASON_APPROVED = "PAYMENT_APPROVED";
    static final String REASON_DECLINED = "PAYMENT_DECLINED";
    static final String REASON_VOIDED = "PAYMENT_VOIDED";

    private final BookingRepository bookingRepository;
    private final SlotInventory slotInventory;
    private final StatusReasonContext statusReason;

    @Override
    @Transactional(readOnly = true)
    public Optional<PayableBooking> find(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(BookingPaymentsImpl::view);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PayableBooking> lockForPayment(UUID bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId).map(BookingPaymentsImpl::view);
    }

    private static PayableBooking view(Booking b) {
        return new PayableBooking(b.getId(), b.getTouristId(), b.getScheduleId(), b.getStatus().name(),
                b.getSlotsCount(), b.getTotalAmountCop(), b.getExpiresAt());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(UUID bookingId) {
        transition(bookingId, REASON_APPROVED, Booking::confirm, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void failPayment(UUID bookingId) {
        transition(bookingId, REASON_DECLINED, Booking::failPayment, true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void voidPending(UUID bookingId) {
        transition(bookingId, REASON_VOIDED, Booking::voidPending, true);
    }

    /** La reserva ya está bloqueada por {@link #lockForPayment}: el {@code findById} la toma del contexto. */
    private void transition(UUID bookingId, String reason, Consumer<Booking> change, boolean releaseSlots) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " not found"));
        statusReason.set(reason);
        change.accept(booking);
        bookingRepository.flush();
        if (releaseSlots) {
            slotInventory.release(Map.of(booking.getScheduleId(), (int) booking.getSlotsCount()));
        }
    }
}
