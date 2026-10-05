package com.integrate.modules.auth.dto.response;

import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.service.model.Session;
import java.util.List;
import java.util.UUID;

/** Contrato {@code AuthTokenResponse} (login y refresh). El refresh token viaja solo en la cookie. */
public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user,
                                boolean requiresConsentUpdate, String pendingPolicyVersion,
                                List<ConsentPurpose> pendingPurposes) {

    public static final String BEARER = "Bearer";

    public static AuthTokenResponse from(Session session) {
        var user = session.user();
        return new AuthTokenResponse(session.accessToken(), BEARER, session.expiresInSeconds(),
                new UserResponse(user.id(), user.email(), user.fullName(), user.roles()),
                session.consent().requiresConsentUpdate(), session.consent().pendingPolicyVersion(),
                session.consent().pendingPurposes());
    }

    /** Contrato {@code AuthenticatedUser}. */
    public record UserResponse(UUID id, String email, String fullName, List<String> roles) {
    }
}
