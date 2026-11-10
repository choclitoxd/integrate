package com.integrate.modules.booking.service.impl;

import com.integrate.modules.booking.dto.response.BookingLockResponse;
import com.integrate.modules.booking.dto.response.MeetingPointResponse;
import com.integrate.modules.booking.entity.Booking;
import com.integrate.modules.booking.entity.BookingStatus;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingQueryService;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.catalog.service.SlotInventory;
import com.integrate.modules.catalog.service.model.OperationalLocation;
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
    private final ScheduleDirectory scheduleDirectory;
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

    @Override
    @Transactional(readOnly = true)
    public MeetingPointResponse meetingPoint(UUID touristId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndTouristId(bookingId, touristId)
                .orElseThrow(BookingErrors::notFound);
        // Solo la reserva confirmada da acceso al punto exacto; el resto responde como inexistente.
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw BookingErrors.notFound();
        }
        OperationalLocation location = scheduleDirectory.operationalLocation(booking.getScheduleId())
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " has no schedule"));
        return MeetingPointResponse.from(location);
    }
}
