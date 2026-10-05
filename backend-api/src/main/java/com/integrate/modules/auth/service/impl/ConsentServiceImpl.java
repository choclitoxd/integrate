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
        PrivacyPolicy policy = ledger.requireCurrentPolicy(acceptance.policyVersion(), "policyVersion");
        ledger.requireApplicable(acceptance.purposes(), roles, "purposes");
        // Idempotente: las finalidades ya vigentes no generan un acto nuevo.
        Set<ConsentPurpose> alreadyValid = ledger.status(userId, roles, Optional.of(policy)).purposes().stream()
                .filter(p -> p.state() == State.VALID)
                .map(PurposeConsent::purpose)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConsentPurpose.class)));
        ledger.insertGrants(userId, policy, acceptance.purposes(), alreadyValid, client);
        return ledger.status(userId, roles, Optional.of(policy));
    }
}
