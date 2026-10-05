package com.integrate.modules.auth.service.model;

import java.net.InetAddress;
import java.util.Objects;

/**
 * Terminal desde la que actúa el titular: se persiste en intentos de login, sesiones y
 * consentimientos (RF-01, RF-02). El {@code userAgent} se recorta a la longitud de las columnas.
 */
public record ClientInfo(InetAddress ipAddress, String userAgent) {

    public static final int MAX_USER_AGENT = 1000;
    private static final String UNKNOWN_AGENT = "unknown";

    public ClientInfo {
        Objects.requireNonNull(ipAddress, "ipAddress");
        userAgent = userAgent == null || userAgent.isBlank()
                ? UNKNOWN_AGENT
                : userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT));
    }
}
