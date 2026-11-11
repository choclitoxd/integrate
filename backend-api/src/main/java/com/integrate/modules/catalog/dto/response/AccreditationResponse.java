package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import java.time.LocalDate;

/** Estado de la acreditación del gestor (ADR-0069): lo consulta la PWA para ocultar sus herramientas. */
public record AccreditationResponse(AccreditationStatus status, LocalDate accreditedOn) {

    public static AccreditationResponse from(LinkManagerProfile profile) {
        return new AccreditationResponse(profile.getAccreditationStatus(), profile.getAccreditedOn());
    }
}
