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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    /** Correo sintético opaco de ADR-0038: {@code h-} y 10 caracteres del alfabeto sin I, O, 0 ni 1. */
    private static final Pattern OPAQUE_SYNTHETIC_LOCAL =
            Pattern.compile("^h-[a-hj-np-z2-9]{10}" + Pattern.quote(SYNTHETIC_SUFFIX) + "$");

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
            boolean synthetic = user.email().endsWith(SYNTHETIC_SUFFIX);
            return new ManagedHostResponse(profile.getId(), user.userId(), user.fullName(),
                    shownEmail(user, synthetic), synthetic, user.phone(), user.docType(), last4(user.docNumber()),
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

    /**
     * Los correos sintéticos anteriores a ADR-0038 son {@code {tipo}{documento}@…}: mostrarlos completos revelaría el
     * número de documento que la lista ya enmascara. Se muestran como {@code cc••••1142@…}; el correo opaco nuevo
     * (que no contiene ningún dato personal) se muestra tal cual.
     */
    private static String shownEmail(UserSummary user, boolean synthetic) {
        if (!synthetic || OPAQUE_SYNTHETIC_LOCAL.matcher(user.email()).matches()) {
            return user.email();
        }
        return user.docType().name().toLowerCase(Locale.ROOT) + "••••" + last4(user.docNumber()) + SYNTHETIC_SUFFIX;
    }

    private static String last4(String docNumber) {
        return docNumber.length() <= 4 ? docNumber : docNumber.substring(docNumber.length() - 4);
    }
}
