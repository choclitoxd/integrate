package com.integrate.modules.payment.repository;

import com.integrate.modules.payment.entity.PaymentProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Bandeja de entrada {@code payments.payment_webhook_events} (ADR-0009 §4). Solo se guardan eventos con firma válida:
 * guardar uno inválido permitiría ocupar la clave única de un evento legítimo.
 */
@Repository
public class WebhookEventStore {

    private static final int MAX_ERROR = 1000;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * {@code INSERT … ON CONFLICT (provider, idempotency_key) DO NOTHING}. Un evento concurrente con la misma clave
     * espera el {@code COMMIT} del primero y no inserta.
     *
     * @return id de la fila nueva, o vacío si el evento ya existía (duplicado)
     */
    public Optional<Long> insertIfAbsent(PaymentProvider provider, String eventId, String eventType,
                                         String gatewayStatus, String signature, String payloadJson) {
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery("""
                        INSERT INTO payments.payment_webhook_events
                               (provider, idempotency_key, event_type, gateway_status, signature, signature_valid, payload)
                        VALUES (:provider, :eventId, :eventType, :gatewayStatus, :signature, TRUE, CAST(:payload AS jsonb))
                        ON CONFLICT (provider, idempotency_key) DO NOTHING
                        RETURNING id""")
                .setParameter("provider", provider.name())
                .setParameter("eventId", eventId)
                .setParameter("eventType", eventType)
                .setParameter("gatewayStatus", gatewayStatus)
                .setParameter("signature", signature)
                .setParameter("payload", payloadJson)
                .getResultList();
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst().longValue());
    }

    /** Cierra el evento: pago al que se aplicó y, si no tuvo efectos, el motivo. */
    public void markProcessed(long eventRowId, UUID paymentId, String processingError) {
        entityManager.createNativeQuery("""
                        UPDATE payments.payment_webhook_events
                           SET processed_at = now(), payment_id = :paymentId, processing_error = :error
                         WHERE id = :id""")
                .setParameter("paymentId", paymentId)
                .setParameter("error", processingError == null || processingError.length() <= MAX_ERROR
                        ? processingError : processingError.substring(0, MAX_ERROR))
                .setParameter("id", eventRowId)
                .executeUpdate();
    }

    /** Pago al que se aplicó un evento ya recibido (respuesta de un duplicado). */
    public Optional<UUID> paymentOf(PaymentProvider provider, String eventId) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery("""
                        SELECT payment_id FROM payments.payment_webhook_events
                         WHERE provider = :provider AND idempotency_key = :eventId AND payment_id IS NOT NULL""", UUID.class)
                .setParameter("provider", provider.name())
                .setParameter("eventId", eventId)
                .getResultList();
        return ids.stream().findFirst();
    }
}
