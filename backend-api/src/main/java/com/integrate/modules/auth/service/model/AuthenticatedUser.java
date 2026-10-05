package com.integrate.modules.auth.service.model;

import java.util.List;
import java.util.UUID;

/** Identidad autenticada tal como la expone el contrato ({@code AuthenticatedUser}); {@code roles} con prefijo {@code ROLE_}. */
public record AuthenticatedUser(UUID id, String email, String fullName, List<String> roles) {

    public AuthenticatedUser {
        roles = List.copyOf(roles);
    }
}
