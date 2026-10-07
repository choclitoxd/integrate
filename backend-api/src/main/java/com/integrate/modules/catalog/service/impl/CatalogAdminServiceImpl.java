package com.integrate.modules.catalog.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.catalog.dto.request.AssociationRequest;
import com.integrate.modules.catalog.dto.request.HostProfileRequest;
import com.integrate.modules.catalog.dto.response.AssociationResponse;
import com.integrate.modules.catalog.dto.response.HostProfileResponse;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.repository.CommunityAssociationRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.service.CatalogAdminService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogAdminServiceImpl implements CatalogAdminService {

    private final CommunityAssociationRepository associationRepository;
    private final HostProfileRepository hostProfileRepository;
    private final MunicipalityRepository municipalityRepository;
    private final RoleAssignmentService roleAssignmentService;

    @Override
    @Transactional
    public AssociationResponse createAssociation(AssociationRequest request) {
        if (!municipalityRepository.existsById(request.municipalityCode())) {
            throw CatalogErrors.invalidField("municipalityCode", "unknown DIVIPOLA municipality code");
        }
        if (associationRepository.existsByNitOrRntNumber(request.nit(), request.rntNumber())) {
            throw associationExists();
        }
        CommunityAssociation association = CommunityAssociation.create(request.nit(), request.rntNumber(),
                request.legalName().strip(), request.associationType(), request.municipalityCode(),
                request.address() == null || request.address().isBlank() ? null : request.address().strip(),
                request.contactEmail().strip().toLowerCase(Locale.ROOT), request.contactPhone());
        try {
            return AssociationResponse.from(associationRepository.saveAndFlush(association));
        } catch (DataIntegrityViolationException e) {
            // Carrera entre dos altas con el mismo NIT o RNT: decide el índice único.
            throw associationExists();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssociationResponse> listAssociations(Pageable pageable) {
        return PageResponse.of(associationRepository.findByDeletedAtIsNull(pageable), AssociationResponse::from);
    }

    @Override
    @Transactional
    public HostProfileResponse createHostProfile(HostProfileRequest request) {
        CommunityAssociation association = associationRepository.findById(request.associationId())
                .orElseThrow(() -> CatalogErrors.notFound("Association"));
        if (!association.isActive()) {
            throw CatalogErrors.hostProfileConflict("The association is not active");
        }
        if (hostProfileRepository.existsByUserIdAndDeletedAtIsNull(request.userId())) {
            throw CatalogErrors.hostProfileConflict("The user already has an active host profile");
        }
        // Valida que el usuario exista y esté activo; otorga ROLE_HOST en esta misma transacción.
        roleAssignmentService.grant(request.userId(), RoleCode.HOST);
        HostProfile profile;
        try {
            profile = hostProfileRepository.saveAndFlush(HostProfile.create(request.userId(), association.getId()));
        } catch (DataIntegrityViolationException e) {
            throw CatalogErrors.hostProfileConflict("The user already has an active host profile");
        }
        return new HostProfileResponse(profile.getId(), profile.getUserId(), profile.getAssociationId());
    }

    private static BusinessRuleException associationExists() {
        return new BusinessRuleException(ErrorCode.CATALOG_ASSOCIATION_EXISTS, "NIT or RNT already registered");
    }
}
