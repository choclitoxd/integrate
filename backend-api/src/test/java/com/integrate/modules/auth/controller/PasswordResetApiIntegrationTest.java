package com.integrate.modules.auth.controller;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.service.CredentialActivationService;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.notification.service.impl.OutboxDispatcher;
import com.integrate.support.PostgresIntegrationTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Recuperación de contraseña de punta a punta (ADR-0014 §7): respuesta uniforme, correo real por el outbox
 * contra GreenMail, canje del enlace, revocación de sesiones, vigencia, reemplazo y límite de solicitudes.
 */
@AutoConfigureMockMvc
class PasswordResetApiIntegrationTest extends PostgresIntegrationTest {

    private static final String RESET_PREFIX = "https://app.integrate.test/restablecer-contrasena#token=";
    private static final String NEW_PASSWORD = "otra-clave-larga-del-titular";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CredentialActivationService activationService;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanQueue() throws Exception {
        clock.set(START);
        // Cola limpia: solo se despachan los mensajes de cada prueba.
        jdbc.update("UPDATE notification.notification_outbox SET status = 'DEAD' WHERE status IN ('PENDING', 'FAILED')");
        SMTP.purgeEmailFromAllMailboxes();
    }

    /** El reloj es un bean compartido por todo el contexto de pruebas: no se deja adelantado. */
    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    @Test
    void forgotPasswordAnswersTheSameWhetherOrNotTheAccountCanRecover() throws Exception {
        String active = registered(uniqueEmail("activa"));
        String suspended = registered(uniqueEmail("suspendida"));
        jdbc.update("UPDATE auth.users SET status = 'SUSPENDED' WHERE email = ?", suspended);
        String synthetic = registered("cc" + UUID.randomUUID().toString().substring(0, 10) + "@comunidad.integrate.co");

        List<MvcResult> results = new ArrayList<>();
        for (String email : List.of(active, uniqueEmail("inexistente"), suspended, synthetic)) {
            results.add(forgot(email).andExpect(status().isAccepted())
                    .andExpect(content().string(""))
                    .andReturn());
        }
        // Mismas cabeceras de respuesta (salvo el traceId, distinto por petición).
        List<String> headers = results.stream()
                .map(r -> r.getResponse().getHeaderNames().stream().filter(h -> !h.equalsIgnoreCase("X-Trace-Id"))
                        .sorted().toList().toString())
                .distinct().toList();
        assertThat(headers).hasSize(1);

        assertThat(resetRequests(active)).isEqualTo(1);
        assertThat(resetRequests(suspended)).isZero();
        assertThat(resetRequests(synthetic)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification.notification_outbox
                 WHERE event_type = 'PASSWORD_RESET_REQUESTED' AND status = 'PENDING'""", Long.class)).isEqualTo(1L);
    }

    @Test
    void resetLinkRestoresAccessAndRevokesEverySession() throws Exception {
        String email = registered(uniqueEmail("titular"));
        login(email, PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());

        forgot(email).andExpect(status().isAccepted());
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        MimeMessage mail = only(receivedBy(email));
        assertThat(mail.getSubject()).isEqualTo("Restablece tu contraseña de Intégrate");
        String text = textOf(mail);
        assertThat(text).contains(RESET_PREFIX).contains("15 minutos");
        String token = text.substring(text.indexOf(RESET_PREFIX) + RESET_PREFIX.length()).split("\\s")[0];
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$").isEqualTo(tokenFromOutbox(email));

        reset(token, "corta").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("newPassword"));
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("""
                SELECT t.revoke_reason FROM auth.refresh_tokens t JOIN auth.users u ON u.id = t.user_id
                 WHERE u.email = ?""", String.class, email)).hasSize(2).containsOnly("PASSWORD_CHANGE");
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        reset(token, NEW_PASSWORD + "-otra").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        MimeMessage notice = receivedBy(email)[1];
        assertThat(notice.getSubject()).isEqualTo("Tu contraseña de Intégrate cambió");
        assertThat(textOf(notice)).contains("cerramos todas tus sesiones").doesNotContain(token);
    }

    @Test
    void aNewRequestReplacesTheLinkAndLinksExpireAfterFifteenMinutes() throws Exception {
        String email = registered(uniqueEmail("vencido"));

        forgot(email).andExpect(status().isAccepted());
        String first = tokenFromOutbox(email);
        forgot(email).andExpect(status().isAccepted());
        String second = tokenFromOutbox(email);
        assertThat(second).isNotEqualTo(first);
        reset(first, NEW_PASSWORD).andExpect(status().isUnauthorized());

        clock.advance(Duration.ofMinutes(16));
        reset(second, NEW_PASSWORD).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
        reset("no-es-un-token", NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void atMostThreeRequestsPerAccountAndHour() throws Exception {
        String email = registered(uniqueEmail("insistente"));

        for (int i = 0; i < 5; i++) {
            forgot(email).andExpect(status().isAccepted());
        }
        assertThat(resetRequests(email)).isEqualTo(3);

        clock.advance(Duration.ofMinutes(61));
        forgot(email).andExpect(status().isAccepted());
        assertThat(resetRequests(email)).isEqualTo(4);
    }

    @Test
    void resetActivatesPendingAccountsAndClearsTheLockout() throws Exception {
        String email = registered(uniqueEmail("pendiente"));
        UUID userId = jdbc.queryForObject("SELECT id FROM auth.users WHERE email = ?", UUID.class, email);
        jdbc.update("UPDATE auth.users SET credential_status = 'PENDING_RESET', locked_until = ? WHERE id = ?",
                java.sql.Timestamp.from(START.plus(Duration.ofMinutes(10))), userId);
        activationService.issue(userId, userId);

        forgot(email).andExpect(status().isAccepted());
        reset(tokenFromOutbox(email), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT credential_status FROM auth.users WHERE id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auth.credential_activation_codes
                 WHERE user_id = ? AND consumed_at IS NULL AND revoked_at IS NULL""", Long.class, userId)).isZero();
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void malformedRequestsAre422() throws Exception {
        forgot("no-es-un-correo").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("email"));
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("token"));
    }

    // ---------------------------------------------------------------- fixtures

    private String registered(String email) {
        registrationService.registerTourist(tourist(email), CLIENT);
        return email;
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private long resetRequests(String email) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM auth.password_reset_tokens t JOIN auth.users u ON u.id = t.user_id
                 WHERE u.email = ?""", Long.class, email);
    }

    /** Token del último enlace encolado para {@code email}. */
    private String tokenFromOutbox(String email) {
        String url = jdbc.queryForObject("""
                SELECT payload->>'resetUrl' FROM notification.notification_outbox
                 WHERE recipient_address = ? AND event_type = 'PASSWORD_RESET_REQUESTED'
                 ORDER BY created_at DESC LIMIT 1""", String.class, email);
        assertThat(url).startsWith(RESET_PREFIX);
        return url.substring(RESET_PREFIX.length());
    }

    private static MimeMessage only(MimeMessage[] messages) {
        assertThat(messages).hasSize(1);
        return messages[0];
    }

    private static MimeMessage[] receivedBy(String email) throws Exception {
        List<MimeMessage> matching = new ArrayList<>();
        for (MimeMessage message : SMTP.getReceivedMessages()) {
            if (Arrays.stream(message.getAllRecipients()).anyMatch(a -> a.toString().contains(email))) {
                matching.add(message);
            }
        }
        return matching.toArray(MimeMessage[]::new);
    }

    /** Cuerpo {@code text/plain} ya decodificado (quoted-printable incluido). */
    private static String textOf(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String text = textOf(child);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }
}
