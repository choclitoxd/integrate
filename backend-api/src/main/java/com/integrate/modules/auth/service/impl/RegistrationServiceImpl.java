package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.Role;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserRole;
import com.integrate.modules.auth.repository.RoleRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.RegisteredUser;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    /** Índices únicos de {@code auth.users} cuya violación significa "la identidad ya existe". */
    private static final Set<String> IDENTITY_CONSTRAINTS = Set.of("uq_users_email_lower", "uq_users_document");
    private static final List<RoleCode> TOURIST_ROLES = List.of(RoleCode.TOURIST);
    private static final List<RoleCode> ADMIN_ROLES = List.of(RoleCode.ADMIN, RoleCode.TOURIST);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ConsentLedger consentLedger;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegisteredUser registerTourist(RegisterRequest request, ClientInfo client) {
        PasswordPolicy.check(request.password(), "password");
        String fullName = requireFullName(request.fullName(), "fullName");
        ConsentAcceptance consent = request.consent();
        PrivacyPolicy policy = consentLedger.requireCurrentPolicy(consent.policyVersion(), "consent.policyVersion");
        if (!consent.purposes().contains(ConsentPurpose.PERSONAL_DATA)) {
            throw AuthErrors.invalidField("consent.purposes", "must include PERSONAL_DATA");
        }
        consentLedger.requireApplicable(consent.purposes(), TOURIST_ROLES, "consent.purposes");

        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByDocTypeAndDocNumber(request.docType(), request.docNumber())) {
            throw AuthErrors.userAlreadyExists();
        }
        User user = persistIdentity(User.register(email, passwordEncoder.encode(request.password()), fullName,
                request.docType(), request.docNumber(), request.phone()), TOURIST_ROLES);
        consentLedger.insertGrants(user.getId(), policy, consent.purposes(), Set.of(), client);
        return new RegisteredUser(user.getId(), user.getEmail(), claims(TOURIST_ROLES));
    }

    @Override
    @Transactional
    public BootstrapOutcome bootstrapAdmin(AdminBootstrapCommand command) {
        if (userRoleRepository.existsActiveAssignment(RoleCode.ADMIN)) {
            return BootstrapOutcome.ADMIN_ALREADY_EXISTS;
        }
        PasswordPolicy.check(command.password(), "BOOTSTRAP_ADMIN_PASSWORD");
        String email = Emails.normalize(command.email());
        if (userRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByDocTypeAndDocNumber(command.docType(), command.docNumber())) {
            return BootstrapOutcome.IDENTITY_TAKEN;
        }
        persistIdentity(User.register(email, passwordEncoder.encode(command.password()),
                requireFullName(command.fullName(), "BOOTSTRAP_ADMIN_FULL_NAME"), command.docType(),
                command.docNumber(), command.phone()), ADMIN_ROLES);
        return BootstrapOutcome.CREATED;
    }

    /**
     * Inserta el usuario y sus roles. La comprobación previa de duplicados no cubre la carrera
     * entre dos registros simultáneos: ahí decide el índice único y se responde igual (409).
     */
    private User persistIdentity(User user, List<RoleCode> roles) {
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isIdentityConflict(e)) {
                throw AuthErrors.userAlreadyExists();
            }
            throw e;
        }
        for (RoleCode code : roles) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Role " + code + " is missing from auth.roles"));
            userRoleRepository.save(UserRole.grant(saved.getId(), role));
        }
        return saved;
    }

    /** {@code ck_users_full_name} exige al menos 3 caracteres sin contar espacios externos. */
    private static String requireFullName(String fullName, String field) {
        String trimmed = fullName == null ? "" : fullName.strip();
        if (trimmed.length() < 3) {
            throw AuthErrors.invalidField(field, "must have at least 3 non-blank characters");
        }
        return trimmed;
    }

    private static boolean isIdentityConflict(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null
                    && IDENTITY_CONSTRAINTS.contains(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> claims(List<RoleCode> roles) {
        return roles.stream().map(RoleCode::claim).toList();
    }
}
