package com.integrate.modules.booking.service;

import java.util.UUID;

/**
 * Cierre de una reserva cuyo voucher se canjeó en campo (ADR-0032 §4). Interfaz pública de {@code booking}; la usa
 * el puerto de canje de {@code voucher}.
 */
public interface BookingExecution {

    /**
     * Pasa la reserva {@code CONFIRMED} a {@code EXECUTED} dentro de la transacción en curso.
     *
     * @throws IllegalStateException si la reserva no existe o no está {@code CONFIRMED}
     */
    void markExecuted(UUID bookingId);

    /**
     * {@code CONFIRMED → NO_SHOW}: nadie asistió y venció la gracia de la dispersión (RF-14, ADR-0034 §4). Un
     * {@code NO_SHOW} todavía puede pasar a {@code EXECUTED} si luego llega un canje válido (ADR-0033).
     *
     * @throws IllegalStateException si la reserva no existe o no está {@code CONFIRMED}
     */
    void markNoShow(UUID bookingId);
}
