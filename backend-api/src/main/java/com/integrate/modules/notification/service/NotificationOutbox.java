package com.integrate.modules.notification.service;

import com.integrate.modules.notification.service.model.EmailRequest;
import java.util.UUID;

/**
 * Encolado transaccional de notificaciones (ADR-0011 §2). Interfaz pública de {@code notification}: el mensaje se
 * inserta en la transacción de quien llama ({@code propagation = MANDATORY}), así que se confirma o se revierte junto
 * con el cambio de negocio que lo origina. El envío lo hace después el despachador.
 */
public interface NotificationOutbox {

    /** @return id del mensaje en {@code notification.notification_outbox} */
    UUID enqueueEmail(EmailRequest request);
}
