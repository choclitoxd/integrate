package com.integrate.modules.auth.dto.response;

import com.integrate.modules.auth.service.model.RegisteredUser;
import java.util.List;
import java.util.UUID;

/** Respuesta 201 de {@code POST /api/v1/auth/register}. No inicia sesión: el cliente llama a {@code /login}. */
public record RegisterResponse(UUID userId, String email, List<String> roles) {

    public static RegisterResponse from(RegisteredUser user) {
        return new RegisterResponse(user.userId(), user.email(), user.roles());
    }
}
