package com.integrate.modules.notification.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Despacho de notificaciones (ADR-0011 §3, §7). El servidor SMTP se configura en {@code spring.mail.*}; aquí van el
 * remitente y el ritmo del despachador.
 *
 * @param mailFrom remitente de los correos ({@code MAIL_FROM}); obligatorio
 */
@ConfigurationProperties("integrate.notification")
public record NotificationProperties(String mailFrom, Dispatch dispatch, Backoff backoff) {

    public NotificationProperties {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("integrate.notification.mail-from is required (MAIL_FROM)");
        }
        dispatch = Objects.requireNonNullElse(dispatch,
                new Dispatch(true, Duration.ofSeconds(15), 20, Duration.ofMinutes(10)));
        backoff = Objects.requireNonNullElse(backoff, new Backoff(Duration.ofMinutes(1), Duration.ofHours(6)));
    }

    /**
     * @param staleAfter un {@code PROCESSING} sin cambios durante este tiempo se considera de una instancia caída
     */
    public record Dispatch(boolean enabled, Duration interval, int batchSize, Duration staleAfter) {
    }

    /** Espera entre intentos: {@code initial × 2^(intento − 1)}, con tope {@code max}. */
    public record Backoff(Duration initial, Duration max) {

        public Duration after(int attempt) {
            long factor = 1L << Math.min(Math.max(attempt - 1, 0), 20);
            Duration wait = initial.multipliedBy(factor);
            return wait.compareTo(max) > 0 ? max : wait;
        }
    }
}
