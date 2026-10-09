package com.integrate.modules.payment.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.modules.booking.service.BookingPayments;
import com.integrate.modules.booking.service.model.PayableBooking;
import com.integrate.modules.catalog.service.SlotInventory;
import com.integrate.modules.catalog.service.model.ScheduleSettlement;
import com.integrate.modules.payment.dto.response.WebhookAck;
import com.integrate.modules.payment.entity.CommissionPolicy;
import com.integrate.modules.payment.entity.Disbursement;
import com.integrate.modules.payment.entity.PaymentStatus;
import com.integrate.modules.payment.entity.PaymentTransaction;
import com.integrate.modules.payment.entity.Refund;
import com.integrate.modules.payment.gateway.CanonicalPaymentEvent;
import com.integrate.modules.payment.gateway.VerifiedEvent;
import com.integrate.modules.payment.repository.CommissionPolicyRepository;
import com.integrate.modules.payment.repository.DisbursementRepository;
import com.integrate.modules.payment.repository.PaymentTransactionRepository;
import com.integrate.modules.payment.repository.RefundRepository;
import com.integrate.modules.payment.repository.WebhookEventStore;
import com.integrate.modules.payment.service.model.BookingConfirmedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una transacción por evento (ADR-0009 §4-§5): bandeja de entrada idempotente, reserva → pago → franja, mapeo de
 * estados, escrow y auditoría como última sentencia. Lo usan el webhook y el simulador de {@code dev}.
 */
@Component
@RequiredArgsConstructor
class PaymentEventProcessor {

