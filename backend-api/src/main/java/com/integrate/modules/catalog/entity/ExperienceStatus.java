package com.integrate.modules.catalog.entity;

/** Valores de {@code ck_experiences_status} (V3). Transiciones: {@link #canTransitionTo}. */
public enum ExperienceStatus {
    DRAFT,
    PENDING_REVIEW,
    REJECTED,
    APPROVED,
    SUSPENDED,
    RETIRED;

    /** Misma tabla que el trigger {@code catalog.fn_experience_guard} (V3), validada antes del UPDATE. */
    public boolean canTransitionTo(ExperienceStatus target) {
        return switch (this) {
            case DRAFT -> target == PENDING_REVIEW || target == RETIRED;
            case PENDING_REVIEW -> target == APPROVED || target == REJECTED || target == DRAFT;
            case REJECTED -> target == DRAFT || target == RETIRED;
            case APPROVED -> target == SUSPENDED || target == RETIRED;
            case SUSPENDED -> target == APPROVED || target == RETIRED;
            case RETIRED -> false;
        };
    }

    /** El anfitrión solo edita propuestas que aún no están en revisión ni publicadas. */
    public boolean isEditableByHost() {
        return this == DRAFT || this == REJECTED;
    }
}
