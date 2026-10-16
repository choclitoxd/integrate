package com.integrate.modules.catalog.entity;

/** Forma del recorrido de una experiencia (ADR-0022); los nombres coinciden con {@code ck_experiences_route_type}. */
public enum RouteType {
    /** Circular: sale y llega al mismo punto sin repetir camino. */
    LOOP,
    /** Ida y vuelta por el mismo camino. */
    OUT_AND_BACK,
    /** Solo ida. */
    ONE_WAY
}
