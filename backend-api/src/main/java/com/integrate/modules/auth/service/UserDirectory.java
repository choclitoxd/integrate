package com.integrate.modules.auth.service;

import com.integrate.modules.auth.entity.CredentialStatus;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.RoleCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de datos de contacto de usuarios para otros módulos (guía §5.1). La usa {@code catalog}
 * para el listado de anfitriones del gestor (ADR-0007 §1) y para las consultas de personas del
 * administrador (ADR-0043 §1). Nunca expone credenciales.
 */
public interface UserDirectory {

    /** Resumen de los usuarios existentes entre {@code userIds}; los inexistentes no aparecen. */
    Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds);

    /** Cuenta abierta con ese correo (sin distinguir mayúsculas), o vacío si no existe o está cerrada. */
    Optional<AccountView> findAccountByEmail(String email);

    /** Cuenta abierta con ese tipo y número de documento exactos, o vacío. */
    Optional<AccountView> findAccountByDocument(DocType docType, String docNumber);

    /** Cuenta abierta por identificador, o vacío si no existe o está cerrada. */
    Optional<AccountView> findAccount(UUID userId);

    /** Todas las asignaciones de rol del usuario, vigentes y revocadas, de la más antigua a la más reciente. */
    List<RoleHistoryEntry> roleHistory(UUID userId);

    record UserSummary(UUID userId, String fullName, String email, String phone, DocType docType, String docNumber,
                       CredentialStatus credentialStatus) {
    }

    /** Identidad de una cuenta para el administrador: sin celular ni credenciales. */
    record AccountView(UUID userId, String fullName, String email, DocType docType, String docNumber, String status,
                       List<RoleCode> roles) {
    }

    /** {@code revokedAt} es {@code null} mientras el rol siga vigente. */
    record RoleHistoryEntry(RoleCode role, Instant revokedAt, UUID revokedBy) {
    }
}
