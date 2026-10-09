package com.integrate.modules.notification.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.storage.VoucherDocumentStorage;
import com.integrate.modules.notification.config.NotificationProperties;
import com.integrate.modules.notification.entity.OutboxMessage;
import com.integrate.modules.notification.repository.OutboxMessageRepository;
import com.integrate.modules.notification.sender.EmailSender;
import com.integrate.modules.notification.sender.EmailSender.Attachment;
import com.integrate.modules.notification.sender.EmailSender.EmailMessage;
import com.integrate.modules.notification.sender.MailTemplates;
import com.integrate.modules.notification.sender.MailTemplates.Rendered;
import com.integrate.modules.notification.service.model.EmailRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Despachador del outbox (ADR-0011 §3): reclama un lote con {@code SKIP LOCKED} y lo marca {@code PROCESSING} en una
 * transacción corta, envía fuera de toda transacción y cierra cada mensaje en otra. Entrega "al menos una vez": si la
 * instancia cae después de que el SMTP aceptó y antes del cierre, el mensaje se reenvía con el mismo
 * {@code Message-ID}.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    static final String MESSAGE_ID_DOMAIN = "integrate.co";

    private final OutboxMessageRepository repository;
    private final EmailSender sender;
    private final MailTemplates templates;
    private final VoucherDocumentStorage voucherStorage;
    private final AuditTrail auditTrail;
    private final NotificationProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OutboxDispatcher(OutboxMessageRepository repository, EmailSender sender, MailTemplates templates,
                            VoucherDocumentStorage voucherStorage, AuditTrail auditTrail,
                            NotificationProperties properties, JsonMapper jsonMapper, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sender = sender;
        this.templates = templates;
        this.voucherStorage = voucherStorage;
        this.auditTrail = auditTrail;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** @return mensajes procesados (enviados o reprogramados) en esta pasada */
    public int dispatchOnce() {
        List<OutboxMessage> claimed = transaction.execute(status -> {
            List<OutboxMessage> due = repository.lockDue(clock.instant(), properties.dispatch().batchSize());
            due.forEach(OutboxMessage::claim);
            repository.flush();
            return due;
        });
        for (OutboxMessage message : claimed) {
            deliver(message);
        }
        return claimed.size();
    }

    /** Devuelve a {@code FAILED} los {@code PROCESSING} sin cambios durante más de {@code staleAfter} configurado. */
    public int rescueStale() {
        return rescueStale(properties.dispatch().staleAfter());
    }

    public int rescueStale(Duration staleAfter) {
        return Optional.ofNullable(transaction.execute(status -> {
            List<OutboxMessage> stale = repository.lockStale(staleAfter.toSeconds(), properties.dispatch().batchSize());
            stale.forEach(message -> closeAsFailed(message, "abandoned while PROCESSING"));
            return stale.size();
        })).orElse(0);
    }

    private void deliver(OutboxMessage message) {
        try {
            String providerId = sender.send(compose(message));
            transaction.executeWithoutResult(status -> repository.findById(message.getId()).ifPresent(m -> {
                m.sent(providerId, clock.instant());
                repository.flush();
            }));
            log.info("notification.sent id={} template={} attempt={}", message.getId(), message.getTemplateCode(),
                    message.getAttempts());
        } catch (RuntimeException e) {
            transaction.executeWithoutResult(status -> repository.findById(message.getId())
                    .ifPresent(m -> closeAsFailed(m, e.toString())));
        }
    }

    /** Reprograma con backoff, o {@code DEAD} con auditoría al agotar los intentos. */
    private void closeAsFailed(OutboxMessage message, String error) {
        Instant next = clock.instant().plus(properties.backoff().after(message.getAttempts()));
        message.failed(error, next);
        repository.flush();
        if (message.isDead()) {
            auditTrail.record(new AuditEntry("notification", "notification_outbox", message.getId().toString(),
                    "NOTIFICATION_DEAD", Severity.WARNING, message.getRecipientUserId(), null, null,
                    Map.of("templateCode", message.getTemplateCode(), "attempts", (int) message.getAttempts(),
                            "aggregateId", message.getAggregateId())));
            log.warn("notification.dead id={} template={} attempts={}", message.getId(), message.getTemplateCode(),
                    message.getAttempts());
        } else {
            log.warn("notification.failed id={} attempt={} nextAttemptAt={} cause={}", message.getId(),
                    message.getAttempts(), next, error);
        }
    }

    private EmailMessage compose(OutboxMessage message) {
        Map<String, Object> payload = jsonMapper.readValue(message.getPayload(), new TypeReference<>() { });
        List<EmailRequest.Attachment> refs = jsonMapper.readValue(message.getAttachments(), new TypeReference<>() { });
        Rendered rendered = templates.render(message.getTemplateCode(), payload);
        List<Attachment> attachments = new ArrayList<>();
        for (EmailRequest.Attachment ref : refs) {
            if (!EmailRequest.Attachment.VOUCHERS.equals(ref.bucket())) {
                throw new IllegalStateException("Unsupported attachment bucket " + ref.bucket());
            }
            byte[] content = voucherStorage.get(ref.key())
                    .orElseThrow(() -> new IllegalStateException("Attachment not found in S3: " + ref.key()));
            attachments.add(new Attachment(ref.filename(), ref.contentType(), content));
        }
        return new EmailMessage(messageId(message.getId()), message.getRecipientAddress(), rendered.subject(),
                rendered.textBody(), rendered.htmlBody(), attachments);
    }

    static String messageId(UUID outboxId) {
        return "<" + outboxId + "@" + MESSAGE_ID_DOMAIN + ">";
    }
}
