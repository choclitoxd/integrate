package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.ConsentAction;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.HabeasDataConsent;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.repository.HabeasDataConsentRepository;
import com.integrate.modules.auth.repository.HealthConsentNoticeRepository;
import com.integrate.modules.auth.repository.PrivacyPolicyRepository;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.HealthConsentGrant;
import com.integrate.modules.auth.service.model.HealthNotice;
import com.integrate.modules.auth.service.model.ConsentStatus.PurposeConsent;
import com.integrate.modules.auth.service.model.ConsentStatus.State;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final HealthConsentNoticeRepository noticeRepository;
    private final Clock clock;

    @Value("${integrate.auth.admin-consent-enforcement-enabled}")
    private boolean enforcementEnabled;

    Optional<PrivacyPolicy> currentPolicy() {
        return policyRepository.findByCurrentTrue()
                .filter(policy -> !enforcementEnabled || canBePublished(policy.getDocumentUrl()));
    }

    /** Aviso de salud aprobado para comunicar la EPS al anfitrión en la política dada; vacío si no hay ninguno (ADR-0060). */
    Optional<HealthNotice> approvedHealthNotice(PrivacyPolicy policy) {
        return noticeRepository.findByPolicyIdAndApprovedForHostTrue(policy.getId())
                .map(notice -> notice.toNotice());
    }

    /**
     * Valida la versión del aviso enviada con el acto de salud: debe ser la del aviso aprobado de la política
     * vigente. Sin aviso aprobado la captura de EPS no está disponible y ninguna versión es válida.
     */
    HealthNotice requireApprovedNotice(PrivacyPolicy policy, String noticeVersion, String field) {
        return approvedHealthNotice(policy)
                .filter(notice -> notice.noticeVersion().equals(noticeVersion))
                .orElseThrow(() -> AuthErrors.invalidField(field, "must be the approved health notice of the current policy"));
    }

    /** Serializa con la inserción de actos de salud del titular (trigger de V19) hasta el fin de la transacción. */
    void lockHealthConsent(UUID userId) {
        consentRepository.lockHealthConsent(userId);
    }

    /** Política vigente retenida hasta el fin de la transacción, con el mismo criterio de publicación que {@link #currentPolicy()}. */
    Optional<PrivacyPolicy> currentPolicyLockedForHealth() {
        return policyRepository.lockCurrentForHealthConsent()
                .filter(policy -> !enforcementEnabled || canBePublished(policy.getDocumentUrl()));
    }

    /**
     * Acto de salud vigente del titular que acredita el aviso aprobado de la política vigente; vacío si fue revocado,
     * no registra aviso, es de otra política o el aviso no está aprobado.
     */
    Optional<HealthConsentGrant> currentHealthGrant(UUID userId) {
        lockHealthConsent(userId);
        return currentPolicyLockedForHealth().flatMap(policy ->
                consentRepository.findFirstByUserIdAndPurposeOrderByOccurredAtDescIdDesc(userId,
                                ConsentPurpose.HEALTH_EMERGENCY)
                        .filter(last -> last.getAction() == ConsentAction.GRANTED
                                && last.getHealthNoticeVersion() != null
                                && policy.getId().equals(last.getPolicyId()))
                        .flatMap(last -> approvedHealthNotice(policy)
                                .filter(notice -> notice.noticeVersion().equals(last.getHealthNoticeVersion()))
                                .map(notice -> new HealthConsentGrant(last.getId(), notice))));
    }

    private static boolean canBePublished(String documentUrl) {
        try {
            URI url = URI.create(documentUrl);
            if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null) {
                return false;
            }
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            return !host.equals("invalid") && !host.endsWith(".invalid");
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
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
        insertGrants(userId, policy, purposes, skip, client, null);
    }

    /** Como arriba; {@code healthNoticeVersion} solo se registra en el acto {@code HEALTH_EMERGENCY}. */
    void insertGrants(UUID userId, PrivacyPolicy policy, Set<ConsentPurpose> purposes, Set<ConsentPurpose> skip,
                      ClientInfo client, String healthNoticeVersion) {
        Instant now = clock.instant();
        // EnumSet: orden de inserción estable y traza reproducible.
        EnumSet<ConsentPurpose> toInsert = EnumSet.noneOf(ConsentPurpose.class);
        toInsert.addAll(purposes);
        toInsert.removeAll(skip);
        consentRepository.saveAll(toInsert.stream()
                .map(p -> HabeasDataConsent.selfServiceGrant(userId, policy, p, client.ipAddress(),
                        client.userAgent(), now, p == ConsentPurpose.HEALTH_EMERGENCY ? healthNoticeVersion : null))
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
                            last.getOccurredAt(), last.getHealthNoticeVersion()));
        }
        Map<ConsentPurpose, State> stateByPurpose = purposes.stream()
                .collect(Collectors.toMap(PurposeConsent::purpose, PurposeConsent::state));
        // Sin política vigente no hay nada que aceptar: no se marca nada como pendiente.
        List<ConsentPurpose> pending = currentId == null ? List.of()
                : requiredPurposes(roles, latest.keySet()).stream()
                        .filter(p -> stateByPurpose.get(p) != State.VALID).toList();
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
     * Finalidades exigidas para operar (§7, enmendado por ADR-0068): {@code PERSONAL_DATA} y, para
     * {@code HOST}, {@code SENSITIVE_VICTIM} solo si la persona ya tiene constancia de ese consentimiento
     * (un acto en cualquier estado: vigente, desactualizado o revocado). Un anfitrión sin datos de víctima
     * no tiene qué autorizar y no queda bloqueado. {@code HEALTH_EMERGENCY} es precondición del lock, no del login.
     *
     * @param withRecord finalidades de las que el usuario tiene al menos un acto en su historial
     */
    static Set<ConsentPurpose> requiredPurposes(Collection<RoleCode> roles, Collection<ConsentPurpose> withRecord) {
        EnumSet<ConsentPurpose> purposes = EnumSet.of(ConsentPurpose.PERSONAL_DATA);
        if (roles.contains(RoleCode.HOST) && withRecord.contains(ConsentPurpose.SENSITIVE_VICTIM)) {
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
