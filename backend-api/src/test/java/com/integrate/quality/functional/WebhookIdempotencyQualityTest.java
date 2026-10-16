package com.integrate.quality.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.integrate.common.crypto.HmacSha256;
import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * RNF-25 (Adecuación funcional · corrección): la pasarela reintenta sus webhooks ante fallas de red; el receptor
 * debe procesar el pago una sola vez. Se entrega el mismo evento en ráfaga concurrente, luego en reintentos
 * secuenciales y además un evento nuevo de la misma transacción, y se cuentan los efectos de negocio.
 */
@Tag("rnf-25")
@Tag("iso-adecuacion")
@DisplayName("[RNF-25] Idempotencia transaccional en el procesamiento de webhooks")
class WebhookIdempotencyQualityTest extends QualityIntegrationTest {

    private static final int BURST = 10;
    private static final int RETRIES = 5;

    @Test
    @DisplayName("[RNF-25] Cero duplicidad de cobros, dispersiones, vouchers y correos ante reintentos")
    void retriedDeliveriesHaveASingleEffect() throws Exception {
        Scenario s = scenario(12);
        String bookingId = lockBooking(s.touristToken(), schedule(s, 6), 2);
        String paymentId = startPayment(s.touristToken(), bookingId);
        String transactionId = "tx-" + UUID.randomUUID();
        String event = event("evt-" + UUID.randomUUID(), transactionId, bookingId);

        List<Integer> statuses = new ArrayList<>(runConcurrently(BURST, BURST, i -> deliver(event)));
        for (int i = 0; i < RETRIES; i++) {
            statuses.add(deliver(event));
        }
        // La pasarela puede reenviar la misma transacción con un identificador de evento nuevo.
        statuses.add(deliver(event("evt-" + UUID.randomUUID(), transactionId, bookingId)));
        Voucher voucher = awaitVoucher(bookingId);

        long deliveries = statuses.size();
        long accepted = statuses.stream().filter(code -> code == 200).count();
        Map<String, Long> effects = Map.of(
                "confirmaciones", count("""
                        SELECT count(*) FROM booking.booking_status_history
                         WHERE booking_id = ?::uuid AND to_status = 'CONFIRMED'""", bookingId),
                "pagos aprobados", count("""
                        SELECT count(*) FROM audit.audit_trail WHERE action = 'PAYMENT_APPROVED' AND entity_id = ?""",
                        paymentId),
                "dispersiones", count("SELECT count(*) FROM payments.disbursements WHERE payment_id = ?::uuid",
                        paymentId),
                "vouchers", count("SELECT count(*) FROM voucher.vouchers WHERE booking_id = ?::uuid", bookingId),
                "correos de voucher", count("""
                        SELECT count(*) FROM notification.notification_outbox
                         WHERE event_type = 'VOUCHER_ISSUED' AND aggregate_id = ?""", voucher.getId().toString()));
        long duplicates = effects.values().stream().mapToLong(n -> Math.max(0, n - 1)).sum();
        long missing = effects.values().stream().filter(n -> n == 0).count();

        QualityMetrics.record("RNF-25", "Efectos duplicados ante reintentos de webhook", duplicates, "efectos",
                "= 0", duplicates == 0 && missing == 0, deliveries + " entregas (" + BURST + " concurrentes, "
                        + RETRIES + " reintentos, 1 evento nuevo de la misma transacción); efectos " + effects);
        QualityMetrics.record("RNF-25", "Entregas de webhook respondidas 200 (sin reintento infinito)",
                QualityMetrics.percent(accepted, deliveries), "%", "= 100 %", accepted == deliveries,
                "un 5xx haría que la pasarela siga reintentando");
        assertThat(missing).as("cada efecto ocurre al menos una vez").isZero();
        assertThat(duplicates).as("ningún efecto se repite: " + effects).isZero();
        assertThat(accepted).isEqualTo(deliveries);
    }

    private int deliver(String body) throws Exception {
        long timestamp = clock.instant().getEpochSecond();
        String signature = HmacSha256.hex(Base64.getDecoder().decode(MOCK_WEBHOOK_SECRET),
                (timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return mvc.perform(post("/api/v1/payments/webhook/mock").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature).header("X-Timestamp", Long.toString(timestamp))
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andReturn().getResponse().getStatus();
    }

    private String event(String eventId, String transactionId, String bookingId) {
        return """
                {"eventId":"%s","provider":"MOCK_GATEWAY","transactionId":"%s","bookingId":"%s",
                 "amountCop":%d,"status":"APPROVED","timestamp":"%s"}"""
                .formatted(eventId, transactionId, bookingId, 2 * PRICE, clock.instant());
    }
}
