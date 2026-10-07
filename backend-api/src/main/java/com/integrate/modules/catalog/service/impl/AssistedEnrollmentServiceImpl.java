package com.integrate.modules.catalog.service.impl;

import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.InvalidParam;
import com.integrate.common.storage.DocumentStorage.PresignedUpload;
import com.integrate.modules.auth.entity.ConsentChannel;
import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.model.AssistedHostAccount;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ProvisionedHost;
import com.integrate.modules.catalog.dto.request.AssistedHostRegistrationRequest;
import com.integrate.modules.catalog.dto.request.AssistedHostRegistrationRequest.VictimData;
import com.integrate.modules.catalog.dto.request.DocumentKind;
import com.integrate.modules.catalog.dto.response.AssistedHostRegistrationResponse;
import com.integrate.modules.catalog.dto.response.AssistedHostRegistrationResponse.ConsentReceipt;
import com.integrate.modules.catalog.dto.response.DocumentUploadResponse;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import com.integrate.modules.catalog.entity.HostManagerAssignment;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.entity.HostProfile.VictimCiphertexts;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import com.integrate.modules.catalog.repository.CommunityAssociationRepository;
import com.integrate.modules.catalog.repository.HostManagerAssignmentRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.LinkManagerProfileRepository;
import com.integrate.modules.catalog.service.AssistedEnrollmentService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssistedEnrollmentServiceImpl implements AssistedEnrollmentService {

    private final LinkManagers linkManagers;
    private final LinkManagerProfileRepository linkManagerRepository;
    private final CommunityAssociationRepository associationRepository;
    private final HostProfileRepository hostProfileRepository;
    private final HostManagerAssignmentRepository assignmentRepository;
    private final EnrollmentDocuments documents;
    private final SensitiveDataCipher cipher;
    private final AssistedAccountService accountService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DocumentUploadResponse presignUpload(UUID actorUserId, boolean actorIsAdmin, DocumentKind kind) {
        if (!actorIsAdmin) {
            linkManagers.requireActive(actorUserId);
        }
        String key = documents.newKey(kind);
        PresignedUpload upload = documents.presign(key);
        return new DocumentUploadResponse(key, upload.url(), EnrollmentDocuments.CONTENT_TYPE,
                EnrollmentDocuments.MAX_SIZE_BYTES, upload.expiresAt());
    }

    @Override
    @Transactional
    public AssistedHostRegistrationResponse register(UUID actorUserId, boolean actorIsAdmin,
                                                     AssistedHostRegistrationRequest request, ClientInfo client) {
        // 1. Gestor efectivo y asociación (la del gestor: misma asociación por construcción, RF-03).
        LinkManagerProfile manager = actorIsAdmin ? managerChosenByAdmin(request) : linkManagers.requireActive(actorUserId);
        CommunityAssociation association = associationRepository.findById(manager.getAssociationId())
                .orElseThrow(() -> CatalogErrors.notFound("Association"));
        if (!association.isActive()) {
            throw CatalogErrors.hostProfileConflict("The association is not active");
        }

        // 2. Validaciones sin escrituras: coherencia de los datos de víctima y documentos en S3.
        VictimData victim = request.victimData();
        requireCoherent(victim);
        documents.requireValid(DocumentKind.PHYSICAL_CONSENT_FORM, request.consent().physicalConsentFormS3Key(),
                "consent.physicalConsentFormS3Key");
        String supportKey = blankToNull(victim.supportDocS3Key());
        if (supportKey != null) {
            documents.requireValid(DocumentKind.VICTIM_SUPPORT_DOC, supportKey, "victimData.supportDocS3Key");
            if (hostProfileRepository.existsBySupportDocS3Key(supportKey)) {
                throw CatalogErrors.documentInvalid("victimData.supportDocS3Key", "already in use");
            }
        }

        // 3. Cuenta, rol HOST y consentimiento ASSISTED_PROXY (interfaz pública de auth).
        ProvisionedHost account = accountService.provisionHost(new AssistedHostAccount(request.email(),
                request.fullName(), request.docType(), request.docNumber(), request.phone(),
                request.consent().policyVersion(), request.consent().purposes(),
                request.consent().physicalConsentFormS3Key(), actorUserId), client);

        // 4. Perfil con datos de víctima cifrados (AAD = fila y columna) y vínculo con el gestor.
        UUID hostProfileId = UUID.randomUUID();
        HostProfile profile = hostProfileRepository.saveAndFlush(HostProfile.enroll(hostProfileId, account.userId(),
                association.getId(), encrypt(hostProfileId, victim, supportKey)));
        HostManagerAssignment assignment = assignmentRepository.saveAndFlush(HostManagerAssignment.start(
                profile.getId(), manager.getId(), association.getId(), clock.instant()));

        return new AssistedHostRegistrationResponse(account.userId(), profile.getId(), association.getId(),
                assignment.getId(), manager.getId(), account.email(), account.emailIsSynthetic(), account.roles(),
                account.credentialStatus(), new ConsentReceipt(ConsentChannel.ASSISTED_PROXY, account.policyVersion(),
                account.purposes(), account.capturedBy(), account.capturedAt()));
    }

    /** {@code linkManagerId} es obligatorio si invoca un administrador; el gestor indicado debe estar activo. */
    private LinkManagerProfile managerChosenByAdmin(AssistedHostRegistrationRequest request) {
        if (request.linkManagerId() == null) {
            throw CatalogErrors.invalidField("linkManagerId", "is required when an administrator registers a host");
        }
        LinkManagerProfile manager = linkManagerRepository.findById(request.linkManagerId())
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> CatalogErrors.notFound("Link manager"));
        if (!manager.isActive()) {
            throw new BusinessRuleException(ErrorCode.CATALOG_INVALID_STATUS_TRANSITION,
                    "The link manager accreditation is " + manager.getAccreditationStatus())
                    .with("currentStatus", manager.getAccreditationStatus().name());
        }
        return manager;
    }

    /** Sin condición de víctima no hay detalle que guardar ({@code ck_host_profiles_victim_detail}). */
    private static void requireCoherent(VictimData victim) {
        if (Boolean.TRUE.equals(victim.victimCondition())) {
            return;
        }
        List<InvalidParam> params = new ArrayList<>();
        if (blankToNull(victim.ruvRegistrationNumber()) != null) {
            params.add(new InvalidParam("victimData.ruvRegistrationNumber", "must be empty when victimCondition is false"));
        }
        if (blankToNull(victim.victimizationType()) != null) {
            params.add(new InvalidParam("victimData.victimizationType", "must be empty when victimCondition is false"));
        }
        if (blankToNull(victim.supportDocS3Key()) != null) {
            params.add(new InvalidParam("victimData.supportDocS3Key", "must be empty when victimCondition is false"));
        }
        if (!params.isEmpty()) {
            throw CatalogErrors.invalid(params);
        }
    }

    private VictimCiphertexts encrypt(UUID hostProfileId, VictimData victim, String supportKey) {
        short version = cipher.currentKeyVersion();
        return new VictimCiphertexts(
                seal("victim_condition_enc", hostProfileId, victim.victimCondition().toString(), version),
                seal("ruv_registration_number_enc", hostProfileId, blankToNull(victim.ruvRegistrationNumber()), version),
                seal("victimization_type_enc", hostProfileId, blankToNull(victim.victimizationType()), version),
                supportKey, version);
    }

    private byte[] seal(String column, UUID hostProfileId, String plaintext, short version) {
        return plaintext == null ? null : cipher.encrypt(plaintext, HostProfile.aad(column, hostProfileId), version);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
