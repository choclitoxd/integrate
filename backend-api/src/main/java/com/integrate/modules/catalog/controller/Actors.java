package com.integrate.modules.catalog.controller;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identidad del invocador a partir del JWT validado por el resource server (ADR-0005 §5). */
final class Actors {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private Actors() {
    }

    static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }
}
