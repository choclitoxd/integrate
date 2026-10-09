package com.integrate.modules.booking.repository;

import com.integrate.modules.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Control de idempotencia del lock (índice único {@code uq_bookings_idempotency}). */
    Optional<Booking> findByTouristIdAndIdempotencyKey(UUID touristId, UUID idempotencyKey);

    /** Serializa las transiciones originadas por el pago con el worker TTL (ADR-0009 §4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /** Lectura por su dueño: una reserva ajena se comporta como inexistente. */
    Optional<Booking> findByIdAndTouristId(UUID id, UUID touristId);

    /**
     * Lote de reservas {@code PENDING} vencidas para el worker TTL (RF-11). {@code SKIP LOCKED}: varias instancias
     * toman lotes disjuntos y no esperan a un webhook que esté procesando la misma reserva. Usa el índice parcial
     * {@code ix_bookings_pending_expiry}.
     */
    @Query(value = """
            SELECT * FROM booking.bookings
             WHERE status = 'PENDING' AND expires_at < :now
             ORDER BY expires_at
             LIMIT :limit
               FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<Booking> lockExpiredPending(@Param("now") Instant now, @Param("limit") int limit);
}
