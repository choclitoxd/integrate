package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.payment.service.model.BookingConfirmedEvent;
import com.integrate.modules.voucher.config.VoucherAsyncConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emisión inmediata tras confirmar el pago (ADR-0010 §5): después del {@code COMMIT} del webhook y en el ejecutor
 * acotado, para no retrasar la respuesta a la pasarela. Cualquier fallo queda para el barrido durable.
 */
@Component
@RequiredArgsConstructor
class BookingConfirmedListener {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedListener.class);

    private final VoucherIssuer issuer;

    @Async(VoucherAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            issuer.issue(event.bookingId()).ifPresent(issuer::renderPdf);
        } catch (RuntimeException e) {
            log.warn("voucher.issue deferred to sweep bookingId={} cause={}", event.bookingId(), e.toString());
        }
    }
}
