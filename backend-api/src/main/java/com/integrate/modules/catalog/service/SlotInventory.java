package com.integrate.modules.catalog.service;

import com.integrate.modules.catalog.service.model.ReservedSlots;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventario de cupos de las franjas (ADR-0008 §2). Interfaz pública de {@code catalog}: es el único dueño de
 * {@code catalog.schedules.available_slots}. {@link #reserve} y {@link #release} exigen una transacción abierta por
 * quien llama ({@code propagation = MANDATORY}): el descuento o la restitución se confirman con la reserva que los
 * origina.
 */
public interface SlotInventory {

    /** Experiencia a la que pertenece una franja (lectura sin bloqueo), o vacío si la franja no existe. */
    Optional<UUID> experienceOf(UUID scheduleId);

    /**
     * Bloquea la franja con {@code SELECT … FOR UPDATE} (espera como máximo el {@code lock_timeout} del rol),
     * valida que admita {@code slots} cupos y los descuenta.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} si la franja no
     *         existe o su experiencia no está {@code APPROVED}; {@code BOOKING_SCHEDULE_NOT_BOOKABLE} si la franja
     *         no está {@code OPEN} o ya inició; {@code BOOKING_INSUFFICIENT_SLOTS} (con {@code availableSlots}) si
     *         no hay cupos suficientes
     */
    ReservedSlots reserve(UUID scheduleId, int slots, Instant now);

    /**
     * Restituye cupos a varias franjas. Las recorre siempre en el mismo orden para que dos transacciones que
     * liberan cupos de las mismas franjas no se interbloqueen.
     */
    void release(Map<UUID, Integer> slotsBySchedule);
}
