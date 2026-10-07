package com.integrate.modules.catalog.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.InvalidParam;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import java.util.List;

/** Fábrica de las excepciones del módulo {@code catalog} (ADR-0006 §7). */
final class CatalogErrors {

    private CatalogErrors() {
    }

    /** 404 también para recursos ajenos: no revela qué identificadores existen (ADR-0006 §4). */
    static BusinessRuleException notFound(String what) {
        return new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, what + " not found");
    }

    static BusinessRuleException invalidTransition(ExperienceStatus from, String action) {
        return new BusinessRuleException(ErrorCode.CATALOG_INVALID_STATUS_TRANSITION,
                "Cannot " + action + " an experience in status " + from)
                .with("currentStatus", from.name());
    }

    static BusinessRuleException notApproved(ExperienceStatus status) {
        return new BusinessRuleException(ErrorCode.CATALOG_EXPERIENCE_NOT_APPROVED,
                "Experience is " + status + "; availability is not published.");
    }

    static BusinessRuleException scheduleConflict(String detail, List<InvalidParam> params) {
        BusinessRuleException e = new BusinessRuleException(ErrorCode.CATALOG_SCHEDULE_CONFLICT, detail);
        return params.isEmpty() ? e : e.with("invalidParams", params);
    }

    static BusinessRuleException hostProfileConflict(String detail) {
        return new BusinessRuleException(ErrorCode.CATALOG_HOST_PROFILE_EXISTS, detail);
    }

    static BusinessRuleException forbidden(String detail) {
        return new BusinessRuleException(ErrorCode.RBAC_FORBIDDEN, detail);
    }

    static BusinessRuleException invalid(List<InvalidParam> params) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.")
                .with("invalidParams", List.copyOf(params));
    }

    static BusinessRuleException invalidField(String field, String reason) {
        return invalid(List.of(new InvalidParam(field, reason)));
    }
}
