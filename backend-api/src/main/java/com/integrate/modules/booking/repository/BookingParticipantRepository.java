package com.integrate.modules.booking.repository;

import com.integrate.modules.booking.entity.BookingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingParticipantRepository extends JpaRepository<BookingParticipant, Long> {
}
