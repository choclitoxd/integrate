package com.integrate.modules.auth.service;

import com.integrate.modules.auth.entity.RoleCode;
import java.util.UUID;

/**
 * Interfaz pública del módulo {@code auth} para otros módulos (guía §5.1): ningún módulo accede
 * a los repositorios de {@code auth} directamente. La usa {@code catalog} al dar de alta un
 * anfitrión (ADR-0006 §3). Participa en la transacción del invocador.
 */
public interface RoleAssignmentService {

    /**
     * Otorga {@code role} a un usuario activo. Idempotente: si ya lo tiene vigente, no hace nada.
     * El cambio se refleja en el JWT en el siguiente login o refresh.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code RESOURCE_NOT_FOUND} si el
     *         usuario no existe, está dado de baja o no está {@code ACTIVE}
     */
    void grant(UUID userId, RoleCode role);

    /**
     * Revoca el rol vigente del usuario (ADR-0038 §2). Idempotente: sin el rol vigente no hace nada. El rol
     * desaparece del JWT en el siguiente login o refresh; el historial queda en {@code auth.user_roles}.
     *
     * @param revokedBy quien ordena la revocación (administrador), o {@code null} si es un proceso del sistema
     */
    void revoke(UUID userId, RoleCode role, UUID revokedBy);
}
