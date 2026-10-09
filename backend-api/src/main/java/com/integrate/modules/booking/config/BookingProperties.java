package com.integrate.modules.booking.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del motor de reservas (ADR-0008 §8). Sin secretos; los valores por defecto viven en
 * {@code application.yml}.
 *
 * @param holdTtl             vigencia de una reserva {@code PENDING} antes de pagar (RF-09/RF-11)
 * @param idempotencyWindow   ventana en la que un reintento con la misma clave reproduce la reserva
 * @param expiration          worker TTL (RF-11)
 */
@ConfigurationProperties("integrate.booking")
public record BookingProperties(Duration holdTtl, Duration idempotencyWindow, Expiration expiration) {

    public BookingProperties {
        Objects.requireNonNull(holdTtl, "integrate.booking.hold-ttl");
        Objects.requireNonNull(idempotencyWindow, "integrate.booking.idempotency-window");
        Objects.requireNonNull(expiration, "integrate.booking.expiration");
    }

    /**
     * @param enabled   programa el worker; en las pruebas se apaga y se invoca a mano
     * @param interval  espera entre ejecuciones ({@code fixedDelay})
     * @param batchSize reservas por transacción
     */
    public record Expiration(boolean enabled, Duration interval, int batchSize) {

        public Expiration {
            if (batchSize < 1) {
                throw new IllegalArgumentException("integrate.booking.expiration.batch-size must be >= 1");
            }
        }
    }
}
