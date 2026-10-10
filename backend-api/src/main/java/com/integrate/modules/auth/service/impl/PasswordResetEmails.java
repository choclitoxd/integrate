package com.integrate.modules.auth.service.impl;

import com.integrate.common.web.FrontendProperties;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.notification.service.NotificationOutbox;
import com.integrate.modules.notification.service.model.EmailRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Correos de la recuperación de contraseña (ADR-0014 §3-§4), encolados en el outbox dentro de la transacción del
 * invocador. El enlace lleva el token en el fragmento: nunca llega al servidor que sirve la PWA.
 */
@Component
@RequiredArgsConstructor
class PasswordResetEmails {

    static final String REQUESTED_EVENT = "PASSWORD_RESET_REQUESTED";
    static final String REQUESTED_TEMPLATE = "password-reset";
    static final String CHANGED_EVENT = "PASSWORD_CHANGED";
    static final String CHANGED_TEMPLATE = "password-changed";
    static final String RESET_PATH = "/restablecer-contrasena#token=";
    private static final String AGGREGATE = "user";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu, HH:mm", Locale.forLanguageTag("es-CO"));

    private final NotificationOutbox outbox;
    private final FrontendProperties frontend;

    void enqueueResetLink(User user, String token, Duration ttl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullName", user.getFullName());
        payload.put("resetUrl", frontend.link(RESET_PATH + token));
        payload.put("ttlMinutes", ttl.toMinutes());
        outbox.enqueueEmail(new EmailRequest(AGGREGATE, user.getId().toString(), REQUESTED_EVENT, user.getId(),
                user.getEmail(), REQUESTED_TEMPLATE, payload, List.of()));
    }

    void enqueueChangedNotice(User user, Instant changedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullName", user.getFullName());
        payload.put("changedAt", WHEN.format(changedAt.atZone(BOGOTA)));
        payload.put("recoverUrl", frontend.link("/recuperar-contrasena"));
        outbox.enqueueEmail(new EmailRequest(AGGREGATE, user.getId().toString(), CHANGED_EVENT, user.getId(),
                user.getEmail(), CHANGED_TEMPLATE, payload, List.of()));
    }
}
