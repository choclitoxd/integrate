package com.integrate.modules.auth.dto.response;

import com.integrate.modules.auth.service.model.PublishedPolicy;
import java.time.Instant;

/** Respuesta de {@code GET /api/v1/privacy-policies/current} (ADR-0013 §1). */
public record PrivacyPolicyResponse(String version, String title, String documentUrl, Instant effectiveFrom,
                                    String contentSha256) {

    public static PrivacyPolicyResponse from(PublishedPolicy policy) {
        return new PrivacyPolicyResponse(policy.version(), policy.title(), policy.documentUrl(),
                policy.effectiveFrom(), policy.contentSha256());
    }
}
