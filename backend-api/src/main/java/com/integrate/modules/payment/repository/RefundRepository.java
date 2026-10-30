package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.Refund;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    /** Órdenes abiertas, las más antiguas primero; una {@code PROCESSING} se reintenta (la pasarela es idempotente). */
    @Query(value = """
            SELECT id FROM payments.refunds WHERE status IN ('REQUESTED', 'PROCESSING')
             ORDER BY created_at LIMIT :limit""", nativeQuery = true)
    List<UUID> findOpenIds(@Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);
}