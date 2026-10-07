package com.integrate.modules.catalog.service.impl;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.CredentialActivationService;
import com.integrate.modules.auth.service.CredentialActivationService.IssuedActivationCode;
import com.integrate.modules.auth.service.UserDirectory;
import com.integrate.modules.auth.service.UserDirectory.UserSummary;
import com.integrate.modules.catalog.dto.response.ActivationCodeResponse;
import com.integrate.modules.catalog.dto.response.ManagedHostResponse;
import com.integrate.modules.catalog.entity.HostManagerAssignment;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import com.integrate.modules.catalog.repository.HostManagerAssignmentRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.service.GestorHostService;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GestorHostServiceImpl implements GestorHostService {

    private static final String SYNTHETIC_SUFFIX = "@" + AssistedAccountService.SYNTHETIC_EMAIL_DOMAIN;

    private final LinkManagers linkManagers;
    private final HostManagerAssignmentRepository assignmentRepository;
    private final HostProfileRepository hostProfileRepository;
    private final UserDirectory userDirectory;
    private final CredentialActivationService activationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ManagedHostResponse> listHosts(UUID managerUserId, Pageable pageable) {
        LinkManagerProfile manager = linkManagers.requireActive(managerUserId);
        Page<HostManagerAssignment> assignments = assignmentRepository.findByManagerIdAndValidToIsNull(manager.getId(),
                pageable);
        Map<UUID, HostProfile> profiles = hostProfileRepository
                .findAllById(assignments.map(HostManagerAssignment::getHostId).toList()).stream()
                .collect(Collectors.toMap(HostProfile::getId, Function.identity()));
        Map<UUID, UserSummary> users = userDirectory.findSummaries(
                profiles.values().stream().map(HostProfile::getUserId).toList());
        return PageResponse.of(assignments, a -> {
            HostProfile profile = profiles.get(a.getHostId());
            UserSummary user = users.get(profile.getUserId());
            return new ManagedHostResponse(profile.getId(), user.userId(), user.fullName(), user.email(),
                    user.email().endsWith(SYNTHETIC_SUFFIX), user.phone(), user.docType(), last4(user.docNumber()),
                    user.credentialStatus(), a.getValidFrom());
        });
    }

    @Override
    @Transactional
    public ActivationCodeResponse issueActivationCode(UUID managerUserId, UUID hostProfileId) {
        LinkManagerProfile manager = linkManagers.requireActive(managerUserId);
        // 404 también para anfitriones de otra jurisdicción: no revela qué identificadores existen.
        HostProfile profile = hostProfileRepository.findById(hostProfileId)
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> assignmentRepository.existsByHostIdAndManagerIdAndValidToIsNull(p.getId(), manager.getId()))
                .orElseThrow(() -> CatalogErrors.notFound("Host"));
        IssuedActivationCode issued = activationService.issue(profile.getUserId(), managerUserId);
        return new ActivationCodeResponse(issued.code(), issued.expiresAt());
    }

    private static String last4(String docNumber) {
        return docNumber.length() <= 4 ? docNumber : docNumber.substring(docNumber.length() - 4);
    }
}
