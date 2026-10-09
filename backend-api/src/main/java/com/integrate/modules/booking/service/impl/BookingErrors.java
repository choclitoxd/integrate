package com.integrate.modules.booking.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.InvalidParam;
import com.integrate.modules.auth.entity.ConsentPurpose;
import java.util.List;

/** Fábrica de las excepciones del módulo {@code booking} (contrato §2 y §5). */
final class BookingErrors {

    private BookingErrors() {
    }

    /** 404 también para reservas ajenas: no revela qué identificadores existen. */
    static BusinessRuleException notFound() {
        return new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "Booking not found");
    }

    static BusinessRuleException invalid(List<InvalidParam> params) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.")
                .with("invalidParams", List.copyOf(params));
    }

    static BusinessRuleException idempotencyKeyReused(String detail) {
        return new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_REUSED, detail);
    }

    static BusinessRuleException healthConsentRequired(String policyVersion) {
        BusinessRuleException e = new BusinessRuleException(ErrorCode.CONSENT_HEALTH_EMERGENCY_REQUIRED,
                "The health and emergency data clause must be accepted before locking slots.")
                .with("requiredPurposes", List.of(ConsentPurpose.HEALTH_EMERGENCY.name()));
        return policyVersion == null ? e : e.with("policyVersion", policyVersion);
    }
}
