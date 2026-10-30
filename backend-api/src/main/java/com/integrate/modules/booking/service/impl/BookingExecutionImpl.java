package com.integrate.modules.booking.service.impl;

import com.integrate.common.persistence.StatusReasonContext;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingExecution;
import com.integrate.modules.booking.service.model.BookingExecutedEvent;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class BookingExecutionImpl implements BookingExecution {

    static final String REASON_NO_SHOW = "NO_SHOW_AFTER_GRACE";

    private final BookingRepository repository;
    private final StatusReasonContext statusReason;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markExecuted(UUID bookingId) {
        repository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " does not exist"))
                .execute();
        repository.flush();
        events.publishEvent(new BookingExecutedEvent(bookingId, clock.instant()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markNoShow(UUID bookingId) {
        statusReason.set(REASON_NO_SHOW);
        repository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " does not exist"))
                .noShow();
        repository.flush();
    }
}