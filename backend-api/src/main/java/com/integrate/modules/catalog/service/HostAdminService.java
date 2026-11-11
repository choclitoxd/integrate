package com.integrate.modules.catalog.service;

import com.integrate.modules.catalog.dto.response.HostStatusResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.util.UUID;

/** Suspender, reactivar y revocar a un anfitrión (ADR-0070). */
public interface HostAdminService {

    /**
     * Mueve el perfil de un anfitrión a {@code target}. Revocar quita {@code ROLE_HOST} y conserva
     * {@code ROLE_TOURIST} (ADR-0064); suspender conserva el rol y el servidor niega sus operaciones.
     * Cada cambio queda en la bitácora con su motivo.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} (404) o
     *         {@code CATALOG_INVALID_STATUS_TRANSITION} (409)
     */
    HostStatusResponse changeStatus(UUID hostProfileId, AccreditationStatus target, String reason, UUID adminUserId);
}
