package com.integrate.modules.booking.repository;

import com.integrate.modules.booking.entity.BookingParticipant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingParticipantRepository extends JpaRepository<BookingParticipant, Long> {

    List<BookingParticipant> findByBookingIdOrderByIdAsc(UUID bookingId);
}
