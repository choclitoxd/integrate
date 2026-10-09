package com.integrate.modules.booking.service.model;

/** Resultado de un lote del worker TTL: reservas pasadas a {@code EXPIRED} y cupos devueltos a sus franjas. */
public record ExpiredBatch(int expired, int releasedSlots) {

    public static final ExpiredBatch EMPTY = new ExpiredBatch(0, 0);
}
