package com.integrate.modules.auth;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.InvalidParam;
import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.model.RegisteredUser;
import com.integrate.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Autorregistro de turista (ADR-0005 §6) contra PostgreSQL real. */
class RegistrationServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registersTouristWithRoleArgon2HashAndSelfServiceConsent() {
        String email = uniqueEmail("Turista.Mayus");

        RegisteredUser user = registrationService.registerTourist(tourist(email), CLIENT);

        assertThat(user.email()).isEqualTo(email.toLowerCase());
        assertThat(user.roles()).containsExactly("ROLE_TOURIST");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT email, password_hash, status, credential_status, created_by FROM auth.users WHERE id = ?",
                user.userId());
        assertThat(row.get("email")).isEqualTo(email.toLowerCase());
        assertThat((String) row.get("password_hash")).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("credential_status")).isEqualTo("ACTIVE");
        assertThat(row.get("created_by")).as("autorregistro: sin actor").isNull();
        assertThat(jdbc.queryForList("""
                SELECT r.code FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND ur.revoked_at IS NULL""", String.class, user.userId()))
                .containsExactly("TOURIST");
        List<Map<String, Object>> consents = jdbc.queryForList("""
                SELECT c.purpose, c.action, c.channel, p.version, host(c.ip_address) AS ip, c.user_agent
                  FROM auth.habeas_data_consents c JOIN auth.privacy_policies p ON p.id = c.policy_id
                 WHERE c.user_id = ?""", user.userId());
        assertThat(consents).singleElement().satisfies(c -> {
            assertThat(c.get("purpose")).isEqualTo("PERSONAL_DATA");
            assertThat(c.get("action")).isEqualTo("GRANTED");
            assertThat(c.get("channel")).isEqualTo("SELF_SERVICE");
            assertThat(c.get("version")).isEqualTo(DEV_POLICY);
            assertThat(c.get("ip")).isEqualTo("127.0.0.1");
            assertThat(c.get("user_agent")).isEqualTo("JUnit");
        });
    }

    @Test
    void registersHealthEmergencyConsentWhenGiven() {
        RegisteredUser user = registrationService.registerTourist(tourist(uniqueEmail("salud"), uniqueDocNumber(),
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA,
                        ConsentPurpose.HEALTH_EMERGENCY))), CLIENT);

        assertThat(jdbc.queryForList("SELECT purpose FROM auth.habeas_data_consents WHERE user_id = ? ORDER BY id",
                String.class, user.userId())).containsExactly("PERSONAL_DATA", "HEALTH_EMERGENCY");
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        String email = uniqueEmail("duplicado");
        registrationService.registerTourist(tourist(email), CLIENT);

        assertConflict(() -> registrationService.registerTourist(tourist(email.toUpperCase()), CLIENT));
    }

    @Test
    void rejectsDuplicateDocument() {
        String doc = uniqueDocNumber();
        ConsentAcceptance consent = new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA));
        registrationService.registerTourist(tourist(uniqueEmail("doc1"), doc, consent), CLIENT);

        assertConflict(() -> registrationService.registerTourist(tourist(uniqueEmail("doc2"), doc, consent), CLIENT));
    }

    @Test
    void requiresPersonalDataConsent() {
        RegisterRequest request = tourist(uniqueEmail("sinconsent"), uniqueDocNumber(),
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.HEALTH_EMERGENCY)));

        assertInvalid(() -> registrationService.registerTourist(request, CLIENT), "consent.purposes");
    }

    @Test
    void rejectsPolicyVersionThatIsNotCurrent() {
        RegisterRequest request = tourist(uniqueEmail("version"), uniqueDocNumber(),
                new ConsentAcceptance("v9.9", Set.of(ConsentPurpose.PERSONAL_DATA)));

        assertInvalid(() -> registrationService.registerTourist(request, CLIENT), "consent.policyVersion");
    }

    @Test
    void touristCannotGrantSensitiveVictimPurpose() {
        RegisterRequest request = tourist(uniqueEmail("victima"), uniqueDocNumber(),
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.SENSITIVE_VICTIM)));

        assertInvalid(() -> registrationService.registerTourist(request, CLIENT), "consent.purposes");
    }

    @Test
    void enforcesPasswordLength() {
        RegisterRequest request = new RegisterRequest(uniqueEmail("corta"), "corta123", "Carlos Restrepo", DocType.CC,
                uniqueDocNumber(), "+573001234567",
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA)));

        assertInvalid(() -> registrationService.registerTourist(request, CLIENT), "password");
    }

    @Test
    void rejectedRegistrationLeavesNoRows() {
        String email = uniqueEmail("rollback");
        RegisterRequest request = tourist(email, uniqueDocNumber(),
                new ConsentAcceptance("v9.9", Set.of(ConsentPurpose.PERSONAL_DATA)));

        assertThatThrownBy(() -> registrationService.registerTourist(request, CLIENT))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE email = ?", Long.class, email))
                .isZero();
    }

    private static void assertConflict(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_USER_ALREADY_EXISTS));
    }

    private static void assertInvalid(ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.getExtensions().get("invalidParams")).asInstanceOf(
                    org.assertj.core.api.InstanceOfAssertFactories.list(InvalidParam.class))
                    .extracting(InvalidParam::name).containsExactly(field);
        });
    }
}
