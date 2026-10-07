package com.integrate.modules.catalog.dto.response;

import com.integrate.modules.catalog.entity.AssociationStatus;
import com.integrate.modules.catalog.entity.AssociationType;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import java.util.UUID;

/** Asociación comunitaria (vista de administración). */
public record AssociationResponse(UUID id, String nit, String rntNumber, String legalName,
                                  AssociationType associationType, String municipalityCode, String address,
                                  String contactEmail, String contactPhone, AssociationStatus status) {

    public static AssociationResponse from(CommunityAssociation a) {
        return new AssociationResponse(a.getId(), a.getNit(), a.getRntNumber(), a.getLegalName(),
                a.getAssociationType(), a.getMunicipalityCode(), a.getAddress(), a.getContactEmail(),
                a.getContactPhone(), a.getStatus());
    }
}
