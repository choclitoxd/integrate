package com.integrate.modules.auth.service.model;

import java.util.List;
import java.util.UUID;

/** Usuario creado por autorregistro. */
public record RegisteredUser(UUID userId, String email, List<String> roles) {

    public RegisteredUser {
        roles = List.copyOf(roles);
    }
}
