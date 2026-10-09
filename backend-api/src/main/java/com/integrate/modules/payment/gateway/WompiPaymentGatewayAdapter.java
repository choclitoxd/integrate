package com.integrate.modules.payment.gateway;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.payment.config.PaymentProperties;
import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.entity.PaymentProvider;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wompi (RF-12, ADR-0009 §3-§4), sin llamadas de red.
 * <ul>
 *   <li><b>Checkout:</b> parámetros del widget con la firma de integridad
 *       {@code SHA-256(reference + amountInCents + currency + secreto de integridad)}, recalculada en cada llamada.</li>
 *   <li><b>Evento:</b> checksum nativo {@code SHA-256(valores de signature.properties + timestamp + secreto de eventos)}.
 *       Se acepta el secreto actual o el anterior (rotación sin cortes). No hay ventana de 300 s: Wompi reintenta a los
 *       30 min, 3 h y 24 h con el {@code timestamp} original; se rechaza solo lo anterior a {@code maxEventAge} o lo que
 *       venga más de 5 min en el futuro. Las repeticiones las frena la clave única {@code (provider, eventId)}.</li>
 *   <li><b>Traducción:</b> {@code eventId = SHA-256(checksum)}, {@code reference} = id del intento,
 *       {@code amountCop = amount_in_cents / 100}.</li>
 * </ul>
 */
@Component
public class WompiPaymentGatewayAdapter implements PaymentGatewayAdapter {

    public static final String TRANSACTION_UPDATED = "transaction.updated";
    private static final String CURRENCY = "COP";
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);

    private final PaymentProperties.Wompi wompi;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public WompiPaymentGatewayAdapter(PaymentProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.wompi = properties.wompi();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.WOMPI;
    }

    @Override
    public boolean configured() {
        return wompi.configured();
    }

    @Override
    public Map<String, Object> start(UUID paymentId, long amountCop, PaymentMethod method) {
        String reference = paymentId.toString();
        long amountInCents = Math.multiplyExact(amountCop, 100L);
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("type", PaymentProvider.WOMPI.name());
        checkout.put("publicKey", wompi.publicKey());
        checkout.put("currency", CURRENCY);
        checkout.put("amountInCents", amountInCents);
        checkout.put("reference", reference);
        checkout.put("integritySignature",
                Digests.sha256Hex(reference + amountInCents + CURRENCY + wompi.integritySecret()));
        return checkout;
    }

    @Override
    public VerifiedEvent verify(byte[] rawBody, HttpHeaders headers) {
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode body;
        try {
            body = jsonMapper.readTree(payload);
        } catch (JacksonException e) {
            throw malformed();
        }
        JsonNode data = body.path("data");
        JsonNode signature = body.path("signature");
        JsonNode properties = signature.path("properties");
        String checksum = signature.path("checksum").asString(null);
        JsonNode timestampNode = body.path("timestamp");
        if (checksum == null || !properties.isArray() || !timestampNode.canConvertToLong()) {
            throw new WebhookRejectedException("missing signature.checksum, signature.properties or timestamp");
        }
        long timestamp = timestampNode.asLong();

        StringBuilder signed = new StringBuilder();
        for (JsonNode property : properties) {
            JsonNode value = resolve(data, property.asString(""));
            signed.append(value.isMissingNode() || value.isNull() ? "" : value.asString());
        }
        signed.append(timestamp);
        if (!matchesAnySecret(signed.toString(), checksum)) {
            throw new WebhookRejectedException("checksum mismatch");
        }
        Instant sentAt = Instant.ofEpochSecond(timestamp);
        Instant now = clock.instant();
        if (sentAt.isBefore(now.minus(wompi.maxEventAge())) || sentAt.isAfter(now.plus(FUTURE_SKEW))) {
            throw new WebhookRejectedException("timestamp outside the accepted age (" + wompi.maxEventAge() + ")");
        }

        String eventType = body.path("event").asString("");
        if (!TRANSACTION_UPDATED.equals(eventType)) {
            throw new IgnoredEventException(eventType);
        }
        return new VerifiedEvent(translate(data.path("transaction"), checksum, sentAt), checksum, payload);
    }

    private CanonicalPaymentEvent translate(JsonNode transaction, String checksum, Instant sentAt) {
        String id = transaction.path("id").asString(null);
        String reference = transaction.path("reference").asString(null);
        JsonNode cents = transaction.path("amount_in_cents");
        if (id == null || id.isBlank() || id.length() > 100 || reference == null || !cents.canConvertToLong()
                || cents.asLong() % 100 != 0) {
            throw malformed();
        }
        GatewayStatus status;
        try {
            status = GatewayStatus.valueOf(transaction.path("status").asString(""));
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
        return new CanonicalPaymentEvent(Digests.sha256Hex(checksum), PaymentProvider.WOMPI, id, null, reference,
                cents.asLong() / 100, status, sentAt, TRANSACTION_UPDATED);
    }

    private boolean matchesAnySecret(String signedPrefix, String checksum) {
        List<String> secrets = new ArrayList<>(2);
        secrets.add(wompi.eventsSecret());
        if (wompi.eventsSecretPrevious() != null && !wompi.eventsSecretPrevious().isBlank()) {
            secrets.add(wompi.eventsSecretPrevious());
        }
        boolean valid = false;
        for (String secret : secrets) {
            // Sin cortocircuito: el tiempo no revela con qué secreto coincidió.
            valid |= Digests.hexEquals(Digests.sha256Hex(signedPrefix + secret), checksum);
        }
        return valid;
    }

    /** {@code "transaction.amount_in_cents"} → {@code data.transaction.amount_in_cents}. */
    private static JsonNode resolve(JsonNode data, String path) {
        JsonNode node = data;
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node;
    }

    private static BusinessRuleException malformed() {
        return new BusinessRuleException(ErrorCode.MALFORMED_REQUEST, "The webhook body is not a valid Wompi event.");
    }
}
