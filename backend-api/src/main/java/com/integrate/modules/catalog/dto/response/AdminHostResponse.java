package com.integrate.modules.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila del listado de anfitriones del administrador (ADR-0043 §1.4). Sin documento, celular ni datos de víctima.
 * {@code emailIsSynthetic} marca las cuentas sin correo propio.
 */
public record AdminHostResponse(UUID hostProfileId, UUID userId, String fullName, String email,
                                boolean emailIsSynthetic, UUID associationId, String associationName,
                                boolean verified, Instant createdAt) {
}
