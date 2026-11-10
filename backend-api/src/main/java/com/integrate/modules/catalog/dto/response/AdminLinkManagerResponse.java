package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.AccreditationStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Fila del listado de gestores del administrador (ADR-0043 §1.3). Sin documento ni celular. */
public record AdminLinkManagerResponse(UUID linkManagerId, UUID userId, String fullName, String email,
                                       UUID associationId, String associationName,
                                       AccreditationStatus accreditationStatus, LocalDate accreditedOn,
                                       String accreditedByName) {
}
