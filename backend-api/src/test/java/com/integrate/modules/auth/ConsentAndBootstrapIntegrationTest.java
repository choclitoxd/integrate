package com.integrate.modules.auth;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.request.LoginRequest;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.AuthService;
import com.integrate.modules.auth.service.ConsentService;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RegistrationService.AdminBootstrapCommand;
import com.integrate.modules.auth.service.RegistrationService.BootstrapOutcome;
import com.integrate.modules.auth.service.model.ConsentStatus;
import com.integrate.modules.auth.service.model.ConsentStatus.PurposeConsent;
import com.integrate.modules.auth.service.model.ConsentStatus.State;
import com.integrate.modules.auth.service.model.Session;
import com.integrate.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Consentimiento por autoservicio (RF-02, ADR-0005 §6-§7) y bootstrap del primer ADMIN (§9). */
class ConsentAndBootstrapIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ConsentService consentService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void statusListsApplicablePurposesForTourist() {
        UUID userId = registrationService.registerTourist(tourist(uniqueEmail("estado")), CLIENT).userId();

        ConsentStatus status = consentService.status(userId);

        assertThat(status.currentPolicyVersion()).isEqualTo(DEV_POLICY);
        assertThat(status.purposes()).extracting(PurposeConsent::purpose, PurposeConsent::state)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ConsentPurpose.PERSONAL_DATA, State.VALID),
                        org.assertj.core.groups.Tuple.tuple(ConsentPurpose.HEALTH_EMERGENCY, State.MISSING));
        assertThat(status.requiresConsentUpdate()).isFalse();
    }

    @Test
    void grantIsIdempotentAndRejectsInapplicablePurposes() {
        UUID userId = registrationService.registerTourist(tourist(uniqueEmail("otorga")), CLIENT).userId();
        ConsentAcceptance health = new ConsentAcceptance(DEV_POLICY,
                Set.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.HEALTH_EMERGENCY));

        ConsentStatus status = consentService.grant(userId, health, CLIENT);
        consentService.grant(userId, health, CLIENT);

        assertThat(status.purposes()).allSatisfy(p -> assertThat(p.state()).isEqualTo(State.VALID));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.habeas_data_consents WHERE user_id = ?",
                Long.class, userId)).as("PERSONAL_DATA del registro + HEALTH_EMERGENCY una sola vez").isEqualTo(2);
        assertThatThrownBy(() -> consentService.grant(userId,
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.SENSITIVE_VICTIM)), CLIENT))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void newPolicyVersionRequiresReconsentAtLogin() {
        String email = uniqueEmail("reconsent");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        publishPolicy("v0.2");
        try {
            Session session = authService.login(new LoginRequest(email, PASSWORD), CLIENT);

            assertThat(session.consent().requiresConsentUpdate()).isTrue();
            assertThat(session.consent().pendingPolicyVersion()).isEqualTo("v0.2");
            assertThat(session.consent().pendingPurposes()).containsExactly(ConsentPurpose.PERSONAL_DATA);
            assertThat(consentService.status(userId).purposes().getFirst().state()).isEqualTo(State.OUTDATED);

            ConsentStatus after = consentService.grant(userId,
                    new ConsentAcceptance("v0.2", Set.of(ConsentPurpose.PERSONAL_DATA)), CLIENT);

            assertThat(after.requiresConsentUpdate()).isFalse();
            assertThat(after.purposes().getFirst().policyVersion()).isEqualTo("v0.2");
        } finally {
            restoreDevPolicy();
        }
    }

    @Test
    void bootstrapCreatesTheFirstAdminOnlyOnce() {
        String email = uniqueEmail("admin");
        AdminBootstrapCommand command = new AdminBootstrapCommand(email, PASSWORD, "Administrador de plataforma",
                DocType.PA, uniqueDocNumber(), "+10000000000");
        boolean adminExisted = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                                WHERE r.code = 'ADMIN' AND ur.revoked_at IS NULL)""", Boolean.class));

        long adminsBefore = jdbc.queryForObject("""
                SELECT count(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                 WHERE r.code = 'ADMIN' AND ur.revoked_at IS NULL""", Long.class);

        BootstrapOutcome first = registrationService.bootstrapAdmin(command);
        BootstrapOutcome second = registrationService.bootstrapAdmin(new AdminBootstrapCommand(uniqueEmail("admin2"),
                PASSWORD, "Otro administrador", DocType.PA, uniqueDocNumber(), "+10000000000"));

        if (!adminExisted) {
            assertThat(first).isEqualTo(BootstrapOutcome.CREATED);
            assertThat(authService.login(new LoginRequest(email, PASSWORD), CLIENT).user().roles())
                    .containsExactly("ROLE_TOURIST", "ROLE_ADMIN");
        }
        assertThat(second).isEqualTo(BootstrapOutcome.ADMIN_ALREADY_EXISTS);
        // Otras clases de prueba (p. ej. la de auditoría) crean administradores en la misma base: si ya había, el
        // arranque no agrega ninguno; si no había, deja exactamente uno.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                 WHERE r.code = 'ADMIN' AND ur.revoked_at IS NULL""", Long.class))
                .isEqualTo(adminExisted ? adminsBefore : 1L);
    }

    private void publishPolicy(String version) {
        jdbc.update("UPDATE auth.privacy_policies SET is_current = false WHERE is_current");
        jdbc.update("""
                INSERT INTO auth.privacy_policies (version, title, document_url, content_sha256, effective_from, is_current)
                VALUES (?, 'Política de prueba', 'https://integrate.localhost/test', repeat('a', 64), now(), true)
                ON CONFLICT (version) DO UPDATE SET is_current = true""", version);
    }

    private void restoreDevPolicy() {
        jdbc.update("UPDATE auth.privacy_policies SET is_current = false WHERE is_current");
        jdbc.update("UPDATE auth.privacy_policies SET is_current = true WHERE version = ?", DEV_POLICY);
    }
}
