package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.ConsentService;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.ConsentStatus.PurposeConsent;
import com.integrate.modules.auth.service.model.ConsentStatus.State;
import com.integrate.modules.auth.service.model.HealthConsentGrant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentServiceImpl implements ConsentService {

    private final ConsentLedger ledger;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public ConsentStatus status(UUID userId, Collection<RoleCode> roles) {
        return ledger.status(userId, roles, ledger.currentPolicy());
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentStatus status(UUID userId) {
        return status(userId, userRoleRepository.findActiveRoleCodes(userId));
    }

    @Override
    @Transactional
    public ConsentStatus grant(UUID userId, ConsentAcceptance acceptance, ClientInfo client) {
        List<RoleCode> roles = userRoleRepository.findActiveRoleCodes(userId);
        String noticeVersion = acceptance.healthNoticeVersion();
        boolean withNotice = noticeVersion != null;
        if (withNotice) {
            if (!acceptance.purposes().contains(ConsentPurpose.HEALTH_EMERGENCY)) {
                throw AuthErrors.invalidField("healthNoticeVersion", "only applies to the HEALTH_EMERGENCY purpose");
            }
            // Mismo orden que currentHealthGrant: candado del titular y política retenida antes de leer o insertar.
            ledger.lockHealthConsent(userId);
        }
        PrivacyPolicy policy = withNotice
                ? ledger.currentPolicyLockedForHealth()
                        .filter(current -> current.getVersion().equals(acceptance.policyVersion()))
                        .orElseGet(() -> ledger.requireCurrentPolicy(acceptance.policyVersion(), "policyVersion"))
                : ledger.requireCurrentPolicy(acceptance.policyVersion(), "policyVersion");
        ledger.requireApplicable(acceptance.purposes(), roles, "purposes");
        if (withNotice) {
            ledger.requireApprovedNotice(policy, noticeVersion, "healthNoticeVersion");
        }
        // Idempotente: las finalidades ya vigentes no generan un acto nuevo. Un acto de salud sin el aviso
        // pedido no basta: la EPS exige el acto que acredita ese aviso.
        Set<ConsentPurpose> alreadyValid = ledger.status(userId, roles, Optional.of(policy)).purposes().stream()
                .filter(p -> p.state() == State.VALID)
                .filter(p -> !withNotice || p.purpose() != ConsentPurpose.HEALTH_EMERGENCY
                        || noticeVersion.equals(p.healthNoticeVersion()))
                .map(PurposeConsent::purpose)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConsentPurpose.class)));
        ledger.insertGrants(userId, policy, acceptance.purposes(), alreadyValid, client, noticeVersion);
        return ledger.status(userId, roles, Optional.of(policy));
    }

    @Override
    @Transactional
    public Optional<HealthConsentGrant> currentHealthGrant(UUID userId) {
        return ledger.currentHealthGrant(userId);
    }
}
