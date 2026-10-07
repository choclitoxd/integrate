package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.RegisteredUser;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final List<RoleCode> TOURIST_ROLES = List.of(RoleCode.TOURIST);
    private static final List<RoleCode> ADMIN_ROLES = List.of(RoleCode.ADMIN, RoleCode.TOURIST);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final IdentityWriter identityWriter;
    private final ConsentLedger consentLedger;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegisteredUser registerTourist(RegisterRequest request, ClientInfo client) {
        PasswordPolicy.check(request.password(), "password");
        String fullName = IdentityWriter.requireFullName(request.fullName(), "fullName");
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
        User user = identityWriter.persist(User.register(email, passwordEncoder.encode(request.password()), fullName,
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
        identityWriter.persist(User.register(email, passwordEncoder.encode(command.password()),
                IdentityWriter.requireFullName(command.fullName(), "BOOTSTRAP_ADMIN_FULL_NAME"), command.docType(),
                command.docNumber(), command.phone()), ADMIN_ROLES);
        return BootstrapOutcome.CREATED;
    }

    private static List<String> claims(List<RoleCode> roles) {
        return roles.stream().map(RoleCode::claim).toList();
    }
}
