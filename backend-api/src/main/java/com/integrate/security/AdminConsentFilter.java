package com.integrate.security;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.service.ConsentService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** ADR-0040. Consulta el consentimiento vigente incluso cuando el JWT se emitió antes de un cambio de política. */
final class AdminConsentFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher API = PATHS.matcher("/api/v1/**");
    private static final List<RequestMatcher> CONSENT_FLOW_AND_PUBLIC_READS = List.of(
            PATHS.matcher(HttpMethod.GET, "/api/v1/me/consents"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/me/consents"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/privacy-policies/current"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/experiences"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/experiences/*"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/experiences/*/availability"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/catalog/**"));

    private final ConsentService consents;
    private final HandlerExceptionResolver errors;

    AdminConsentFilter(ConsentService consents, HandlerExceptionResolver errors) {
        this.consents = consents;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var principal = SecurityContextHolder.getContext().getAuthentication();
        if (!(principal instanceof JwtAuthenticationToken jwt) || !API.matches(request)
                || jwt.getAuthorities().stream().noneMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()))
                || CONSENT_FLOW_AND_PUBLIC_READS.stream().anyMatch(matcher -> matcher.matches(request))) {
            chain.doFilter(request, response);
            return;
        }

        var status = consents.status(UUID.fromString(jwt.getToken().getSubject()));
        if (status.currentPolicyVersion() == null) {
            errors.resolveException(request, response, null,
                    new BusinessRuleException(ErrorCode.ADMIN_PRIVACY_POLICY_UNAVAILABLE));
            return;
        }
        if (!status.hasCurrentPersonalDataConsent()) {
            errors.resolveException(request, response, null, new BusinessRuleException(ErrorCode.ADMIN_CONSENT_REQUIRED)
                    .with("policyVersion", status.currentPolicyVersion())
                    .with("requiredPurposes", List.of("PERSONAL_DATA")));
            return;
        }
        chain.doFilter(request, response);
    }
}
