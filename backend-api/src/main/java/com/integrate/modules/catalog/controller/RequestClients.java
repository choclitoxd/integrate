package com.integrate.modules.catalog.controller;

import com.integrate.modules.auth.service.model.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.http.HttpHeaders;

/**
 * Terminal del gestor que captura un consentimiento asistido (ADR-0007 §3). Mismo criterio que el
 * módulo {@code auth}: IP de {@code getRemoteAddr()}, nunca {@code X-Forwarded-For} leído a mano.
 */
final class RequestClients {

    private RequestClients() {
    }

    static ClientInfo from(HttpServletRequest request) {
        try {
            // getRemoteAddr() devuelve un literal IP: getByName no hace resolución DNS.
            return new ClientInfo(InetAddress.getByName(request.getRemoteAddr()),
                    request.getHeader(HttpHeaders.USER_AGENT));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Servlet container returned a non-literal remote address", e);
        }
    }
}
