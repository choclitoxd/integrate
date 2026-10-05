package com.integrate.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asigna un identificador de correlación a cada petición (ADR-0003): lo publica en el MDC
 * ({@code traceId}), en la cabecera {@code X-Trace-Id} de la respuesta y en un atributo de la
 * petición. Corre con precedencia máxima, antes de Spring Security, para que también los
 * 401/403 lo incluyan. Un {@code X-Trace-Id} entrante solo se reutiliza si cumple
 * {@link #VALID_TRACE_ID}; cualquier otro valor se descarta (evita inyección en logs).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Devuelve el traceId de la petición en curso, o {@code null} si el filtro no corrió. */
    public static String currentTraceId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value != null ? value.toString() : MDC.get(MDC_KEY);
    }

    private static String resolve(String inbound) {
        if (inbound != null && VALID_TRACE_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
