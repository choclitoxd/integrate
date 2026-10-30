package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.payment.service.model.BookingCancelledEvent;
import com.integrate.modules.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revoca el voucher de una reserva cancelada (ADR-0034 §2). Listener síncrono a propósito: corre en el hilo y la
 * transacción de la cancelación, así que el voucher queda revocado con ella (y entra en la lista delta del ADR-0032)
 * o no queda nada.
 */
@Component
@RequiredArgsConstructor
class BookingCancelledListener {

    private final VoucherRepository vouchers;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onBookingCancelled(BookingCancelledEvent event) {
        vouchers.revokeOfBooking(event.bookingId(), event.at(), event.reason());
    }
}