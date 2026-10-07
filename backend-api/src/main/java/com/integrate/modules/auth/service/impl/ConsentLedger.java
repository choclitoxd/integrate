package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.ConsentAction;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.HabeasDataConsent;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.repository.HabeasDataConsentRepository;
import com.integrate.modules.auth.repository.PrivacyPolicyRepository;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.ConsentStatus.PurposeConsent;
import com.integrate.modules.auth.service.model.ConsentStatus.State;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reglas de consentimiento compartidas por el registro, el login y {@code /me/consents}
 * (RF-02, ADR-0005 §6-§7). Sin transacción propia: participa en la del servicio que la invoca.
 */
@Component
@RequiredArgsConstructor
class ConsentLedger {

    private final HabeasDataConsentRepository consentRepository;
    private final PrivacyPolicyRepository policyRepository;
    private final Clock clock;

    Optional<PrivacyPolicy> currentPolicy() {
        return policyRepository.findByCurrentTrue();
    }

    /** Valida que {@code version} sea la vigente; {@code field} es la ruta del campo para {@code invalidParams}. */
    PrivacyPolicy requireCurrentPolicy(String version, String field) {
        PrivacyPolicy current = currentPolicy()
                .orElseThrow(() -> AuthErrors.invalidField(field, "no privacy policy is currently in force"));
        if (!current.getVersion().equals(version)) {
            throw AuthErrors.invalidField(field, "must be the current policy version " + current.getVersion());
        }
        return current;
    }

    void requireApplicable(Set<ConsentPurpose> purposes, Collection<RoleCode> roles, String field) {
        Set<ConsentPurpose> applicable = applicablePurposes(roles);
        if (!applicable.containsAll(purposes)) {
            throw AuthErrors.invalidField(field, "only " + applicable + " apply to this account");
        }
    }

    /** Inserta un acto {@code GRANTED / SELF_SERVICE} por finalidad, salvo las de {@code skip}. */
    void insertGrants(UUID userId, PrivacyPolicy policy, Set<ConsentPurpose> purposes, Set<ConsentPurpose> skip,
                      ClientInfo client) {
        Instant now = clock.instant();
        // EnumSet: orden de inserción estable y traza reproducible.
        EnumSet<ConsentPurpose> toInsert = EnumSet.noneOf(ConsentPurpose.class);
        toInsert.addAll(purposes);
        toInsert.removeAll(skip);
        consentRepository.saveAll(toInsert.stream()
                .map(p -> HabeasDataConsent.selfServiceGrant(userId, policy, p, client.ipAddress(),
                        client.userAgent(), now))
                .toList());
    }

    /** Inserta un acto {@code GRANTED / ASSISTED_PROXY} por finalidad (ADR-0007 §3). */
    Instant insertAssistedGrants(UUID userId, PrivacyPolicy policy, Set<ConsentPurpose> purposes, UUID capturedBy,
                                 String physicalConsentFormS3Key, ClientInfo client) {
        Instant now = clock.instant();
        consentRepository.saveAll(EnumSet.copyOf(purposes).stream()
                .map(p -> HabeasDataConsent.assistedProxyGrant(userId, policy, p, capturedBy, physicalConsentFormS3Key,
                        client.ipAddress(), client.userAgent(), now))
                .toList());
        return now;
    }

    boolean isConsentFormInUse(String physicalConsentFormS3Key) {
        return consentRepository.existsByPhysicalConsentFormS3Key(physicalConsentFormS3Key);
    }

    ConsentStatus status(UUID userId, Collection<RoleCode> roles, Optional<PrivacyPolicy> current) {
        // Primer registro por finalidad = último acto (mismo criterio que auth.v_current_consents).
        Map<ConsentPurpose, HabeasDataConsent> latest = new EnumMap<>(ConsentPurpose.class);
        for (HabeasDataConsent consent : consentRepository.findByUserIdOrderByOccurredAtDescIdDesc(userId)) {
            latest.putIfAbsent(consent.getPurpose(), consent);
        }
        Map<Short, String> versions = policyRepository
                .findAllById(latest.values().stream().map(HabeasDataConsent::getPolicyId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PrivacyPolicy::getId, PrivacyPolicy::getVersion));
        Short currentId = current.map(PrivacyPolicy::getId).orElse(null);

        List<PurposeConsent> purposes = new ArrayList<>();
        for (ConsentPurpose purpose : applicablePurposes(roles)) {
            HabeasDataConsent last = latest.get(purpose);
            purposes.add(last == null
                    ? new PurposeConsent(purpose, State.MISSING, null, null)
                    : new PurposeConsent(purpose, stateOf(last, currentId), versions.get(last.getPolicyId()),
                            last.getOccurredAt()));
        }
        Map<ConsentPurpose, State> stateByPurpose = purposes.stream()
                .collect(Collectors.toMap(PurposeConsent::purpose, PurposeConsent::state));
        // Sin política vigente no hay nada que aceptar: no se marca nada como pendiente.
        List<ConsentPurpose> pending = currentId == null ? List.of()
                : requiredPurposes(roles).stream().filter(p -> stateByPurpose.get(p) != State.VALID).toList();
        return new ConsentStatus(current.map(PrivacyPolicy::getVersion).orElse(null), purposes, pending);
    }

    /** Finalidades que el titular puede otorgar: {@code SENSITIVE_VICTIM} solo con {@code HOST}. */
    static Set<ConsentPurpose> applicablePurposes(Collection<RoleCode> roles) {
        EnumSet<ConsentPurpose> purposes = EnumSet.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.HEALTH_EMERGENCY);
        if (roles.contains(RoleCode.HOST)) {
            purposes.add(ConsentPurpose.SENSITIVE_VICTIM);
        }
        return purposes;
    }

    /**
     * Finalidades exigidas para operar (§7): {@code PERSONAL_DATA} y, para {@code HOST},
     * {@code SENSITIVE_VICTIM}. {@code HEALTH_EMERGENCY} es precondición del lock, no del login.
     */
    static Set<ConsentPurpose> requiredPurposes(Collection<RoleCode> roles) {
        EnumSet<ConsentPurpose> purposes = EnumSet.of(ConsentPurpose.PERSONAL_DATA);
        if (roles.contains(RoleCode.HOST)) {
            purposes.add(ConsentPurpose.SENSITIVE_VICTIM);
        }
        return purposes;
    }

    private static State stateOf(HabeasDataConsent last, Short currentPolicyId) {
        if (last.getAction() == ConsentAction.REVOKED) {
            return State.REVOKED;
        }
        return last.getPolicyId().equals(currentPolicyId) ? State.VALID : State.OUTDATED;
    }
}
