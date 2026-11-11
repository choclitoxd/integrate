package com.integrate.modules.auth.service.model;

/** Acto del titular que acredita el aviso aceptado. Solo se usa entre módulos. */
public record HealthConsentGrant(long grantId, HealthNotice notice) {
}
