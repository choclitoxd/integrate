package com.integrate.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Endpoints de sesión públicos (ADR-0005 §9). En ellos se <b>ignora</b> la cabecera
 * {@code Authorization}: la PWA envía su access token en todas las peticiones y, cuando expira,
 * llama a {@code /refresh} todavía con él; validarlo ahí devolvería 401 y dejaría al cliente sin
 * forma de renovar la sesión.
 */
final class PublicAuthEndpoints implements BearerTokenResolver {

    static final String[] PATHS = {
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/logout"};
    private static final Set<String> PATH_SET = Set.of(PATHS);

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (HttpMethod.POST.matches(request.getMethod()) && PATH_SET.contains(request.getRequestURI())) {
            return null;
        }
        return delegate.resolve(request);
    }
}
