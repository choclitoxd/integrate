package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.InvalidParam;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.PrivacyPolicy;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.model.AssistedHostAccount;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.ProvisionedHost;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssistedAccountServiceImpl implements AssistedAccountService {

    private static final Set<ConsentPurpose> REQUIRED_PURPOSES =
            EnumSet.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.SENSITIVE_VICTIM);
    private static final List<RoleCode> HOST_ROLES = List.of(RoleCode.HOST);
    private static final int UNUSABLE_SECRET_BYTES = 32;
    /** Sin I, O, 0 ni 1 (como los códigos cortos), en minúsculas: 32 símbolos, así que 10 son 50 bits. */
    private static final String SYNTHETIC_EMAIL_ALPHABET = "abcdefghjklmnpqrstuvwxyz23456789";
    private static final String SYNTHETIC_EMAIL_PREFIX = "h-";
    private static final int SYNTHETIC_EMAIL_ID_LENGTH = 10;
    private static final int SYNTHETIC_EMAIL_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final IdentityWriter identityWriter;
    private final ConsentLedger consentLedger;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public ProvisionedHost provisionHost(AssistedHostAccount account, ClientInfo client) {
        String fullName = IdentityWriter.requireFullName(account.fullName(), "fullName");
        PrivacyPolicy policy = consentLedger.requireCurrentPolicy(account.policyVersion(), "consent.policyVersion");
        if (account.purposes() == null || !REQUIRED_PURPOSES.equals(account.purposes())) {
            throw AuthErrors.invalidField("consent.purposes", "must be exactly " + REQUIRED_PURPOSES);
        }
        if (consentLedger.isConsentFormInUse(account.physicalConsentFormS3Key())) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_DOCUMENT_INVALID,
                    "The signed consent form already backs another consent.")
                    .with("invalidParams", List.of(new InvalidParam("consent.physicalConsentFormS3Key",
                            "already in use")));
        }

        boolean synthetic = account.email() == null || account.email().isBlank();
        String email = synthetic ? newSyntheticEmail() : Emails.normalize(account.email());
        if (userRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByDocTypeAndDocNumber(account.docType(), account.docNumber())) {
            throw AuthErrors.userAlreadyExists();
        }
        User user = identityWriter.persist(User.registerPendingReset(email, passwordEncoder.encode(unusableSecret()),
                fullName, account.docType(), account.docNumber(), account.phone()), HOST_ROLES);
        Instant capturedAt = consentLedger.insertAssistedGrants(user.getId(), policy, REQUIRED_PURPOSES,
                account.capturedBy(), account.physicalConsentFormS3Key(), client);
        return new ProvisionedHost(user.getId(), user.getEmail(), synthetic,
                HOST_ROLES.stream().map(RoleCode::claim).toList(), user.getCredentialStatus(), policy.getVersion(),
                EnumSet.copyOf(REQUIRED_PURPOSES), account.capturedBy(), capturedAt);
    }

    /**
     * {@code h-<10 caracteres>@comunidad.integrate.co} (ADR-0038 §1): opaco, sin relación con el documento ni con el
     * nombre. Reemplaza a {@code {docType}{docNumber}@…} de ADR-0007 §3.4, que dejaba el número de documento a la
     * vista en la lista del gestor. El sufijo no cambia: es lo que identifica una cuenta sin correo real.
     */
    private String newSyntheticEmail() {
        for (int attempt = 0; attempt < SYNTHETIC_EMAIL_ATTEMPTS; attempt++) {
            StringBuilder local = new StringBuilder(SYNTHETIC_EMAIL_PREFIX);
            for (int i = 0; i < SYNTHETIC_EMAIL_ID_LENGTH; i++) {
                local.append(SYNTHETIC_EMAIL_ALPHABET.charAt(RANDOM.nextInt(SYNTHETIC_EMAIL_ALPHABET.length())));
            }
            String email = local + "@" + SYNTHETIC_EMAIL_DOMAIN;
            // 50 bits: una colisión es despreciable, pero el índice único es la última palabra.
            if (!userRepository.existsByEmailIgnoreCase(email)) {
                return email;
            }
        }
        throw new IllegalStateException("Could not generate a unique synthetic e-mail address");
    }

    /** Secreto que nadie conoce: la cuenta solo se habilita con un código de activación (§6). */
    private static String unusableSecret() {
        byte[] bytes = new byte[UNUSABLE_SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
