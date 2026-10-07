package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.catalog.dto.request.AssociationRequest;
import com.integrate.modules.catalog.dto.request.HostProfileRequest;
import com.integrate.modules.catalog.dto.response.AssociationResponse;
import com.integrate.modules.catalog.dto.response.HostProfileResponse;
import org.springframework.data.domain.Pageable;

/** Alta mínima de asociaciones y anfitriones por el administrador (RF-03, ADR-0006 §3). */
public interface CatalogAdminService {

    /**
     * @throws com.integrate.common.exception.BusinessRuleException {@code CATALOG_ASSOCIATION_EXISTS} (409) si el
     *         NIT o el RNT ya están registrados; {@code VALIDATION_FAILED} (422) si el municipio no existe
     */
    AssociationResponse createAssociation(AssociationRequest request);

    PageResponse<AssociationResponse> listAssociations(Pageable pageable);

    /**
     * Crea el perfil de anfitrión y otorga {@code ROLE_HOST} en una sola transacción.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} (usuario o
     *         asociación inexistentes) o {@code CATALOG_HOST_PROFILE_EXISTS} (perfil vigente o asociación inactiva)
     */
    HostProfileResponse createHostProfile(HostProfileRequest request);
}
