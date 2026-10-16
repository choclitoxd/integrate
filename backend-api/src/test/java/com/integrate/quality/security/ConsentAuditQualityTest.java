package com.integrate.quality.security;

import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.RegistrationService.AdminBootstrapCommand;
import com.integrate.modules.auth.service.RegistrationService.BootstrapOutcome;
import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RNF-06 (Seguridad · no repudio) y la métrica "Cumplimiento de consentimiento legal" del §3.4.1: todo registro
 * deja su consentimiento Ley 1581 en una bitácora de solo inserción con fecha, IP, versión de la política y agente.
 */
@Tag("rnf-06")
@Tag("iso-seguridad")
@DisplayName("[RNF-06] Bitácora inmutable de auditoría de Habeas Data")
class ConsentAuditQualityTest extends QualityIntegrationTest {

    private static final String USER_AGENT = "IntegrateQualitySuite/1.0";

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("[RNF-06] 100 % de los registros por API dejan consentimiento con fecha, IP y versión")
    void everyRegistrationLeavesACompleteConsentRecord() throws Exception {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String email = uniqueEmail("consent-calidad");
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.USER_AGENT, USER_AGENT)
                            .content("""
                                    {"email":"%s","password":"%s","fullName":"Carlos Restrepo","docType":"CC",
                                     "docNumber":"%s","phone":"+573001234567",
                                     "consent":{"policyVersion":"%s","purposes":["PERSONAL_DATA"]}}"""
                                    .formatted(email, PASSWORD, uniqueDocNumber(), DEV_POLICY)))
                    .andExpect(status().isCreated());
            emails.add(email);
        }

        long complete = 0;
        for (String email : emails) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT c.purpose, c.action, host(c.ip_address) AS ip, c.user_agent, c.occurred_at, p.version
                      FROM auth.habeas_data_consents c
                      JOIN auth.users u ON u.id = c.user_id
                      JOIN auth.privacy_policies p ON p.id = c.policy_id
                     WHERE u.email = ?""", email);
            boolean ok = rows.stream().anyMatch(r -> "PERSONAL_DATA".equals(r.get("purpose"))
                    && "GRANTED".equals(r.get("action")) && r.get("ip") != null
                    && USER_AGENT.equals(r.get("user_agent")) && r.get("occurred_at") != null
                    && DEV_POLICY.equals(r.get("version")));
            complete += ok ? 1 : 0;
        }

        QualityMetrics.record("RNF-06", "Registros con consentimiento auditado completo",
                QualityMetrics.percent(complete, emails.size()), "%", "= 100 %", complete == emails.size(),
                emails.size() + " registros por POST /auth/register (fecha, IP, agente, versión de política)");
        assertThat(complete).isEqualTo(emails.size());
    }

    @Test
    @Tag("nonconformity")
    @DisplayName("[RNF-06] NC: la cuenta del administrador inicial (bootstrap) nace sin consentimiento registrado")
    void everyAccountChannelRecordsConsent() {
        String email = uniqueEmail("admin-bootstrap");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long[] result = new long[2];
        // Se simula una base sin administradores dentro de una transacción que se revierte: no altera otras pruebas.
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE auth.user_roles SET revoked_at = now()
                     WHERE revoked_at IS NULL AND role_id = (SELECT id FROM auth.roles WHERE code = 'ADMIN')""");
            BootstrapOutcome outcome = registrationService.bootstrapAdmin(new AdminBootstrapCommand(email, PASSWORD,
                    "Administrador de plataforma", DocType.PA, uniqueDocNumber(), "+10000000000"));
            result[0] = outcome == BootstrapOutcome.CREATED ? 1 : 0;
            result[1] = count("""
                    SELECT count(*) FROM auth.habeas_data_consents c JOIN auth.users u ON u.id = c.user_id
                     WHERE u.email = ? AND c.purpose = 'PERSONAL_DATA' AND c.action = 'GRANTED'""", email);
            status.setRollbackOnly();
        });
        // Canales de alta: autoservicio (POST /auth/register), asistido por gestor y bootstrap del administrador.
        long channels = 3;
        long withConsent = 2 + (result[1] > 0 ? 1 : 0);

        QualityMetrics.record("RNF-06", "Canales de alta de cuentas que registran el consentimiento",
                QualityMetrics.percent(withConsent, channels), "%", "= 100 % (cero tolerancia legal)",
                withConsent == channels, "autoservicio y enrolamiento asistido lo registran al crear la cuenta; el "
                        + "bootstrap del ADMIN (ADR-0005 §9) no: lo pide en el primer ingreso (requiresConsentUpdate)");
        assertThat(result[0]).as("el bootstrap creó la cuenta").isEqualTo(1);
        assertThat(result[1]).as("consentimiento PERSONAL_DATA del administrador inicial").isPositive();
    }

    @Test
    @DisplayName("[RNF-06] La bitácora de consentimientos rechaza UPDATE, DELETE y TRUNCATE")
    void theConsentLogIsAppendOnly() {
        registerTourist();
        List<String> mutations = List.of(
                "UPDATE auth.habeas_data_consents SET action = 'REVOKED'",
                "DELETE FROM auth.habeas_data_consents",
                "TRUNCATE auth.habeas_data_consents",
                "UPDATE audit.audit_trail SET action = 'X'",
                "DELETE FROM audit.audit_trail");
        long blocked = 0;
        for (String sql : mutations) {
            try {
                jdbc.execute(sql);
            } catch (DataAccessException expected) {
                blocked++;
            }
        }
        QualityMetrics.record("RNF-06", "Intentos de alterar la bitacora bloqueados",
                QualityMetrics.percent(blocked, mutations.size()), "%", "= 100 %", blocked == mutations.size(),
                "UPDATE/DELETE/TRUNCATE con el rol de la aplicación (integrate_api)");
        assertThat(blocked).isEqualTo(mutations.size());
        assertThatThrownBy(() -> jdbc.execute(mutations.getFirst())).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForList("SELECT * FROM audit.verify_chain()")).isEmpty();
    }
}
