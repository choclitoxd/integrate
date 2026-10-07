package com.integrate.modules.catalog.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.catalog.dto.request.LinkManagerRequest;
import com.integrate.modules.catalog.dto.response.LinkManagerResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import com.integrate.modules.catalog.repository.CommunityAssociationRepository;
import com.integrate.modules.catalog.repository.LinkManagerProfileRepository;
import com.integrate.modules.catalog.service.LinkManagerAdminService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkManagerAdminServiceImpl implements LinkManagerAdminService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final LinkManagerProfileRepository linkManagerRepository;
    private final CommunityAssociationRepository associationRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final Clock clock;

    @Override
    @Transactional
    public LinkManagerResponse accredit(LinkManagerRequest request, UUID adminUserId) {
        if (request.accreditedOn().isAfter(LocalDate.ofInstant(clock.instant(), BOGOTA))) {
            throw CatalogErrors.invalidField("accreditedOn", "must not be in the future");
        }
        CommunityAssociation association = associationRepository.findById(request.associationId())
                .orElseThrow(() -> CatalogErrors.notFound("Association"));
        if (!association.isActive()) {
            throw CatalogErrors.hostProfileConflict("The association is not active");
        }
        if (linkManagerRepository.existsByUserIdAndDeletedAtIsNull(request.userId())) {
            throw CatalogErrors.managerExists();
        }
        // Valida que el usuario exista y esté activo; otorga ROLE_LINK_MANAGER en esta misma transacción.
        roleAssignmentService.grant(request.userId(), RoleCode.LINK_MANAGER);
        try {
            return LinkManagerResponse.from(linkManagerRepository.saveAndFlush(LinkManagerProfile.accredit(
                    request.userId(), association.getId(), request.accreditedOn(), adminUserId)));
        } catch (DataIntegrityViolationException e) {
            // Carrera entre dos acreditaciones del mismo usuario: decide uq_link_manager_user_active.
            throw CatalogErrors.managerExists();
        }
    }

    @Override
    @Transactional
    public LinkManagerResponse changeStatus(UUID linkManagerId, AccreditationStatus target) {
        LinkManagerProfile profile = linkManagerRepository.findById(linkManagerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> CatalogErrors.notFound("Link manager"));
        if (!profile.canMoveTo(target)) {
            throw new BusinessRuleException(ErrorCode.CATALOG_INVALID_STATUS_TRANSITION,
                    "Cannot move an accreditation from " + profile.getAccreditationStatus() + " to " + target)
                    .with("currentStatus", profile.getAccreditationStatus().name());
        }
        profile.moveTo(target);
        return LinkManagerResponse.from(profile);
    }
}
