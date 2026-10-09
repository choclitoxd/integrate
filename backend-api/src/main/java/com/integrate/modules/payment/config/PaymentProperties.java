package com.integrate.modules.payment.config;

import com.integrate.modules.payment.entity.PaymentProvider;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pasarela de pagos (ADR-0009 §7). SEC-DIR-004: los secretos llegan por variables de entorno y nunca se repiten en
 * mensajes de error. Cada adaptador solo se activa si su secreto está configurado; el proveedor activo debe estarlo
 * o el arranque aborta.
 *
 * @param provider proveedor de los cobros nuevos ({@code MOCK} en {@code dev}/{@code test}, {@code WOMPI} en despliegue)
 */
@ConfigurationProperties("integrate.payments")
public record PaymentProperties(PaymentProvider provider, Mock mock, Wompi wompi) {

    public PaymentProperties {
        Objects.requireNonNull(provider, "integrate.payments.provider (PAYMENT_PROVIDER) is required");
        mock = mock == null ? new Mock(null, null) : mock;
        wompi = wompi == null ? new Wompi(null, null, null, null, null) : wompi;
    }

    /**
     * @param webhookSecret      HMAC de {@code /webhook/mock}: ≥ 32 bytes en Base64 ({@code PAYMENT_WEBHOOK_SECRET_MOCK})
     * @param signatureTolerance ventana de {@code X-Timestamp} (±300 s del contrato)
     */
    public record Mock(String webhookSecret, Duration signatureTolerance) {

        public Mock {
            signatureTolerance = signatureTolerance == null ? Duration.ofMinutes(5) : signatureTolerance;
        }

        public boolean configured() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }
    }

    /**
     * @param eventsSecretPrevious secreto de eventos anterior, solo durante una rotación (ADR-0009 §4)
     * @param maxEventAge          antigüedad máxima del {@code timestamp} del evento: cubre los reintentos de Wompi
     *                             (30 min, 3 h y 24 h) con margen
     */
    public record Wompi(String publicKey, String integritySecret, String eventsSecret, String eventsSecretPrevious,
                        Duration maxEventAge) {

        public Wompi {
            maxEventAge = maxEventAge == null ? Duration.ofHours(25) : maxEventAge;
        }

        public boolean configured() {
            return notBlank(publicKey) && notBlank(integritySecret) && notBlank(eventsSecret);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
