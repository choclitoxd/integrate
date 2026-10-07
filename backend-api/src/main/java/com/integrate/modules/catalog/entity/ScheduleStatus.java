package com.integrate.modules.catalog.entity;

/** Valores de {@code ck_schedules_status} (V3). */
public enum ScheduleStatus {
    /** Admite reservas. */
    OPEN,
    /** Sin nuevas reservas (experiencia suspendida o retirada). */
    CLOSED,
    /** Cancelada por el anfitrión (clima u otra causa). */
    CANCELLED
}
