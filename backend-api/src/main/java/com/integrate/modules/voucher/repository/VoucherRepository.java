package com.integrate.modules.voucher.repository;

import com.integrate.modules.voucher.entity.Voucher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    Optional<Voucher> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);

    /**
     * Reservas confirmadas sin voucher, para el barrido durable (ADR-0010 §5). Es la única lectura cruzada de esquema
     * del módulo, de solo lectura y documentada en el ADR: un anti-join es la forma correcta de encontrarlas sin que
     * {@code booking} conozca {@code voucher}.
     */
    @Query(value = """
            SELECT b.id FROM booking.bookings b
             WHERE b.status = 'CONFIRMED'
               AND NOT EXISTS (SELECT 1 FROM voucher.vouchers v WHERE v.booking_id = b.id)
             ORDER BY b.confirmed_at
             LIMIT :limit""", nativeQuery = true)
    List<UUID> findConfirmedBookingsWithoutVoucher(@Param("limit") int limit);

    /** Vouchers emitidos cuyo PDF falta (fallo de S3 o reinicio entre la emisión y el PDF). */
    @Query(value = """
            SELECT v.id FROM voucher.vouchers v
             WHERE v.status = 'ISSUED' AND v.pdf_s3_key IS NULL
             ORDER BY v.created_at
             LIMIT :limit""", nativeQuery = true)
    List<UUID> findIssuedWithoutPdf(@Param("limit") int limit);

    /** Canje en campo (ADR-0032 §4): solo un voucher {@code ISSUED} pasa a {@code CONSUMED}; devuelve las filas tocadas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE voucher.vouchers SET status = 'CONSUMED', consumed_at = :at
             WHERE id = :id AND status = 'ISSUED'""", nativeQuery = true)
    int consume(@Param("id") UUID id, @Param("at") java.time.Instant at);

    /** Idempotente: dos generadores concurrentes escriben la misma clave y solo el primero la fija. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE voucher.vouchers SET pdf_s3_key = :key WHERE id = :id AND pdf_s3_key IS NULL",
            nativeQuery = true)
    int attachPdf(@Param("id") UUID id, @Param("key") String key);
}
