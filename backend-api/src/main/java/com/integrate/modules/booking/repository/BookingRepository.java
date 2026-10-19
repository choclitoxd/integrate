package com.integrate.modules.booking.repository;

import com.integrate.modules.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Experiencias próximas del turista (ADR-0023 §1): {@code CONFIRMED} o {@code PENDING} aún pagable cuya franja no
     * ha terminado, la más próxima primero. Lee {@code catalog.schedules} en solo lectura para ordenar por la fecha
     * de la franja en la base (paginar antes de ordenar daría páginas incorrectas).
     */
    @Query(value = """
            SELECT b.id AS id, b.status AS status, b.schedule_id AS scheduleId, b.slots_count AS slots,
                   b.total_amount_cop AS totalAmountCop
              FROM booking.bookings b JOIN catalog.schedules s ON s.id = b.schedule_id
             WHERE b.tourist_id = :touristId AND s.end_time > :now
               AND (b.status = 'CONFIRMED' OR (b.status = 'PENDING' AND b.expires_at > :now))
             ORDER BY s.start_time ASC, b.id""",
            countQuery = """
            SELECT count(*) FROM booking.bookings b JOIN catalog.schedules s ON s.id = b.schedule_id
             WHERE b.tourist_id = :touristId AND s.end_time > :now
               AND (b.status = 'CONFIRMED' OR (b.status = 'PENDING' AND b.expires_at > :now))""",
            nativeQuery = true)
    Page<BookingHistoryRow> findUpcoming(@Param("touristId") UUID touristId, @Param("now") Instant now,
                                         Pageable pageable);

    /** El complemento de {@link #findUpcoming}: realizadas, pasadas, canceladas y fallidas, la más reciente primero. */
    @Query(value = """
            SELECT b.id AS id, b.status AS status, b.schedule_id AS scheduleId, b.slots_count AS slots,
                   b.total_amount_cop AS totalAmountCop
              FROM booking.bookings b JOIN catalog.schedules s ON s.id = b.schedule_id
             WHERE b.tourist_id = :touristId
               AND NOT (s.end_time > :now
                        AND (b.status = 'CONFIRMED' OR (b.status = 'PENDING' AND b.expires_at > :now)))
             ORDER BY s.start_time DESC, b.id""",
            countQuery = """
            SELECT count(*) FROM booking.bookings b JOIN catalog.schedules s ON s.id = b.schedule_id
             WHERE b.tourist_id = :touristId
               AND NOT (s.end_time > :now
                        AND (b.status = 'CONFIRMED' OR (b.status = 'PENDING' AND b.expires_at > :now)))""",
            nativeQuery = true)
    Page<BookingHistoryRow> findPast(@Param("touristId") UUID touristId, @Param("now") Instant now, Pageable pageable);

    /** Fila del historial: solo lo necesario para la tarjeta, sin datos de viajeros. */
    interface BookingHistoryRow {
        UUID getId();

        String getStatus();

        UUID getScheduleId();

        Short getSlots();

        java.math.BigDecimal getTotalAmountCop();
    }

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
