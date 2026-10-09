package com.integrate.modules.notification;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.notification.service.impl.OutboxDispatcher;
import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.modules.voucher.service.impl.VoucherIssuer;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Correo transaccional de punta a punta (ADR-0011 §8): encolado con el PDF, despacho SMTP real contra GreenMail,
 * reintentos con backoff, {@code DEAD}, rescate de {@code PROCESSING}, concurrencia y escape de HTML.
 */
@AutoConfigureMockMvc
class NotificationIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private VoucherIssuer issuer;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String hostToken;
    private String experienceId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor(userWithRole("admin", RoleCode.ADMIN));
        String associationId = createAssociation();
        String hostEmail = uniqueEmail("anfitrion");
        UUID hostUserId = registrationService.registerTourist(tourist(hostEmail), CLIENT).userId();
        postJson("/api/v1/admin/host-profiles", adminToken,
                "{\"userId\":\"" + hostUserId + "\",\"associationId\":\"" + associationId + "\"}")
                .andExpect(status().isCreated());
        hostToken = tokenFor(hostEmail);
        experienceId = approvedExperience();
        // Cola limpia: solo se despachan los mensajes de cada prueba.
        jdbc.update("UPDATE notification.notification_outbox SET status = 'DEAD' WHERE status IN ('PENDING', 'FAILED')");
        SMTP.purgeEmailFromAllMailboxes();
    }

    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    // ---------------------------------------------------------- encolado y envío

    @Test
    void confirmedBookingSendsTheVoucherPdfByEmail() throws Exception {
        String email = registerTourist("Ana Ruiz");
        String bookingId = confirmedBookingWithPdf(email, 2);
        Voucher voucher = voucherRepository.findByBookingId(UUID.fromString(bookingId)).orElseThrow();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT id, event_type, channel, status, recipient_address, template_code, payload::text AS payload,
                       attachments::text AS attachments, attempts
                  FROM notification.notification_outbox WHERE aggregate_type = 'voucher' AND aggregate_id = ?""",
                voucher.getId().toString());
        assertThat(row).containsEntry("event_type", "VOUCHER_ISSUED").containsEntry("channel", "EMAIL")
                .containsEntry("status", "PENDING").containsEntry("recipient_address", email)
                .containsEntry("template_code", "voucher-issued");
        assertThat((String) row.get("payload")).contains("Ana Ruiz", voucher.getShortCode(), "$ 160.000")
                .doesNotContain("Sura").doesNotContain("Carlos Gomez").doesNotContain("+573001234567");
        assertThat((String) row.get("attachments")).contains(voucher.getPdfS3Key());

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

        MimeMessage[] received = receivedBy(email);
        assertThat(received).hasSize(1);
        MimeMessage mail = received[0];
        assertThat(mail.getSubject()).startsWith("Tu reserva está confirmada: Sendero del correo");
        assertThat(mail.getMessageID()).isEqualTo("<" + row.get("id") + "@integrate.co>");
        assertThat(mail.getFrom()[0].toString()).contains("no-reply@integrate.test");
        Parts parts = parts(mail);
        assertThat(parts.text()).contains("Ana Ruiz", voucher.getShortCode(), "$ 160.000", bookingId);
        assertThat(parts.html()).contains("Ana Ruiz", voucher.getShortCode());
        assertThat(parts.attachmentName()).isEqualTo("voucher-" + voucher.getShortCode() + ".pdf");
        assertThat(parts.attachment()).isEqualTo(voucherStorage.get(voucher.getPdfS3Key()).orElseThrow());

        Map<String, Object> sent = jdbc.queryForMap("""
                SELECT status, sent_at, provider_message_id, attempts FROM notification.notification_outbox
                 WHERE id = ?::uuid""", row.get("id").toString());
        assertThat(sent.get("status")).isEqualTo("SENT");
        assertThat(sent.get("sent_at")).isNotNull();
        assertThat(sent.get("provider_message_id")).isEqualTo(mail.getMessageID());
        assertThat(((Number) sent.get("attempts")).intValue()).isEqualTo(1);
        assertThat(dispatcher.dispatchOnce()).as("nada más que enviar").isZero();
    }

    @Test
    void htmlInPersonalDataIsEscaped() throws Exception {
        String email = registerTourist("Ana <b>Ruiz</b>");
        confirmedBookingWithPdf(email, 1);

        dispatcher.dispatchOnce();

        Parts parts = parts(receivedBy(email)[0]);
        assertThat(parts.html()).contains("Ana &lt;b&gt;Ruiz&lt;/b&gt;").doesNotContain("<b>Ruiz</b>");
    }

    @Test
    void concurrentPdfRenderersEnqueueASingleEmail() throws Exception {
        String email = registerTourist("Luis Gil");
        String bookingId = confirmedBooking(email, 1);
        UUID voucherId = issuer.issue(UUID.fromString(bookingId)).orElseThrow();

        runConcurrently(3, () -> {
            issuer.renderPdf(voucherId);
            return null;
        });

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification.notification_outbox WHERE aggregate_id = ?""", Long.class,
                voucherId.toString())).isEqualTo(1);
    }

    @Test
    void syntheticCommunityAddressesAreNeverEnqueued() throws Exception {
        String email = "cc" + System.nanoTime() % 1_000_000_000L + "@comunidad.integrate.co";
        registerTourist(email, "Pedro Ruiz");
        String bookingId = confirmedBookingWithPdf(email, 1);
        UUID voucherId = voucherRepository.findByBookingId(UUID.fromString(bookingId)).orElseThrow().getId();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification.notification_outbox WHERE aggregate_id = ?""", Long.class,
                voucherId.toString())).isZero();
    }

    // ------------------------------------------------------------- reintentos

    @Test
    void failedDeliveriesAreRetriedWithBackoffAndDieAfterMaxAttempts() throws Exception {
        String email = registerTourist("Marta Ruiz");
        String bookingId = confirmedBookingWithPdf(email, 1);
        Voucher voucher = voucherRepository.findByBookingId(UUID.fromString(bookingId)).orElseThrow();
        String outboxId = jdbc.queryForObject(
                "SELECT id::text FROM notification.notification_outbox WHERE aggregate_id = ?", String.class,
                voucher.getId().toString());

        // El adjunto desaparece de S3: el envío falla y se reprograma 1 min después.
        byte[] pdf = voucherStorage.remove(voucher.getPdfS3Key()).orElseThrow();
        dispatcher.dispatchOnce();
        Map<String, Object> failed = outbox(outboxId);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(((Number) failed.get("attempts")).intValue()).isEqualTo(1);
        assertThat(((java.sql.Timestamp) failed.get("next_attempt_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
        assertThat((String) failed.get("last_error")).contains("Attachment not found");
        assertThat(dispatcher.dispatchOnce()).as("todavía no vence el backoff").isZero();

        // Segundo fallo: la espera se duplica.
        clock.advance(Duration.ofSeconds(61));
        dispatcher.dispatchOnce();
        assertThat(((java.sql.Timestamp) outbox(outboxId).get("next_attempt_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(2)));

        // Se recupera el adjunto: el siguiente intento entrega el correo.
        voucherStorage.put(voucher.getPdfS3Key(), pdf, "application/pdf");
        clock.advance(Duration.ofMinutes(3));
        dispatcher.dispatchOnce();
        assertThat(outbox(outboxId).get("status")).isEqualTo("SENT");
        assertThat(receivedBy(email)).hasSize(1);

        // Otro mensaje en su último intento termina en DEAD y queda auditado.
        String other = registerTourist("Sara Ruiz");
        String otherBooking = confirmedBookingWithPdf(other, 1);
        Voucher otherVoucher = voucherRepository.findByBookingId(UUID.fromString(otherBooking)).orElseThrow();
        String otherId = jdbc.queryForObject(
                "SELECT id::text FROM notification.notification_outbox WHERE aggregate_id = ?", String.class,
                otherVoucher.getId().toString());
        jdbc.update("UPDATE notification.notification_outbox SET attempts = 7 WHERE id = ?::uuid", otherId);
        voucherStorage.remove(otherVoucher.getPdfS3Key());
        dispatcher.dispatchOnce();
        assertThat(outbox(otherId).get("status")).isEqualTo("DEAD");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'NOTIFICATION_DEAD' AND entity_id = ?""",
                Long.class, otherId)).isEqualTo(1);
    }

    @Test
    void abandonedProcessingMessagesAreRescued() throws Exception {
        String email = registerTourist("Elena Ruiz");
        String bookingId = confirmedBookingWithPdf(email, 1);
        UUID voucherId = voucherRepository.findByBookingId(UUID.fromString(bookingId)).orElseThrow().getId();
        String outboxId = jdbc.queryForObject(
                "SELECT id::text FROM notification.notification_outbox WHERE aggregate_id = ?", String.class,
                voucherId.toString());
        // Una instancia lo reclamó y murió antes de cerrarlo.
        jdbc.update("UPDATE notification.notification_outbox SET status = 'PROCESSING', attempts = 1 WHERE id = ?::uuid",
                outboxId);
        assertThat(dispatcher.dispatchOnce()).isZero();

        assertThat(dispatcher.rescueStale(Duration.ZERO)).isEqualTo(1);
        assertThat(outbox(outboxId).get("status")).isEqualTo("FAILED");
        clock.advance(Duration.ofMinutes(2));
        dispatcher.dispatchOnce();
        assertThat(outbox(outboxId).get("status")).isEqualTo("SENT");
    }

    @Test
    void concurrentDispatchersSendEachMessageOnce() throws Exception {
        String email = registerTourist("Jose Ruiz");
        confirmedBookingWithPdf(email, 1);

        List<Integer> processed = runConcurrently(3, dispatcher::dispatchOnce);

        assertThat(processed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(receivedBy(email)).hasSize(1);
    }

    // ---------------------------------------------------------------- fixtures

    private Map<String, Object> outbox(String id) {
        return jdbc.queryForMap("""
                SELECT status, attempts, next_attempt_at, last_error FROM notification.notification_outbox
                 WHERE id = ?::uuid""", id);
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

    private record Parts(String text, String html, String attachmentName, byte[] attachment) {
    }

    private static Parts parts(MimeMessage message) throws Exception {
        String[] found = new String[3];
        byte[][] attachment = new byte[1][];
        walk((Multipart) message.getContent(), found, attachment);
        return new Parts(found[0], found[1], found[2], attachment[0]);
    }

    private static void walk(Multipart multipart, String[] found, byte[][] attachment) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                walk(nested, found, attachment);
            } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                found[2] = part.getFileName();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                part.getInputStream().transferTo(out);
                attachment[0] = out.toByteArray();
            } else if (part.isMimeType("text/plain")) {
                found[0] = (String) part.getContent();
            } else if (part.isMimeType("text/html")) {
                found[1] = (String) part.getContent();
            }
        }
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reserva confirmada con su voucher y su PDF emitidos de forma síncrona (sin esperar al listener asíncrono). */
    private String confirmedBookingWithPdf(String email, int slots) throws Exception {
        String bookingId = confirmedBooking(email, slots);
        UUID voucherId = issuer.issue(UUID.fromString(bookingId)).orElseThrow();
        issuer.renderPdf(voucherId);
        return bookingId;
    }

    private String confirmedBooking(String email, int slots) throws Exception {
        String token = tokenFor(email);
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            participants.append(i == 0 ? "" : ",").append("""
                    {"fullName":"Participante %d","docType":"CC","docNumber":"4094000%03d","medicalEps":"Sura","isHolder":%s}"""
                    .formatted(i, i, i == 0));
        }
        String bookingId = JsonPath.read(mvc.perform(post("/api/v1/bookings/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"scheduleId":"%s","requestedSlots":%d,
                                 "emergencyContact":{"fullName":"Carlos Gomez","phone":"+573001234567"},
                                 "participants":[%s]}""".formatted(schedule(), slots, participants)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.bookingId");
        // Confirmación directa: el flujo de pago ya está cubierto por PaymentApiIntegrationTest.
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);
        return bookingId;
    }

    private String schedule() throws Exception {
        LocalDate day = LocalDate.ofInstant(START, BOGOTA).plusDays(30 + SEQ.incrementAndGet() % 300);
        String json = postJson("/api/v1/host/experiences/" + experienceId + "/schedules", hostToken,
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":6}]}"
                        .formatted(day, day))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private String approvedExperience() throws Exception {
        String id = JsonPath.read(postJson("/api/v1/host/experiences", hostToken, """
                {"title":"Sendero del correo","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Agua.",
                 "physicalIntensity":"MEDIUM","durationMinutes":240,"municipalityCode":"05001","vereda":"La Esperanza",
                 "meetingPoint":"Parque principal","latitude":6.25,"longitude":-75.56,"maxCapacityPerSlot":12,
                 "unitPriceCop":80000,"categoryCodes":["SENDERISMO_MEMORIA"]}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/v1/host/experiences/" + id + "/submit", hostToken, null).andExpect(status().isOk());
        postJson("/api/v1/admin/experiences/" + id + "/approve", adminToken, null).andExpect(status().isOk());
        return id;
    }

    private String createAssociation() throws Exception {
        long seq = SEQ.incrementAndGet();
        return JsonPath.read(postJson("/api/v1/admin/associations", adminToken, """
                {"nit":"5%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina del Correo",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 900_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist(String fullName) {
        String email = uniqueEmail("turista-correo");
        registerTourist(email, fullName);
        return email;
    }

    private void registerTourist(String email, String fullName) {
        registrationService.registerTourist(new RegisterRequest(email, PASSWORD, fullName, DocType.CC,
                uniqueDocNumber(), "+573001234567", new ConsentAcceptance(DEV_POLICY,
                Set.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.HEALTH_EMERGENCY))), CLIENT);
    }

    private String userWithRole(String prefix, RoleCode role) {
        String email = uniqueEmail(prefix);
        UUID id = registrationService.registerTourist(tourist(email), CLIENT).userId();
        roleAssignmentService.grant(id, role);
        return email;
    }

    private String tokenFor(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions postJson(String path, String token, String body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
