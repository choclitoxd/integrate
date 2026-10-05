package com.integrate.modules.auth.controller;

import com.integrate.modules.auth.service.model.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.http.HttpHeaders;

/**
 * IP y user agent de la terminal (RF-01, RF-02). La IP es {@code getRemoteAddr()}: detrás del
 * balanceador de AWS se activará {@code server.forward-headers-strategy} solo para proxies de
 * confianza (ADR-0005 §4); nunca se lee {@code X-Forwarded-For} a mano.
 */
final class ClientInfos {

    private ClientInfos() {
    }

    static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(address(request.getRemoteAddr()), request.getHeader(HttpHeaders.USER_AGENT));
    }

    private static InetAddress address(String remoteAddr) {
        try {
            // getRemoteAddr() devuelve un literal IP: getByName no hace resolución DNS.
            return InetAddress.getByName(remoteAddr);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Servlet container returned a non-literal remote address", e);
        }
    }
}
