package com.integrate.modules.payment.gateway;

import com.integrate.common.crypto.HmacSha256;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.payment.config.PaymentProperties;
import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.entity.PaymentProvider;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pasarela simulada del MVP con el modelo canónico de la plataforma (contrato §6). Firma:
 * {@code X-Signature = hex(HMAC_SHA256(secreto, X-Timestamp + "." + raw_body))}, con el secreto decodificado de
 * Base64 y una ventana de ±300 s sobre {@code X-Timestamp}.
 */
@Component
public class MockPaymentGatewayAdapter implements PaymentGatewayAdapter {

    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration tolerance;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public MockPaymentGatewayAdapter(PaymentProperties properties, JsonMapper jsonMapper, Clock clock) {
        PaymentProperties.Mock mock = properties.mock();
        this.secret = mock.configured() ? decode(mock.webhookSecret()) : null;
        this.tolerance = mock.signatureTolerance();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public boolean configured() {
        return secret != null;
    }

    @Override
    public Map<String, Object> start(UUID paymentId, long amountCop, PaymentMethod method) {
        return Map.of("type", PaymentProvider.MOCK.name(), "reference", paymentId.toString());
    }

    @Override
    public VerifiedEvent verify(byte[] rawBody, HttpHeaders headers) {
        String signature = headers.getFirst(SIGNATURE_HEADER);
        String timestamp = headers.getFirst(TIMESTAMP_HEADER);
        if (signature == null || timestamp == null || !timestamp.matches("^[0-9]{1,12}$")) {
            throw new WebhookRejectedException("missing or malformed " + SIGNATURE_HEADER + "/" + TIMESTAMP_HEADER);
        }
        Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        if (Duration.between(signedAt, clock.instant()).abs().compareTo(tolerance) > 0) {
            throw new WebhookRejectedException("timestamp outside the " + tolerance.toSeconds() + " s window");
        }
        byte[] signed = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[signed.length + rawBody.length];
        System.arraycopy(signed, 0, message, 0, signed.length);
        System.arraycopy(rawBody, 0, message, signed.length, rawBody.length);
        if (!Digests.hexEquals(HmacSha256.hex(secret, message), signature)) {
            throw new WebhookRejectedException("signature mismatch");
        }
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        return new VerifiedEvent(parse(payload), signature, payload);
    }

    private CanonicalPaymentEvent parse(String payload) {
        try {
            JsonNode body = jsonMapper.readTree(payload);
            String provider = text(body, "provider");
            if (!PaymentProvider.MOCK.contractName().equals(provider)) {
                throw new BusinessRuleException(ErrorCode.PAYMENT_PROVIDER_MISMATCH,
                        "The event provider does not match the webhook path.");
            }
            GatewayStatus status = GatewayStatus.valueOf(text(body, "status"));
            if (status == GatewayStatus.PENDING) {
                throw malformed();
            }
            String eventId = bounded(text(body, "eventId"));
            JsonNode amount = body.get("amountCop");
            if (amount == null || !amount.canConvertToLong() || !amount.isIntegralNumber()) {
                throw malformed();
            }
            return new CanonicalPaymentEvent(eventId, PaymentProvider.MOCK, bounded(text(body, "transactionId")),
                    UUID.fromString(text(body, "bookingId")), null, amount.asLong(),
                    status, Instant.parse(text(body, "timestamp")), "payment." + status.name().toLowerCase());
        } catch (JacksonException | IllegalArgumentException | DateTimeParseException e) {
            throw malformed();
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw malformed();
        }
        return node.asString();
    }

    private static String bounded(String value) {
        if (value.length() > 100) {
            throw malformed();
        }
        return value;
    }

    private static BusinessRuleException malformed() {
        return new BusinessRuleException(ErrorCode.MALFORMED_REQUEST, "The webhook body is not a valid payment event.");
    }

    /** Fail-fast sin eco del valor (SEC-DIR-004). */
    private static byte[] decode(String base64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PAYMENT_WEBHOOK_SECRET_MOCK is not valid Base64");
        }
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("PAYMENT_WEBHOOK_SECRET_MOCK must decode to at least 32 bytes");
        }
        return key;
    }
}
