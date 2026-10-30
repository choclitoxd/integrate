package com.integrate.modules.audit.service;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.audit.dto.response.AuditRecordResponse;
import com.integrate.modules.audit.dto.response.AuditVerifyResponse;
import com.integrate.modules.audit.service.model.AuditCaller;
import com.integrate.modules.audit.service.model.AuditFilter;

/** Consulta, exportación y verificación de la bitácora para el Administrador (RF-20, ADR-0035 §1). */
public interface AuditQueryService {

    /** Más reciente primero. La consulta queda auditada (`AUDIT_QUERIED`). */
    PageResponse<AuditRecordResponse> search(AuditCaller caller, AuditFilter filter, int page, int size);

    /**
     * CSV UTF-8 con todas las filas del filtro (`AUDIT_EXPORTED`).
     *
     * @throws com.integrate.common.exception.BusinessRuleException 422 {@code AUDIT_EXPORT_TOO_LARGE} si el filtro
     *         devuelve más filas que el tope
     */
    byte[] export(AuditCaller caller, AuditFilter filter);

    /**
     * Verifica la cadena de hashes (`AUDIT_VERIFIED`). Sin rango, las últimas filas.
     *
     * @throws com.integrate.common.exception.BusinessRuleException 422 si el rango es inválido o demasiado grande
     */
    AuditVerifyResponse verify(AuditCaller caller, Long fromId, Long toId);
}