    private static final EnumSet<PaymentStatus> OPEN =
            EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PENDING, PaymentStatus.ERROR);

    private final WebhookEventStore eventStore;
    private final PaymentTransactionRepository paymentRepository;
    private final CommissionPolicyRepository policyRepository;
    private final DisbursementRepository disbursementRepository;
    private final RefundRepository refundRepository;
    private final BookingPayments bookingPayments;
    private final SlotInventory slotInventory;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher events;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public WebhookAck process(VerifiedEvent verified) {
        CanonicalPaymentEvent event = verified.event();
        Optional<Long> row = eventStore.insertIfAbsent(event.provider(), event.eventId(), event.eventType(),
                event.status().name(), verified.signature(), verified.payloadJson());
        if (row.isEmpty()) {
            return duplicateAck(event);
        }

        // Orden de bloqueos: reserva → pago → franja. El intento se localiza primero sin bloqueo.
        PaymentTransaction located = locate(event).orElseThrow(PaymentErrors::webhookBookingNotFound);
        PayableBooking booking = bookingPayments.lockForPayment(located.getBookingId())
                .orElseThrow(PaymentErrors::webhookBookingNotFound);
        // refresh con bloqueo: el intento ya está en el contexto y otra transacción pudo cambiarlo mientras se
        // esperaba la reserva; un SELECT … FOR UPDATE por consulta devolvería la instancia sin releer su estado.
        PaymentTransaction payment = located;
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        if (payment.amountCopValue() != event.amountCop()) {
            throw PaymentErrors.amountMismatch(payment.amountCopValue(), event.amountCop());
        }

        if (payment.getStatus().isFinal()) {
            eventStore.markProcessed(row.get(), payment.getId(), "payment already " + payment.getStatus());
            return ack(event, false, payment.getStatus(), booking.status());
        }
        payment.attachGatewayTransaction(event.transactionId());

        Outcome outcome = switch (event.status()) {
            case APPROVED -> approve(payment, booking);
            case DECLINED -> settleWithoutCharge(payment, booking, PaymentStatus.REJECTED, true);
            case VOIDED -> settleWithoutCharge(payment, booking, PaymentStatus.VOIDED, false);
            case ERROR -> keep(payment, booking, PaymentStatus.ERROR);
            case PENDING -> keep(payment, booking, PaymentStatus.PENDING);
        };
        eventStore.markProcessed(row.get(), payment.getId(), outcome.processingError());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bookingId", booking.bookingId().toString());
        details.put("eventId", event.eventId());
        details.put("gatewayStatus", event.status().name());
        details.put("amountCop", event.amountCop());
        details.putAll(outcome.details());
        auditTrail.record(new AuditEntry("payments", "payment_transactions", payment.getId().toString(),
                outcome.action(), outcome.severity(), null, null, null, details));
        return ack(event, false, payment.getStatus(), outcome.bookingStatus());
    }

    /**
     * Wompi trae la referencia del intento; el Mock, la reserva y el id de su transacción. Solo se consideran
     * intentos del mismo proveedor.
     */
    private Optional<PaymentTransaction> locate(CanonicalPaymentEvent event) {
        if (event.paymentReference() != null) {
            UUID reference;
            try {
                reference = UUID.fromString(event.paymentReference());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            return paymentRepository.findById(reference).filter(p -> p.getProvider() == event.provider());
        }
        Optional<PaymentTransaction> known = event.transactionId() == null ? Optional.empty()
                : paymentRepository.findByProviderAndGatewayTransactionId(event.provider(), event.transactionId());
        if (known.isPresent()) {
            return known.filter(p -> event.bookingId() == null || p.getBookingId().equals(event.bookingId()));
        }
        return event.bookingId() == null ? Optional.empty() : paymentRepository
                .findFirstByBookingIdAndProviderAndStatusInOrderByCreatedAtDesc(event.bookingId(), event.provider(), OPEN);
    }

    private Outcome approve(PaymentTransaction payment, PayableBooking booking) {
        if (booking.isPending()) {
            payment.moveTo(PaymentStatus.APPROVED, null);
            paymentRepository.flush();
            bookingPayments.confirm(booking.bookingId());
            ScheduleSettlement settlement = slotInventory.settlementOf(booking.scheduleId())
                    .orElseThrow(() -> new IllegalStateException("Booking " + booking.bookingId() + " has no schedule"));
            CommissionPolicy policy = policyRepository.findById(payment.getCommissionPolicyId())
                    .orElseThrow(() -> new IllegalStateException("Payment " + payment.getId() + " has no policy"));
            BigDecimal community = policy.communityShareOf(payment.getAmountCop());
            disbursementRepository.saveAndFlush(Disbursement.schedule(payment.getId(), settlement.associationId(),
                    community, settlement.endTime().plus(Duration.ofHours(policy.getPayoutGraceHours()))));
            events.publishEvent(new BookingConfirmedEvent(booking.bookingId(), payment.getId()));
            return new Outcome("PAYMENT_APPROVED", Severity.INFO, PayableBooking.CONFIRMED, null,
                    Map.of("communityAmountCop", community.longValueExact(),
                            "associationId", settlement.associationId().toString()));
        }
        if (PayableBooking.CONFIRMED.equals(booking.status())
                || paymentRepository.existsByBookingIdAndStatus(booking.bookingId(), PaymentStatus.APPROVED)) {
            // Otro intento ya cobró esta reserva: doble cobro que exige reembolso manual (índice de un solo APPROVED).
            payment.moveTo(PaymentStatus.ERROR, "DUPLICATE_APPROVAL: the booking was already paid");
            return new Outcome("PAYMENT_DUPLICATE_APPROVAL", Severity.CRITICAL, booking.status(),
                    "duplicate approval", Map.of());
        }
        // Aprobación tardía (S3): la reserva no revive; se ordena el reembolso del 100% (ejecución en T-2.6).
        payment.moveTo(PaymentStatus.APPROVED, "LATE_APPROVAL: booking was " + booking.status());
        paymentRepository.flush();
        Refund refund = refundRepository.saveAndFlush(Refund.lateApproval(payment));
        return new Outcome("PAYMENT_LATE_APPROVAL", Severity.HIGH, booking.status(), null,
                Map.of("refundId", refund.getId().toString(), "refundAmountCop", payment.amountCopValue()));
    }

    /** {@code DECLINED} → {@code PAYMENT_FAILED}; {@code VOIDED} → {@code CANCELLED_REFUNDED}; ambos devuelven cupos. */
    private Outcome settleWithoutCharge(PaymentTransaction payment, PayableBooking booking, PaymentStatus target,
                                        boolean declined) {
        payment.moveTo(target, null);
        paymentRepository.flush();
        String bookingStatus = booking.status();
        if (booking.isPending()) {
            if (declined) {
                bookingPayments.failPayment(booking.bookingId());
                bookingStatus = "PAYMENT_FAILED";
            } else {
                bookingPayments.voidPending(booking.bookingId());
                bookingStatus = "CANCELLED_REFUNDED";
            }
        }
        return new Outcome("PAYMENT_" + target.name(), Severity.INFO, bookingStatus, null,
                Map.of("slotsReleased", booking.isPending() ? booking.slots() : 0));
    }

    private Outcome keep(PaymentTransaction payment, PayableBooking booking, PaymentStatus target) {
        payment.moveTo(target, null);
        paymentRepository.flush();
        return new Outcome("PAYMENT_" + target.name(), Severity.INFO, booking.status(), null, Map.of());
    }

    private WebhookAck duplicateAck(CanonicalPaymentEvent event) {
        Optional<PaymentTransaction> payment = eventStore.paymentOf(event.provider(), event.eventId())
                .flatMap(paymentRepository::findById);
        String bookingStatus = payment.flatMap(p -> bookingPayments.find(p.getBookingId()))
                .map(PayableBooking::status).orElse(null);
        return ack(event, true, payment.map(PaymentTransaction::getStatus).orElse(null), bookingStatus);
    }

    private static WebhookAck ack(CanonicalPaymentEvent event, boolean duplicate, PaymentStatus paymentStatus,
                                  String bookingStatus) {
        return new WebhookAck(true, duplicate, event.provider().contractName(), event.eventId(),
                paymentStatus == null ? null : paymentStatus.name(), bookingStatus);
    }

    private record Outcome(String action, Severity severity, String bookingStatus, String processingError,
                           Map<String, Object> details) {
    }
}
