package com.integrate.modules.payment.service.impl;

import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.payment.dto.response.WebhookAck;
import com.integrate.modules.payment.entity.PaymentProvider;
import com.integrate.modules.payment.entity.PaymentTransaction;
import com.integrate.modules.payment.gateway.CanonicalPaymentEvent;
import com.integrate.modules.payment.gateway.GatewayStatus;
import com.integrate.modules.payment.gateway.IgnoredEventException;
import com.integrate.modules.payment.gateway.PaymentGatewayAdapter;
import com.integrate.modules.payment.gateway.PaymentGateways;
import com.integrate.modules.payment.gateway.VerifiedEvent;
import com.integrate.modules.payment.gateway.WebhookRejectedException;
import com.integrate.modules.payment.repository.PaymentTransactionRepository;
import com.integrate.modules.payment.service.PaymentWebhookService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/** La firma se verifica fuera de toda transacción; el procesamiento ocurre en una transacción por evento. */
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookServiceImpl.class);

    private final PaymentGateways gateways;
    private final PaymentEventProcessor processor;
    private final WebhookRejectionAudit rejectionAudit;
    private final PaymentTransactionRepository paymentRepository;
    private final BookingPayments bookingPayments;
    private final Clock clock;

    @Override
    public WebhookAck receive(PaymentProvider provider, byte[] rawBody, HttpHeaders headers) {
        PaymentGatewayAdapter adapter = gateways.forWebhook(provider).orElseThrow(PaymentErrors::webhookNotConfigured);
        VerifiedEvent verified;
        try {
            verified = adapter.verify(rawBody, headers);
        } catch (WebhookRejectedException e) {
            log.warn("payment.webhook rejected provider={} reason={}", provider, e.getMessage());
            rejectionAudit.record(provider, e.getMessage());
            throw PaymentErrors.signatureInvalid();
        } catch (IgnoredEventException e) {
            log.info("payment.webhook ignored provider={} eventType={}", provider, e.eventType());
            return new WebhookAck(true, false, provider.contractName(), null, null, null);
        }
        return processor.process(verified);
    }

    @Override
    public WebhookAck simulate(UUID touristId, UUID paymentId, GatewayStatus status) {
        PaymentTransaction payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getProvider() == PaymentProvider.MOCK)
                .filter(p -> bookingPayments.find(p.getBookingId()).map(b -> b.touristId().equals(touristId))
                        .orElse(false))
                .orElseThrow(PaymentErrors::paymentNotFound);
        String eventId = "sim-" + UUID.randomUUID();
        CanonicalPaymentEvent event = new CanonicalPaymentEvent(eventId, PaymentProvider.MOCK,
                payment.getGatewayTransactionId() != null ? payment.getGatewayTransactionId() : "sim-tx-" + paymentId,
                payment.getBookingId(), null, payment.amountCopValue(), status, clock.instant(),
                "payment." + status.name().toLowerCase());
        String payload = "{\"simulated\":true,\"eventId\":\"" + eventId + "\",\"status\":\"" + status + "\"}";
        return processor.process(new VerifiedEvent(event, "dev-simulator", payload));
    }
}
