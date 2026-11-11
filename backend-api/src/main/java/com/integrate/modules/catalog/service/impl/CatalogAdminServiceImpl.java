package com.integrate.modules.catalog.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.catalog.dto.request.AssociationRequest;
import com.integrate.modules.catalog.dto.request.HostProfileRequest;
import com.integrate.modules.catalog.dto.response.AssociationResponse;
import com.integrate.modules.catalog.dto.response.HostProfileResponse;
import com.integrate.modules.catalog.entity.AssociationStatus;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.repository.CommunityAssociationRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.MunicipalityRepository;
import com.integrate.modules.catalog.service.CatalogAdminService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
    private final AuditTrail auditTrail;

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
    @Transactional
    public AssociationResponse updateAssociation(UUID id, AssociationRequest request, UUID adminUserId) {
        CommunityAssociation association = requireActiveNotDeleted(id);
        if (!municipalityRepository.existsById(request.municipalityCode())) {
            throw CatalogErrors.invalidField("municipalityCode", "unknown DIVIPOLA municipality code");
        }
        if (associationRepository.existsByIdNotAndNit(id, request.nit())
                || associationRepository.existsByIdNotAndRntNumber(id, request.rntNumber())) {
            throw associationExists();
        }
        association.update(request.nit(), request.rntNumber(), request.legalName().strip(), request.associationType(),
                request.municipalityCode(),
                request.address() == null || request.address().isBlank() ? null : request.address().strip(),
                request.contactEmail().strip().toLowerCase(Locale.ROOT), request.contactPhone());
        AssociationResponse response;
        try {
            response = AssociationResponse.from(associationRepository.saveAndFlush(association));
        } catch (DataIntegrityViolationException e) {
            throw associationExists();
        }
        auditTrail.record(new AuditEntry("catalog", "community_associations", association.getId().toString(),
                "ASSOCIATION_UPDATED", Severity.INFO, adminUserId, null, null,
                Map.of("legalName", association.getLegalName(), "nit", association.getNit(),
                        "rntNumber", association.getRntNumber())));
        return response;
    }

    @Override
    @Transactional
    public AssociationResponse suspendAssociation(UUID id, UUID adminUserId) {
        CommunityAssociation association = requireActiveNotDeleted(id);
        if (association.getStatus() == AssociationStatus.SUSPENDED) {
            throw invalidAssociationTransition(association.getStatus(), "suspend");
        }
        association.suspend();
        AssociationResponse response = AssociationResponse.from(associationRepository.saveAndFlush(association));
        auditTrail.record(new AuditEntry("catalog", "community_associations", association.getId().toString(),
                "ASSOCIATION_SUSPENDED", Severity.WARNING, adminUserId, null, null,
                Map.of("legalName", association.getLegalName(), "status", AssociationStatus.SUSPENDED.name())));
        return response;
    }

    @Override
    @Transactional
    public AssociationResponse activateAssociation(UUID id, UUID adminUserId) {
        CommunityAssociation association = requireActiveNotDeleted(id);
        if (association.getStatus() == AssociationStatus.ACTIVE) {
            throw invalidAssociationTransition(association.getStatus(), "activate");
        }
        association.activate();
        AssociationResponse response = AssociationResponse.from(associationRepository.saveAndFlush(association));
        auditTrail.record(new AuditEntry("catalog", "community_associations", association.getId().toString(),
                "ASSOCIATION_ACTIVATED", Severity.INFO, adminUserId, null, null,
                Map.of("legalName", association.getLegalName(), "status", AssociationStatus.ACTIVE.name())));
        return response;
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
        // Todo anfitrión es también turista (ADR-0064); grant es idempotente.
        roleAssignmentService.grant(request.userId(), RoleCode.TOURIST);
        HostProfile profile;
        try {
            profile = hostProfileRepository.saveAndFlush(HostProfile.create(request.userId(), association.getId()));
        } catch (DataIntegrityViolationException e) {
            throw CatalogErrors.hostProfileConflict("The user already has an active host profile");
        }
        return new HostProfileResponse(profile.getId(), profile.getUserId(), profile.getAssociationId());
    }

    private CommunityAssociation requireActiveNotDeleted(UUID id) {
        return associationRepository.findById(id)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> CatalogErrors.notFound("Association"));
    }

    private static BusinessRuleException invalidAssociationTransition(AssociationStatus from, String action) {
        return new BusinessRuleException(ErrorCode.CATALOG_INVALID_STATUS_TRANSITION,
                "Cannot " + action + " an association in status " + from)
                .with("currentStatus", from.name());
    }

    private static BusinessRuleException associationExists() {
        return new BusinessRuleException(ErrorCode.CATALOG_ASSOCIATION_EXISTS, "NIT or RNT already registered");
    }
}
