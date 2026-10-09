package com.integrate.modules.booking.service.impl;

import com.integrate.modules.booking.entity.BookingParticipant;
import com.integrate.modules.booking.repository.BookingParticipantRepository;
import com.integrate.modules.booking.repository.BookingRepository;
import com.integrate.modules.booking.service.BookingVouchers;
import com.integrate.modules.booking.service.model.VoucherSubject;
import com.integrate.modules.booking.service.model.VoucherSubject.Holder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingVouchersImpl implements BookingVouchers {

    private final BookingRepository bookingRepository;
    private final BookingParticipantRepository participantRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<VoucherSubject> subjectOf(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(booking -> {
            // Orden estable: titular primero y luego el orden de registro.
            List<BookingParticipant> participants = participantRepository.findByBookingIdOrderByIdAsc(bookingId)
                    .stream()
                    .sorted(Comparator.comparing(BookingParticipant::isHolder).reversed())
                    .toList();
            BookingParticipant holder = participants.stream().filter(BookingParticipant::isHolder).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " has no holder"));
            return new VoucherSubject(bookingId, booking.getTouristId(), booking.getStatus().name(),
                    booking.getScheduleId(), booking.getSlotsCount(), booking.getTotalAmountCop(),
                    new Holder(holder.getFullName(), holder.getDocType().name(), holder.getDocNumber()),
                    participants.stream().map(BookingParticipant::getFullName).toList());
        });
    }
}
