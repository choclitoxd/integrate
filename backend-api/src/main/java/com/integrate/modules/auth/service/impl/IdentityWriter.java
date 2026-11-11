package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.Role;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserRole;
import com.integrate.modules.auth.repository.RoleRepository;
import com.integrate.modules.auth.repository.UserRoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Alta de una identidad con sus roles, compartida por el autorregistro, el primer administrador y
 * el registro asistido (ADR-0005 §6, ADR-0007 §3). Sin transacción propia.
 */
@Component
@RequiredArgsConstructor
class IdentityWriter {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final IdentityProtection identityProtection;

    /**
     * Inserta el usuario y sus roles. La comprobación previa de duplicados no cubre la carrera
     * entre dos registros simultáneos: ahí decide el índice único y se responde igual (409).
     */
    User persist(User user, List<RoleCode> roles) {
        identityProtection.insertIdentity(user);
        for (RoleCode code : roles) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Role " + code + " is missing from auth.roles"));
            userRoleRepository.save(UserRole.grant(user.getId(), role));
        }
        return user;
    }

    /** {@code ck_users_full_name} exige al menos 3 caracteres sin contar espacios externos. */
    static String requireFullName(String fullName, String field) {
        String trimmed = fullName == null ? "" : fullName.strip();
        if (trimmed.length() < 3) {
            throw AuthErrors.invalidField(field, "must have at least 3 non-blank characters");
        }
        return trimmed;
    }

}
