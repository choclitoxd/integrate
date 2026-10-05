package com.integrate.modules.auth.service.model;

import com.integrate.modules.auth.entity.ConsentPurpose;
import java.time.Instant;
import java.util.List;

/**
 * Estado de consentimiento del titular frente a la política vigente (RF-02, ADR-0005 §6-§7).
 *
 * @param currentPolicyVersion versión vigente, o {@code null} si no hay ninguna publicada
 * @param purposes             estado de cada finalidad aplicable a los roles del titular
 * @param pendingPurposes      finalidades <em>exigidas</em> por rol que no están vigentes
 */
public record ConsentStatus(String currentPolicyVersion, List<PurposeConsent> purposes,
                            List<ConsentPurpose> pendingPurposes) {

    public ConsentStatus {
        purposes = List.copyOf(purposes);
        pendingPurposes = List.copyOf(pendingPurposes);
    }

    public boolean requiresConsentUpdate() {
        return !pendingPurposes.isEmpty();
    }

    /** Versión que el titular debe aceptar, o {@code null} si no hay nada pendiente. */
    public String pendingPolicyVersion() {
        return requiresConsentUpdate() ? currentPolicyVersion : null;
    }

    public enum State {
        /** Otorgado sobre la política vigente. */
        VALID,
        /** Otorgado sobre una versión anterior: debe revalidarse. */
        OUTDATED,
        /** El último acto fue una revocación. */
        REVOKED,
        /** Nunca otorgado. */
        MISSING
    }

    public record PurposeConsent(ConsentPurpose purpose, State state, String policyVersion, Instant occurredAt) {
    }
}
