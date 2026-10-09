package com.integrate.modules.notification.sender;

import com.integrate.modules.notification.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP con {@link JavaMailSender} (ADR-0011 §4): {@code multipart/alternative} con texto plano y HTML, adjuntos y un
 * {@code Message-ID} determinista. Los mensajes de error nunca incluyen credenciales: solo el tipo y el mensaje del
 * servidor.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mailFrom();
    }

    @Override
    public String send(EmailMessage message) {
        try {
            MimeMessage mime = new FixedIdMimeMessage(mailSender.createMimeMessage().getSession(), message.messageId());
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            for (Attachment attachment : message.attachments()) {
                helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.send(mime);
            return message.messageId();
        } catch (MessagingException | MailException e) {
            throw new EmailDeliveryException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** {@code updateMessageID} se invoca al guardar el mensaje: se sobrescribe para conservar el identificador fijo. */
    private static final class FixedIdMimeMessage extends MimeMessage {

        private final String messageId;

        FixedIdMimeMessage(Session session, String messageId) {
            super(session);
            this.messageId = messageId;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", messageId);
        }
    }
}
