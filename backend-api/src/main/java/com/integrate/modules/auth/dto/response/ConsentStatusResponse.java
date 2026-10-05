package com.integrate.modules.auth.dto.response;

import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.service.model.ConsentStatus;
import java.time.Instant;
import java.util.List;

/** Respuesta de {@code GET/POST /api/v1/me/consents} (ADR-0005 §6). */
public record ConsentStatusResponse(String currentPolicyVersion, List<PurposeResponse> consents,
                                    boolean requiresConsentUpdate, List<ConsentPurpose> pendingPurposes) {

    public static ConsentStatusResponse from(ConsentStatus status) {
        List<PurposeResponse> consents = status.purposes().stream()
                .map(p -> new PurposeResponse(p.purpose(), p.state(), p.policyVersion(), p.occurredAt()))
                .toList();
        return new ConsentStatusResponse(status.currentPolicyVersion(), consents, status.requiresConsentUpdate(),
                status.pendingPurposes());
    }

    /**
     * @param policyVersion versión sobre la que se dio el último acto ({@code null} si {@code MISSING})
     * @param occurredAt    instante UTC del último acto ({@code null} si {@code MISSING})
     */
    public record PurposeResponse(ConsentPurpose purpose, ConsentStatus.State state, String policyVersion,
                                  Instant occurredAt) {
    }
}
