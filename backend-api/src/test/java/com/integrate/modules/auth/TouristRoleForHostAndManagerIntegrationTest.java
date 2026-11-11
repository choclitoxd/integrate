package com.integrate.modules.auth;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ADR-0064: todo anfitrión y gestor de enlace es también turista. La migración V20 otorga {@code TOURIST} a las
 * cuentas activas que solo tenían {@code HOST} o {@code LINK_MANAGER}, sin tocar las suspendidas, y se puede repetir.
 */
class TouristRoleForHostAndManagerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationGrantsTouristToActiveHostsAndManagersAndSkipsSuspendedAccounts() throws Exception {
        UUID host = accountWithOnly(RoleCode.HOST);
        UUID manager = accountWithOnly(RoleCode.LINK_MANAGER);
        UUID suspendedHost = accountWithOnly(RoleCode.HOST);
        jdbc.update("UPDATE auth.users SET status = 'SUSPENDED' WHERE id = ?", suspendedHost);
        UUID plainTourist = registrationService.registerTourist(tourist(uniqueEmail("solo-turista")), CLIENT).userId();

        runMigration();

        assertThat(activeRoles(host)).containsExactlyInAnyOrder("HOST", "TOURIST");
        assertThat(activeRoles(manager)).containsExactlyInAnyOrder("LINK_MANAGER", "TOURIST");
        assertThat(activeRoles(suspendedHost)).containsExactly("HOST");
        assertThat(activeRoles(plainTourist)).containsExactly("TOURIST");

        // Repetirla no duplica filas.
        runMigration();
        assertThat(activeRoles(host)).containsExactlyInAnyOrder("HOST", "TOURIST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.user_roles WHERE user_id = ?", Long.class, host))
                .isEqualTo(3L); // TOURIST original (revocado), HOST y el TOURIST nuevo
    }

    @Test
    void revokingTheZoneRoleLeavesTouristInPlace() {
        UUID manager = accountWithOnly(RoleCode.LINK_MANAGER);
        runMigration();

        roleAssignmentService.revoke(manager, RoleCode.LINK_MANAGER, manager);

        assertThat(activeRoles(manager)).containsExactly("TOURIST");
    }

    /** Cuenta con un solo rol vigente: se registra (nace TOURIST), se le agrega el rol y se revoca TOURIST. */
    private UUID accountWithOnly(RoleCode role) {
        UUID userId = registrationService.registerTourist(tourist(uniqueEmail("rol-" + role.name().toLowerCase())),
                CLIENT).userId();
        roleAssignmentService.grant(userId, role);
        jdbc.update("""
                UPDATE auth.user_roles SET revoked_at = now()
                 WHERE user_id = ? AND revoked_at IS NULL
                   AND role_id = (SELECT id FROM auth.roles WHERE code = 'TOURIST')""", userId);
        return userId;
    }

    private List<String> activeRoles(UUID userId) {
        return jdbc.queryForList("""
                SELECT r.code FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND ur.revoked_at IS NULL""", String.class, userId);
    }

    private void runMigration() {
        try {
            String sql = new String(new ClassPathResource("db/migration/V20__tourist_role_for_host_and_manager.sql")
                    .getContentAsByteArray(), StandardCharsets.UTF_8);
            jdbc.execute(sql);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
