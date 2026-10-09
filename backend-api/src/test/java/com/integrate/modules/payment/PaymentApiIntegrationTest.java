package com.integrate.modules.payment;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.crypto.HmacSha256;
import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.booking.service.BookingExpirationService;
import com.integrate.modules.payment.service.model.BookingConfirmedEvent;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Cobro de punta a punta (ADR-0009 §9): inicio del pago, webhook Mock firmado de verdad, escrow, idempotencia,
 * aprobación tardía, rechazos, webhook nativo de Wompi y simulador de {@code dev}.
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class PaymentApiIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    /** Precio que no da un 85% entero: 80001 × 0,85 = 68000,85 → CEIL = 68001 (F3). */
    private static final long PRICE = 80_001;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private BookingExpirationService expirationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents applicationEvents;

    private String adminToken;
    private String hostToken;
    private String experienceId;
    private String touristEmail;
    private String touristToken;

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
        touristEmail = registerTourist();
        touristToken = tokenFor(touristEmail);
    }

    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    // ------------------------------------------------------------ inicio del pago

    @Test
    void startCreatesOneOpenAttemptAndReusesIt() throws Exception {
        String bookingId = lockBooking(schedule(4), 2);

        String paymentId = JsonPath.read(start(touristToken, bookingId, "PSE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.provider").value("MOCK_GATEWAY"))
                .andExpect(jsonPath("$.amountCop").value(2 * PRICE))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.bookingExpiresAt").value("2026-10-05T12:15:00Z"))
                .andExpect(jsonPath("$.checkout.type").value("MOCK"))
                .andReturn().getResponse().getContentAsString(), "$.paymentId");

        start(touristToken, bookingId, "NEQUI")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.paymentMethod").value("NEQUI"))
                .andExpect(jsonPath("$.checkout.reference").value(paymentId));

        assertThat(count("SELECT count(*) FROM payments.payment_transactions WHERE booking_id = ?::uuid", bookingId))
                .isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'PAYMENT_INITIATED' AND entity_id = ?""",
                paymentId)).isEqualTo(1);
    }

    @Test
    void onlyThePendingOwnerBookingIsPayable() throws Exception {
        String bookingId = lockBooking(schedule(4), 1);
        start(tokenFor(registerTourist()), bookingId, "PSE").andExpect(status().isNotFound());
        start(touristToken, UUID.randomUUID().toString(), "PSE").andExpect(status().isNotFound());
        start(touristToken, bookingId, "BITCOIN").andExpect(status().isBadRequest());

        advance(Duration.ofMinutes(16));
        start(touristToken, bookingId, "PSE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_BOOKING_NOT_PAYABLE"))
                .andExpect(jsonPath("$.bookingStatus").value("EXPIRED"));
    }

    // ------------------------------------------------------------- webhook Mock

    @Test
    void approvedEventConfirmsTheBookingAndKeepsTheCommunityShareInEscrow() throws Exception {
        String scheduleId = schedule(4);
        String bookingId = lockBooking(scheduleId, 1);
        String paymentId = startPayment(bookingId);
        String body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), bookingId, PRICE, "APPROVED");

        mockWebhook(body, signNow(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.provider").value("MOCK_GATEWAY"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT confirmed_at FROM booking.bookings WHERE id = ?::uuid",
                Timestamp.class, bookingId)).isNotNull();
        Map<String, Object> disbursement = jdbc.queryForMap("""
                SELECT d.amount_cop, d.status, d.release_at, s.end_time
                  FROM payments.disbursements d
                  JOIN payments.payment_transactions p ON p.id = d.payment_id
                  JOIN booking.bookings b ON b.id = p.booking_id
                  JOIN catalog.schedules s ON s.id = b.schedule_id
                 WHERE d.payment_id = ?::uuid""", paymentId);
        assertThat((BigDecimal) disbursement.get("amount_cop")).isEqualByComparingTo("68001");
        assertThat(disbursement.get("status")).isEqualTo("SCHEDULED");
        assertThat(((Timestamp) disbursement.get("release_at")).toInstant())
                .isEqualTo(((Timestamp) disbursement.get("end_time")).toInstant().plus(Duration.ofHours(24)));
        assertThat(applicationEvents.stream(BookingConfirmedEvent.class))
                .contains(new BookingConfirmedEvent(UUID.fromString(bookingId), UUID.fromString(paymentId)));
        assertThat(count("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'PAYMENT_APPROVED' AND entity_id = ?""",
                paymentId)).isEqualTo(1);
        assertThat(availableSlots(scheduleId)).isEqualTo(3);

        // El mismo evento otra vez: 200 sin segundos efectos.
        mockWebhook(body, signNow(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
        assertThat(count("SELECT count(*) FROM payments.disbursements WHERE payment_id = ?::uuid", paymentId))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM audit.verify_chain()")).isEmpty();
    }

    @Test
    void declinedAndVoidedEventsReleaseTheSlots() throws Exception {
        String scheduleId = schedule(4);
        String declined = lockBooking(scheduleId, 2);
        startPayment(declined);
        String voided = lockBooking(scheduleId, 1);
        startPayment(voided);
        assertThat(availableSlots(scheduleId)).isEqualTo(1);

        String body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), declined, 2 * PRICE, "DECLINED");
        mockWebhook(body, signNow(body))
                .andExpect(jsonPath("$.paymentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.bookingStatus").value("PAYMENT_FAILED"));
        body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), voided, PRICE, "VOIDED");
        mockWebhook(body, signNow(body))
                .andExpect(jsonPath("$.paymentStatus").value("VOIDED"))
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED_REFUNDED"));

        assertThat(availableSlots(scheduleId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT has_drift FROM booking.v_schedule_slot_reconciliation WHERE schedule_id = ?::uuid""",
                Boolean.class, scheduleId)).isFalse();
        assertThat(count("SELECT count(*) FROM payments.disbursements d JOIN payments.payment_transactions p "
                + "ON p.id = d.payment_id WHERE p.booking_id IN (?::uuid, ?::uuid)", declined, voided)).isZero();
    }

    @Test
    void gatewayErrorKeepsTheBookingPendingAndANewAttemptIsCreated() throws Exception {
        String bookingId = lockBooking(schedule(4), 1);
        String first = startPayment(bookingId);
        String body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), bookingId, PRICE, "ERROR");

        mockWebhook(body, signNow(body))
                .andExpect(jsonPath("$.paymentStatus").value("ERROR"))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING"));

        String second = JsonPath.read(start(touristToken, bookingId, "CARD").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.paymentId");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void approvalAfterTheHoldExpiredOrdersAFullRefund() throws Exception {
        String scheduleId = schedule(4);
        String bookingId = lockBooking(scheduleId, 2);
        String paymentId = startPayment(bookingId);
        advance(Duration.ofMinutes(16));
        while (expirationService.expireBatch(100).expired() == 100) {
            // otro lote
        }
        assertThat(bookingStatus(bookingId)).isEqualTo("EXPIRED");

        String body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), bookingId, 2 * PRICE,
                "APPROVED");
        mockWebhook(body, signNow(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.bookingStatus").value("EXPIRED"));

        Map<String, Object> refund = jdbc.queryForMap(
                "SELECT reason, status, amount_cop FROM payments.refunds WHERE payment_id = ?::uuid", paymentId);
        assertThat(refund.get("reason")).isEqualTo("LATE_APPROVAL");
        assertThat(refund.get("status")).isEqualTo("REQUESTED");
        assertThat((BigDecimal) refund.get("amount_cop")).isEqualByComparingTo(BigDecimal.valueOf(2 * PRICE));
        assertThat(count("SELECT count(*) FROM payments.disbursements WHERE payment_id = ?::uuid", paymentId)).isZero();
        assertThat(count("""
                SELECT count(*) FROM audit.audit_trail
                 WHERE action = 'PAYMENT_LATE_APPROVAL' AND severity = 'HIGH' AND entity_id = ?""", paymentId))
                .isEqualTo(1);
        assertThat(availableSlots(scheduleId)).isEqualTo(4);
    }

    @Test
    void invalidEventsAreRejectedWithoutTouchingTheInbox() throws Exception {
        String bookingId = lockBooking(schedule(4), 1);
        startPayment(bookingId);
        long inbox = count("SELECT count(*) FROM payments.payment_webhook_events");
        long rejected = count("SELECT count(*) FROM audit.audit_trail WHERE action = 'PAYMENT_WEBHOOK_REJECTED'");
        String body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), bookingId, PRICE, "APPROVED");
        long now = clock.instant().getEpochSecond();

        mockWebhook(body, Map.of("X-Signature", "0".repeat(64), "X-Timestamp", Long.toString(now)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mockWebhook(body, sign(body, now - 301))
                .andExpect(status().isUnauthorized());
        mockWebhook(body.replace("MOCK_GATEWAY", "WOMPI"), signNow(body.replace("MOCK_GATEWAY", "WOMPI")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_MISMATCH"));
        String unknown = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(),
                UUID.randomUUID().toString(), PRICE, "APPROVED");
        mockWebhook(unknown, signNow(unknown))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_BOOKING_NOT_FOUND"));
        String wrongAmount = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), bookingId, 1,
                "APPROVED");
        mockWebhook(wrongAmount, signNow(wrongAmount))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        mockWebhook("{no es json", signNow("{no es json")).andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM payments.payment_webhook_events")).isEqualTo(inbox);
        assertThat(count("SELECT count(*) FROM audit.audit_trail WHERE action = 'PAYMENT_WEBHOOK_REJECTED'"))
                .isEqualTo(rejected + 2);
        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING");
    }

    @Test
    void theSameEventDeliveredConcurrentlyIsProcessedOnce() throws Exception {
        String bookingId = lockBooking(schedule(4), 1);
        String paymentId = startPayment(bookingId);
        String body = mockEvent("evt-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), bookingId, PRICE, "APPROVED");
        Map<String, String> headers = signNow(body);

        List<String> duplicates = runConcurrently(2, () -> JsonPath.read(mockWebhook(body, headers)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.duplicate").toString());

        assertThat(duplicates).containsExactlyInAnyOrder("true", "false");
        assertThat(count("SELECT count(*) FROM payments.disbursements WHERE payment_id = ?::uuid", paymentId))
                .isEqualTo(1);
    }

    // --------------------------------------------------- Wompi y simulador de dev

    @Test
    void nativeWompiEventConfirmsAWompiAttempt() throws Exception {
        String bookingId = lockBooking(schedule(4), 1);
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payments.payment_transactions
                       (id, booking_id, commission_policy_id, provider, payment_method, idempotency_key,
                        gateway_reference, amount_cop)
                SELECT ?::uuid, ?::uuid, id, 'WOMPI', 'PSE', ?, ?, ? FROM payments.commission_policies
                 WHERE code = 'STANDARD_2026'""", paymentId.toString(), bookingId, "pay:" + paymentId,
                paymentId.toString(), BigDecimal.valueOf(PRICE));
        String body = wompiEvent("wompi-tx-" + UUID.randomUUID(), paymentId.toString(), PRICE * 100, "APPROVED");

        wompiWebhook(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("WOMPI"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
        wompiWebhook(body).andExpect(jsonPath("$.duplicate").value(true));
        wompiWebhook(body.replace("\"status\":\"APPROVED\"", "\"status\":\"DECLINED\""))
                .andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM payments.disbursements WHERE payment_id = ?::uuid",
                paymentId.toString())).isEqualTo(1);
    }

    @Test
    void devSimulatorCompletesTheOwnersPayment() throws Exception {
        String bookingId = lockBooking(schedule(4), 1);
        String paymentId = startPayment(bookingId);

        mvc.perform(post("/api/v1/dev/payments/" + paymentId + "/simulate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(registerTourist())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/dev/payments/" + paymentId + "/simulate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
    }

    // ---------------------------------------------------------------- fixtures

    private String startPayment(String bookingId) throws Exception {
        return JsonPath.read(start(touristToken, bookingId, "PSE").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.paymentId");
    }

    private ResultActions start(String token, String bookingId, String method) throws Exception {
        return postJson("/api/v1/bookings/" + bookingId + "/payment", token, "{\"paymentMethod\":\"" + method + "\"}");
    }

    private ResultActions mockWebhook(String body, Map<String, String> headers) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/payments/webhook/mock")
                .contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
        headers.forEach(request::header);
        return mvc.perform(request);
    }

    private ResultActions wompiWebhook(String body) throws Exception {
        return mvc.perform(post("/api/v1/payments/webhook/wompi").contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> signNow(String body) {
        return sign(body, clock.instant().getEpochSecond());
    }

    /** Firma del contrato: {@code hex(HMAC_SHA256(secreto, X-Timestamp + "." + raw_body))}. */
    private static Map<String, String> sign(String body, long timestamp) {
        String signature = HmacSha256.hex(Base64.getDecoder().decode(MOCK_WEBHOOK_SECRET),
                (timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return Map.of("X-Signature", signature, "X-Timestamp", Long.toString(timestamp));
    }

    private String mockEvent(String eventId, String transactionId, String bookingId, long amount, String status) {
        return """
                {"eventId":"%s","provider":"MOCK_GATEWAY","transactionId":"%s","bookingId":"%s",
                 "amountCop":%d,"status":"%s","timestamp":"%s"}"""
                .formatted(eventId, transactionId, bookingId, amount, status, clock.instant());
    }

    private String wompiEvent(String txId, String reference, long cents, String status) throws Exception {
        long timestamp = clock.instant().getEpochSecond();
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((txId + status + cents + timestamp + WOMPI_EVENTS_SECRET).getBytes(StandardCharsets.UTF_8)));
        return """
                {"event":"transaction.updated","environment":"test","timestamp":%d,
                 "data":{"transaction":{"id":"%s","reference":"%s","amount_in_cents":%d,"currency":"COP",
                         "status":"%s"}},
                 "signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],
                              "checksum":"%s"}}""".formatted(timestamp, txId, reference, cents, status, checksum);
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

    /** Adelanta el reloj y renueva el token: el JWT se valida con el mismo reloj de prueba. */
    private void advance(Duration duration) throws Exception {
        clock.advance(duration);
        touristToken = tokenFor(touristEmail);
    }

    private String lockBooking(String scheduleId, int slots) throws Exception {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            participants.append(i == 0 ? "" : ",").append("""
                    {"fullName":"Participante %d","docType":"CC","docNumber":"20940%05d","medicalEps":"Sura","isHolder":%s}"""
                    .formatted(i, i, i == 0));
        }
        String body = """
                {"scheduleId":"%s","requestedSlots":%d,
                 "emergencyContact":{"fullName":"Carlos Gomez","phone":"+573001234567"},"participants":[%s]}"""
                .formatted(scheduleId, slots, participants);
        return JsonPath.read(mvc.perform(post("/api/v1/bookings/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.bookingId");
    }

    private String schedule(int slots) throws Exception {
        LocalDate day = LocalDate.ofInstant(START, BOGOTA).plusDays(30 + SEQ.incrementAndGet() % 300);
        String json = postJson("/api/v1/host/experiences/" + experienceId + "/schedules", hostToken,
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":%d}]}"
                        .formatted(day, day, slots))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private String approvedExperience() throws Exception {
        String id = JsonPath.read(postJson("/api/v1/host/experiences", hostToken, """
                {"title":"Ruta de pagos","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Agua.",
                 "physicalIntensity":"MEDIUM","durationMinutes":240,"municipalityCode":"05001","vereda":"La Esperanza",
                 "meetingPoint":"Parque principal","latitude":6.25,"longitude":-75.56,"maxCapacityPerSlot":12,
                 "unitPriceCop":%d,"categoryCodes":["SENDERISMO_MEMORIA"]}""".formatted(PRICE))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/v1/host/experiences/" + id + "/submit", hostToken, null).andExpect(status().isOk());
        postJson("/api/v1/admin/experiences/" + id + "/approve", adminToken, null).andExpect(status().isOk());
        return id;
    }

    private String createAssociation() throws Exception {
        long seq = SEQ.incrementAndGet();
        return JsonPath.read(postJson("/api/v1/admin/associations", adminToken, """
                {"nit":"7%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina de Pagos",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 600_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        String email = uniqueEmail("turista-pago");
        registrationService.registerTourist(tourist(email, uniqueDocNumber(), new ConsentAcceptance(DEV_POLICY,
                Set.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.HEALTH_EMERGENCY))), CLIENT);
        return email;
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

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private int availableSlots(String scheduleId) {
        return jdbc.queryForObject("SELECT available_slots FROM catalog.schedules WHERE id = ?::uuid", Integer.class,
                scheduleId);
    }

    private String bookingStatus(String bookingId) {
        return jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class, bookingId);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
