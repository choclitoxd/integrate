package com.integrate.modules.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.modules.payment.config.PaymentProperties;
import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.entity.PaymentProvider;
import com.integrate.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

/** Firma de integridad, checksum nativo, rotación y ventana de reintentos de Wompi (ADR-0009 §3-§4). */
class WompiPaymentGatewayAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String EVENTS = "test_events_actual";
    private static final String PREVIOUS = "test_events_anterior";
    private static final String TX_PROPS = "[\"transaction.id\",\"transaction.status\",\"transaction.amount_in_cents\"]";

    private final MutableClock clock = new MutableClock(NOW);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void integritySignatureFollowsTheWidgetFormulaAndIsRecomputedPerCall() {
        UUID paymentId = UUID.randomUUID();
        Map<String, Object> checkout = adapter("integridad-1", null).start(paymentId, 160_000, PaymentMethod.PSE);

        assertThat(checkout).containsEntry("type", "WOMPI").containsEntry("amountInCents", 16_000_000L)
                .containsEntry("reference", paymentId.toString()).containsEntry("currency", "COP");
        assertThat(checkout.get("integritySignature"))
                .isEqualTo(Digests.sha256Hex(paymentId + "16000000" + "COP" + "integridad-1"));
        // Tras rotar el secreto de integridad, el mismo intento recibe una firma distinta (no se guarda).
        assertThat(adapter("integridad-2", null).start(paymentId, 160_000, PaymentMethod.PSE).get("integritySignature"))
                .isNotEqualTo(checkout.get("integritySignature"));
    }

    @Test
    void validChecksumIsTranslatedToTheCanonicalModel() {
        String body = event("tx-1", "APPROVED", 16_000_000, NOW.getEpochSecond(), EVENTS, TX_PROPS, true);

        VerifiedEvent verified = adapter("i", null).verify(bytes(body), new HttpHeaders());

        CanonicalPaymentEvent event = verified.event();
        assertThat(event.provider()).isEqualTo(PaymentProvider.WOMPI);
        assertThat(event.transactionId()).isEqualTo("tx-1");
        assertThat(event.amountCop()).isEqualTo(160_000);
        assertThat(event.status()).isEqualTo(GatewayStatus.APPROVED);
        assertThat(event.bookingId()).isNull();
        assertThat(event.paymentReference()).isEqualTo("ref-1");
        assertThat(event.eventId()).isEqualTo(Digests.sha256Hex(verified.signature()));
    }

    @Test
    void propertiesAreReadInTheOrderTheEventDeclares() {
        String reversed = "[\"transaction.amount_in_cents\",\"transaction.status\",\"transaction.id\"]";
        String body = event("tx-2", "DECLINED", 500_000, NOW.getEpochSecond(), EVENTS, reversed, false);

        assertThat(adapter("i", null).verify(bytes(body), new HttpHeaders()).event().status())
                .isEqualTo(GatewayStatus.DECLINED);
    }

    @Test
    void wrongSecretOrTamperedBodyIsRejected() {
        String body = event("tx-3", "APPROVED", 16_000_000, NOW.getEpochSecond(), "otro-secreto", TX_PROPS, false);
        assertThatThrownBy(() -> adapter("i", null).verify(bytes(body), new HttpHeaders()))
                .isInstanceOf(WebhookRejectedException.class);

        String tampered = event("tx-3", "APPROVED", 16_000_000, NOW.getEpochSecond(), EVENTS, TX_PROPS, false)
                .replace("\"amount_in_cents\":16000000", "\"amount_in_cents\":100");
        assertThatThrownBy(() -> adapter("i", null).verify(bytes(tampered), new HttpHeaders()))
                .isInstanceOf(WebhookRejectedException.class);
    }

    @Test
    void previousSecretIsAcceptedDuringRotation() {
        String body = event("tx-4", "APPROVED", 16_000_000, NOW.getEpochSecond(), PREVIOUS, TX_PROPS, false);

        assertThatThrownBy(() -> adapter("i", null).verify(bytes(body), new HttpHeaders()))
                .isInstanceOf(WebhookRejectedException.class);
        assertThat(adapter("i", PREVIOUS).verify(bytes(body), new HttpHeaders()).event().transactionId())
                .isEqualTo("tx-4");
    }

    @Test
    void retriesWithinTwentyFiveHoursAreAcceptedAndOlderOnesRejected() {
        long sent = NOW.getEpochSecond();
        String body = event("tx-5", "APPROVED", 16_000_000, sent, EVENTS, TX_PROPS, false);

        clock.set(NOW.plus(Duration.ofHours(3)));
        assertThat(adapter("i", null).verify(bytes(body), new HttpHeaders()).event().transactionId()).isEqualTo("tx-5");
        clock.set(NOW.plus(Duration.ofHours(24)));
        assertThat(adapter("i", null).verify(bytes(body), new HttpHeaders()).event().transactionId()).isEqualTo("tx-5");
        clock.set(NOW.plus(Duration.ofHours(26)));
        assertThatThrownBy(() -> adapter("i", null).verify(bytes(body), new HttpHeaders()))
                .isInstanceOf(WebhookRejectedException.class);
        clock.set(NOW.minus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> adapter("i", null).verify(bytes(body), new HttpHeaders()))
                .as("timestamp más de 5 min en el futuro")
                .isInstanceOf(WebhookRejectedException.class);
    }

    @Test
    void nonTransactionEventsAreIgnoredAndCentsMustBeWholePesos() {
        String token = event("tx-6", "APPROVED", 16_000_000, NOW.getEpochSecond(), EVENTS, TX_PROPS, false)
                .replace("transaction.updated", "nequi_token.updated");
        assertThatThrownBy(() -> adapter("i", null).verify(bytes(token), new HttpHeaders()))
                .isInstanceOf(IgnoredEventException.class);

        String cents = event("tx-7", "APPROVED", 16_000_050, NOW.getEpochSecond(), EVENTS, TX_PROPS, false);
        assertThatThrownBy(() -> adapter("i", null).verify(bytes(cents), new HttpHeaders()))
                .isInstanceOf(BusinessRuleException.class);
    }

    private WompiPaymentGatewayAdapter adapter(String integritySecret, String previousEventsSecret) {
        PaymentProperties properties = new PaymentProperties(PaymentProvider.WOMPI, null,
                new PaymentProperties.Wompi("pub_test_x", integritySecret, EVENTS, previousEventsSecret, null), null);
        return new WompiPaymentGatewayAdapter(properties, json, clock);
    }

    /** Evento nativo con el checksum de Wompi sobre las propiedades declaradas, en el orden declarado. */
    private String event(String txId, String status, long cents, long timestamp, String secret, String properties,
                         boolean upperCase) {
        Map<String, String> values = Map.of("transaction.id", txId, "transaction.status", status,
                "transaction.amount_in_cents", Long.toString(cents));
        StringBuilder signed = new StringBuilder();
        for (String property : json.readValue(properties, String[].class)) {
            signed.append(values.get(property));
        }
        String checksum = Digests.sha256Hex(signed.toString() + timestamp + secret);
        checksum = upperCase ? checksum.toUpperCase(Locale.ROOT) : checksum;
        return """
                {"event":"transaction.updated","environment":"test","timestamp":%d,
                 "data":{"transaction":{"id":"%s","reference":"ref-1","amount_in_cents":%d,"currency":"COP",
                         "status":"%s","payment_method_type":"PSE"}},
                 "signature":{"properties":%s,"checksum":"%s"},"sent_at":"2026-10-05T12:00:00Z"}"""
                .formatted(timestamp, txId, cents, status, properties, checksum);
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
