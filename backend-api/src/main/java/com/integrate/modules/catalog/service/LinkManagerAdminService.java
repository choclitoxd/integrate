package com.integrate.modules.catalog.service;

import com.integrate.modules.catalog.dto.request.LinkManagerRequest;
import com.integrate.modules.catalog.dto.response.LinkManagerResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.util.UUID;

/** Acreditación de gestores de enlace por el administrador (RF-03, ADR-0007 §2). */
public interface LinkManagerAdminService {

    /**
     * Crea la acreditación {@code ACTIVE} y otorga {@code ROLE_LINK_MANAGER} en una sola transacción.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} (usuario o asociación),
     *         {@code CATALOG_HOST_PROFILE_EXISTS} (asociación inactiva), {@code ENROLLMENT_MANAGER_EXISTS} (acreditación
     *         vigente) o {@code VALIDATION_FAILED} (fecha futura)
     */
    LinkManagerResponse accredit(LinkManagerRequest request, UUID adminUserId);

    /**
     * Suspende, reactiva o revoca la acreditación. {@code REVOKED} es terminal y además quita
     * {@code ROLE_LINK_MANAGER} y deja una entrada de auditoría (ADR-0038 §2); suspender no toca el rol.
     *
     * @param adminUserId administrador que ordena el cambio
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} o
     *         {@code CATALOG_INVALID_STATUS_TRANSITION} (409)
     */
    LinkManagerResponse changeStatus(UUID linkManagerId, AccreditationStatus target, UUID adminUserId);
}
