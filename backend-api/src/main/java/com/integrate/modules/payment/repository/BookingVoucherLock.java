package com.integrate.modules.payment.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Bloqueo del voucher de una reserva (ADR-0034 §2). Solo toma el candado de la fila; la revocación la hace
 * {@code voucher} al atender el evento de cancelación. Existe para respetar el orden de bloqueo canónico
 * voucher → reserva → pago → dispersión → franja que ya siguen el canje y la sincronización: sin él, una cancelación
 * y un canje simultáneos podrían interbloquearse.
 */
@Repository
@RequiredArgsConstructor
public class BookingVoucherLock {

    private final JdbcClient jdbc;

    /** {@code FOR NO KEY UPDATE}, igual que el canje: no bloquea las FK de la evidencia que se inserta en paralelo. */
    public void lockOf(UUID bookingId) {
        jdbc.sql("SELECT id FROM voucher.vouchers WHERE booking_id = :bookingId FOR NO KEY UPDATE")
                .param("bookingId", bookingId).query((rs, n) -> rs.getObject("id", UUID.class)).list();
    }
}