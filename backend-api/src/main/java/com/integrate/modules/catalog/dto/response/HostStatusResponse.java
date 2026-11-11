package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.util.UUID;

/** Estado del perfil de un anfitrión (ADR-0070). */
public record HostStatusResponse(UUID hostProfileId, AccreditationStatus status) {
}
