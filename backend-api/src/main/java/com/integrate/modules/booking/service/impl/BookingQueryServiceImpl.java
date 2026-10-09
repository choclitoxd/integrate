package com.integrate.modules.booking.service.impl;

import com.integrate.modules.booking.dto.response.BookingLockResponse;
import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingQueryService;
import com.integrate.modules.catalog.service.SlotInventory;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingQueryServiceImpl implements BookingQueryService {

    private final BookingRepository bookingRepository;
    private final SlotInventory slotInventory;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public BookingLockResponse get(UUID touristId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndTouristId(bookingId, touristId)
                .orElseThrow(BookingErrors::notFound);
        UUID experienceId = slotInventory.experienceOf(booking.getScheduleId())
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " has no schedule"));
        return BookingLockResponse.from(booking, experienceId, clock.instant());
    }
}
