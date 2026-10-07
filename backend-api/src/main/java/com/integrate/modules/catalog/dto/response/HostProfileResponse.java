package com.integrate.modules.catalog.dto.response;

import java.util.UUID;

/**
 * Anfitrión dado de alta. El usuario recibe {@code ROLE_HOST}, visible en su JWT desde el siguiente
 * login o refresh. Nunca incluye datos de víctima.
 */
public record HostProfileResponse(UUID hostProfileId, UUID userId, UUID associationId) {
}
