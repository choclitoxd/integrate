package com.integrate.modules.notification.sender;

import java.util.List;

/** Transporte de correo (ADR-0011 §4). La implementación SMTP sirve para Mailpit, Gmail SMTP o un relé de producción. */
public interface EmailSender {

    /**
     * @return {@code Message-ID} entregado al servidor
     * @throws EmailDeliveryException si el servidor no aceptó el mensaje (se reintenta con backoff)
     */
    String send(EmailMessage message);

    /**
     * @param messageId {@code Message-ID} determinista ({@code <outboxId@dominio>}): un reenvío tras una caída lleva el
     *                  mismo identificador y el cliente de correo puede detectarlo
     */
    record EmailMessage(String messageId, String to, String subject, String textBody, String htmlBody,
                        List<Attachment> attachments) {

        public EmailMessage {
            attachments = List.copyOf(attachments);
        }
    }

    record Attachment(String filename, String contentType, byte[] content) {
    }

    class EmailDeliveryException extends RuntimeException {

        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
