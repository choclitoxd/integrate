package com.integrate.modules.catalog.dto.request;

import jakarta.validation.constraints.NotNull;

/** Solicitud de URL prefirmada para subir un documento del enrolamiento (ADR-0007 §5). */
public record DocumentUploadRequest(@NotNull DocumentKind kind) {
}
