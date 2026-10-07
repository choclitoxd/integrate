package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import java.time.LocalDate;
import java.util.UUID;

/** Acreditación de un gestor de enlace (ADR-0007 §2). */
public record LinkManagerResponse(UUID linkManagerId, UUID userId, UUID associationId,
                                  AccreditationStatus accreditationStatus, LocalDate accreditedOn) {

    public static LinkManagerResponse from(LinkManagerProfile profile) {
        return new LinkManagerResponse(profile.getId(), profile.getUserId(), profile.getAssociationId(),
                profile.getAccreditationStatus(), profile.getAccreditedOn());
    }
}
