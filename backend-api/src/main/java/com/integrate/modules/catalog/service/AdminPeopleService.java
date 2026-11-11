package com.integrate.modules.catalog.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.catalog.dto.request.AdminUserCreateRequest;
import com.integrate.modules.catalog.dto.request.AdminUserUpdateRequest;
import com.integrate.modules.catalog.dto.response.AdminHostResponse;
import com.integrate.modules.catalog.dto.response.AdminLinkManagerResponse;
import com.integrate.modules.catalog.dto.response.AdminUserDetailResponse;
import com.integrate.modules.catalog.dto.response.AdminUserResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Consulta de personas por el administrador (ADR-0043 §1): búsqueda exacta, detalle y los listados de gestores y de
 * anfitriones. Cada consulta que entrega nombre o correo queda en la bitácora como acceso sensible (ADR-0035 §2);
 * el criterio buscado nunca se guarda.
 */
public interface AdminPeopleService {

    /**
     * Cuenta con ese correo exacto, en claro. Las cuentas con correo sintético no se encuentran por correo.
     *
     * @return página de 0 o 1 elemento
     * @throws com.integrate.common.exception.BusinessRuleException {@code ADMIN_LOOKUP_RATE_LIMITED} (429)
     */
    PageResponse<AdminUserResponse> searchByEmail(String email, UUID adminId, ClientInfo client);

    /**
     * Cuenta con ese tipo y número de documento exactos; el correo sale enmascarado.
     *
     * @return página de 0 o 1 elemento
     * @throws com.integrate.common.exception.BusinessRuleException {@code ADMIN_LOOKUP_RATE_LIMITED} (429)
     */
    PageResponse<AdminUserResponse> searchByDocument(DocType docType, String docNumber, UUID adminId,
                                                     ClientInfo client);

    /** @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} (404) */
    AdminUserDetailResponse detail(UUID userId, UUID adminId, ClientInfo client);

    /** @param status filtro opcional por estado de acreditación */
    PageResponse<AdminLinkManagerResponse> listLinkManagers(AccreditationStatus status, Pageable pageable,
                                                            UUID adminId, ClientInfo client);

    /**
     * Cuentas abiertas paginadas, con el correo en claro (ADR-0048 §1). Deja una fila de acceso sensible.
     *
     * @param role filtro opcional por rol vigente
     */
    PageResponse<AdminUserResponse> listUsers(RoleCode role, Pageable pageable, UUID adminId, ClientInfo client);

    /**
     * Crea una cuenta de turista (ADR-0048 §2).
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code AUTH_USER_ALREADY_EXISTS} (409),
     *         {@code VALIDATION_FAILED} (422)
     */
    AdminUserResponse createUser(AdminUserCreateRequest request, UUID adminId);

    /**
     * Actualiza los datos de una persona (ADR-0057 §1).
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} (404),
     *         {@code AUTH_USER_ALREADY_EXISTS} (409), {@code VALIDATION_FAILED} (422)
     */
    AdminUserResponse updateUser(UUID userId, AdminUserUpdateRequest request, UUID adminId);

    /** @param associationId filtro opcional por asociación */
    PageResponse<AdminHostResponse> listHosts(UUID associationId, Pageable pageable, UUID adminId,
                                              ClientInfo client);
}
