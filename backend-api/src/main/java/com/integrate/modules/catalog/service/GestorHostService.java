package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.response.ActivationCodeResponse;
import com.integrate.modules.catalog.dto.response.ManagedHostResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Trabajo del gestor sobre los anfitriones de su jurisdicción vigente (ADR-0007 §1 y §6). */
public interface GestorHostService {

    /** @throws com.integrate.common.exception.BusinessRuleException {@code ENROLLMENT_MANAGER_NOT_ACTIVE} (403) */
    PageResponse<ManagedHostResponse> listHosts(UUID managerUserId, Pageable pageable);

    /**
     * Emite el código de activación de un anfitrión con asignación vigente del gestor.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code ENROLLMENT_MANAGER_NOT_ACTIVE} (403),
     *         {@code RESOURCE_NOT_FOUND} (404, también si el anfitrión no es de su jurisdicción) o
     *         {@code CATALOG_INVALID_STATUS_TRANSITION} (409, la cuenta ya está activa)
     */
    ActivationCodeResponse issueActivationCode(UUID managerUserId, UUID hostProfileId);
}
