package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.util.List;
import java.util.UUID;

/**
 * Cuenta vista por el administrador al buscar una persona (ADR-0043 §1). Respuesta mínima: nunca celular, documento
 * completo ni datos de víctima. El correo va en claro solo cuando se buscó por correo; si se buscó por documento va
 * enmascarado.
 *
 * @param emailMasked      {@code true} si {@code email} está enmascarado
 * @param hostProfileId    perfil de anfitrión vigente, o {@code null}
 * @param linkManagerId    acreditación de gestor vigente, o {@code null}
 * @param linkManagerStatus estado de esa acreditación, o {@code null}
 */
public record AdminUserResponse(UUID userId, String fullName, String email, boolean emailMasked, String status,
                                List<String> roles, UUID hostProfileId, UUID linkManagerId,
                                AccreditationStatus linkManagerStatus) {
}
