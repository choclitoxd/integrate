package com.integrate.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Registra solo respuestas 5xx con ruta normalizada y sin parámetros ni contenido (ADR-0053). */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HttpFailureLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpFailureLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            if (status >= 500) {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = pattern instanceof String value ? value : "unmatched";
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                log.atError()
                        .addKeyValue("event.action", "http.request.failure")
                        .addKeyValue("http.request.method", request.getMethod())
                        .addKeyValue("http.route", route)
                        .addKeyValue("http.response.status_code", status)
                        .addKeyValue("event.duration_ms", durationMs)
                        .log("HTTP request failed");
            }
        }
    }
}
