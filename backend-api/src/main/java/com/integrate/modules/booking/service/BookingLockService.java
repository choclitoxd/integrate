package com.integrate.modules.booking.service;

import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.booking.dto.request.BookingLockRequest;
import com.integrate.modules.booking.service.model.LockOutcome;
import java.util.UUID;

/** Bloqueo pesimista de cupos y creación de la reserva {@code PENDING} (RF-09, RF-10, ADR-0008 §3). */
public interface BookingLockService {

    /**
     * @param idempotencyKey cabecera {@code Idempotency-Key}; un reintento con el mismo cuerpo reproduce la reserva
     * @throws com.integrate.common.exception.BusinessRuleException {@code VALIDATION_FAILED} (422),
     *         {@code IDEMPOTENCY_KEY_REUSED} (409), {@code CONSENT_HEALTH_EMERGENCY_REQUIRED} (428) y los errores de
     *         {@link com.integrate.modules.catalog.service.SlotInventory#reserve}
     */
    LockOutcome lock(UUID touristId, UUID idempotencyKey, BookingLockRequest request, ClientInfo client);
}
