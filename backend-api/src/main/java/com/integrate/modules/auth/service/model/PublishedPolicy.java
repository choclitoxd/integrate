package com.integrate.modules.auth.service.model;

import java.time.Instant;

/** Versión vigente de la política de tratamiento de datos que el titular lee antes de aceptar (ADR-0013 §1). */
public record PublishedPolicy(String version, String title, String documentUrl, Instant effectiveFrom,
                              String contentSha256) {
}
