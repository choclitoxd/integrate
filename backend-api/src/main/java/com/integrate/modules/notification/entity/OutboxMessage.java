package com.integrate.modules.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Mensaje del transactional outbox ({@code notification.notification_outbox}, I4, ADR-0011). {@code payload} y
 * {@code attachments} se guardan como JSON en texto: los escribe y los lee solo este módulo.
 */
@Entity
@Table(name = "notification_outbox", schema = "notification")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String DEAD = "DEAD";
    public static final String EMAIL = "EMAIL";
    private static final int MAX_ERROR = 1000;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(nullable = false, length = 10, updatable = false)
    private String channel;

    @Column(name = "recipient_user_id", updatable = false)
    private UUID recipientUserId;

    @Column(name = "recipient_address", nullable = false, length = 254, updatable = false)
    private String recipientAddress;

    @Column(name = "template_code", nullable = false, length = 60, updatable = false)
    private String templateCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String attachments;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private short maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "provider_message_id", length = 150)
    private String providerMessageId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static OutboxMessage email(UUID id, String aggregateType, String aggregateId, String eventType,
                                      UUID recipientUserId, String recipientAddress, String templateCode,
                                      String payloadJson, String attachmentsJson, int maxAttempts, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.id = id;
        message.aggregateType = aggregateType;
        message.aggregateId = aggregateId;
        message.eventType = eventType;
        message.channel = EMAIL;
        message.recipientUserId = recipientUserId;
        message.recipientAddress = recipientAddress;
        message.templateCode = templateCode;
        message.payload = payloadJson;
        message.attachments = attachmentsJson;
        message.status = PENDING;
        message.maxAttempts = (short) maxAttempts;
        message.nextAttemptAt = now;
        return message;
    }

    /** Reclamado por un despachador: cuenta el intento antes de enviar. */
    public void claim() {
        this.status = PROCESSING;
        this.attempts++;
    }

    public void sent(String providerMessageId, Instant now) {
        this.status = SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = now;
        this.lastError = null;
    }

    /** Error de envío: reintento en {@code nextAttemptAt}, o {@code DEAD} si se agotaron los intentos. */
    public void failed(String error, Instant nextAttemptAt) {
        this.lastError = error == null || error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
        if (attempts >= maxAttempts) {
            this.status = DEAD;
        } else {
            this.status = FAILED;
            this.nextAttemptAt = nextAttemptAt;
        }
    }

    /** Rescate de un {@code PROCESSING} abandonado por una instancia caída. */
    public void abandon(Instant now) {
        failed("abandoned while PROCESSING; retried by the stale sweep", now);
    }

    public boolean isDead() {
        return DEAD.equals(status);
    }
}
