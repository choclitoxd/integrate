package com.integrate.modules.booking.service.impl;

import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingConflicts;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class BookingConflictsImpl implements BookingConflicts {

    private final BookingRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markSyncConflict(UUID bookingId) {
        repository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " does not exist"))
                .syncConflict();
    }
}