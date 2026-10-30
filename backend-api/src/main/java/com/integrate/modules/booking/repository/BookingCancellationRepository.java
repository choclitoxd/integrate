package com.integrate.modules.booking.repository;

import com.integrate.modules.booking.entity.BookingCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingCancellationRepository extends JpaRepository<BookingCancellation, Long> {
}