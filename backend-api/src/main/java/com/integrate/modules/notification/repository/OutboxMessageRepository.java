package com.integrate.modules.notification.repository;

import com.integrate.modules.notification.entity.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * Lote listo para enviar (índice {@code ix_outbox_dispatch}). {@code SKIP LOCKED}: varios despachadores toman
     * lotes disjuntos sin esperarse.
     */
    @Query(value = """
            SELECT * FROM notification.notification_outbox
             WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at <= :now
             ORDER BY next_attempt_at
             LIMIT :limit
               FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxMessage> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * {@code PROCESSING} abandonados por una instancia caída entre el reclamo y el cierre. Compara con el reloj de la
     * base porque {@code updated_at} lo fija su trigger.
     */
    @Query(value = """
            SELECT * FROM notification.notification_outbox
             WHERE status = 'PROCESSING' AND updated_at < now() - make_interval(secs => :staleSeconds)
             LIMIT :limit
               FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxMessage> lockStale(@Param("staleSeconds") long staleSeconds, @Param("limit") int limit);

    List<OutboxMessage> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);
}
