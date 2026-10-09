package com.integrate.modules.booking.entity;

/**
 * Valores de {@code ck_bookings_status} (V4). Las transiciones válidas (E3) las impone
 * {@code booking.fn_booking_update_guard} (V10).
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    PAYMENT_FAILED,
    EXPIRED,
    CANCELLED_REFUNDED,
    CANCELLED_NO_REFUND,
    CANCELLED_BY_PLATFORM,
    EXECUTED,
    NO_SHOW,
    SYNC_CONFLICT
}
