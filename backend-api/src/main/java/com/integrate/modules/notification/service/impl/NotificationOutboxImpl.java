package com.integrate.modules.notification.service.impl;

import com.integrate.modules.notification.entity.OutboxMessage;
import com.integrate.modules.notification.repository.OutboxMessageRepository;
import com.integrate.modules.notification.service.NotificationOutbox;
import com.integrate.modules.notification.service.model.EmailRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class NotificationOutboxImpl implements NotificationOutbox {

    /** {@code DEFAULT 8} de la columna: con el backoff de 1 min ×2 cubre unas 4 h de caída del SMTP. */
    static final int MAX_ATTEMPTS = 8;

    private final OutboxMessageRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueEmail(EmailRequest request) {
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(OutboxMessage.email(id, request.aggregateType(), request.aggregateId(),
                request.eventType(), request.recipientUserId(), request.recipientAddress(), request.templateCode(),
                jsonMapper.writeValueAsString(request.payload()), jsonMapper.writeValueAsString(request.attachments()),
                MAX_ATTEMPTS, clock.instant()));
        return id;
    }
}
