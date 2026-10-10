package com.integrate.common.web;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origen público de la PWA ({@code FRONTEND_BASE_URL}), usado para construir los enlaces de los correos
 * (ADR-0014 §3). Obligatorio: sin él el arranque falla en lugar de enviar enlaces rotos.
 */
@ConfigurationProperties("integrate.frontend")
public record FrontendProperties(String baseUrl) {

    public FrontendProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("integrate.frontend.base-url is required (FRONTEND_BASE_URL)");
        }
        URI uri = URI.create(baseUrl.strip());
        if (!uri.isAbsolute() || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("integrate.frontend.base-url must be an absolute http(s) origin");
        }
        baseUrl = baseUrl.strip().replaceAll("/+$", "");
    }

    /** {@code baseUrl + path}; {@code path} empieza por {@code /}. */
    public String link(String path) {
        return baseUrl + path;
    }
}
