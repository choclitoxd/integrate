package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.booking.service.model.PayableBooking;
import com.integrate.modules.payment.dto.response.PaymentCheckoutResponse;
import com.integrate.modules.payment.entity.CommissionPolicy;
import com.integrate.modules.payment.entity.PaymentMethod;
import com.integrate.modules.payment.entity.PaymentStatus;
import com.integrate.modules.payment.entity.PaymentTransaction;
import com.integrate.modules.payment.gateway.PaymentGatewayAdapter;
import com.integrate.modules.payment.gateway.PaymentGateways;
import com.integrate.modules.payment.repository.CommissionPolicyRepository;
import com.integrate.modules.payment.repository.PaymentTransactionRepository;
import com.integrate.modules.payment.service.PaymentCheckoutService;
import com.integrate.modules.payment.service.model.CheckoutOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Un solo intento abierto por reserva (ADR-0009 §3): un doble clic devuelve el mismo intento y nunca crea dos cobros.
 * Un intento en {@code ERROR} no se reutiliza, porque la pasarela puede rechazar una referencia ya usada.
 */
@Service
@RequiredArgsConstructor
public class PaymentCheckoutServiceImpl implements PaymentCheckoutService {

    private static final EnumSet<PaymentStatus> REUSABLE = EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PENDING);

    private final BookingPayments bookingPayments;
    private final PaymentTransactionRepository paymentRepository;
    private final CommissionPolicyRepository policyRepository;
    private final PaymentGateways gateways;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    @Transactional
    public CheckoutOutcome start(UUID touristId, UUID bookingId, PaymentMethod method, ClientInfo client) {
        Instant now = clock.instant();
        PayableBooking booking = bookingPayments.lockForPayment(bookingId)
                .filter(b -> b.touristId().equals(touristId))
                .orElseThrow(PaymentErrors::bookingNotFound);
        if (!booking.isPayableAt(now)) {
            throw PaymentErrors.notPayable(booking.isPending() ? "EXPIRED" : booking.status());
        }

        PaymentGatewayAdapter adapter = gateways.active();
        Optional<PaymentTransaction> open = paymentRepository
                .findFirstByBookingIdAndProviderAndStatusInOrderByCreatedAtDesc(bookingId, adapter.provider(), REUSABLE);
        PaymentTransaction payment;
        boolean created = open.isEmpty();
        if (open.isPresent()) {
            payment = open.get();
            payment.changeMethod(method);
            paymentRepository.flush();
        } else {
            CommissionPolicy policy = policyRepository.findCurrent(now)
                    .orElseThrow(() -> new IllegalStateException("No commission policy is in force at " + now));
            // El trigger revalida reserva PENDING, monto exacto, participantes completos y política vigente.
            payment = paymentRepository.saveAndFlush(PaymentTransaction.initiate(UUID.randomUUID(), bookingId,
                    policy.getId(), adapter.provider(), method, booking.totalAmountCop()));
        }

        Map<String, Object> checkout = adapter.start(payment.getId(), payment.amountCopValue(), method);
        if (created) {
            auditTrail.record(new AuditEntry("payments", "payment_transactions", payment.getId().toString(),
                    "PAYMENT_INITIATED", Severity.INFO, touristId, client.ipAddress(), client.userAgent(),
                    Map.of("bookingId", bookingId.toString(), "provider", adapter.provider().name(),
                            "amountCop", payment.amountCopValue(), "paymentMethod", method.name())));
        }
        return new CheckoutOutcome(new PaymentCheckoutResponse(payment.getId(), bookingId,
                adapter.provider().contractName(), payment.getPaymentMethod(), payment.getStatus(),
                payment.amountCopValue(), "COP", booking.expiresAt(), checkout), created);
    }
}
