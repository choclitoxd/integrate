package com.integrate.modules.booking;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.common.crypto.SensitiveDataCipher;
import com.integrate.common.crypto.SensitiveDataException;
import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.booking.service.BookingExpirationService;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Motor de reservas de punta a punta (ADR-0008 §9) sobre la cadena real de Spring Security y PostgreSQL:
 * bloqueo pesimista, idempotencia, consentimiento, cifrado, auditoría, concurrencia y worker TTL.
 */
@AutoConfigureMockMvc
class BookingApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private BookingExpirationService expirationService;

    @Autowired
    private SensitiveDataCipher cipher;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String hostToken;
    private String experienceId;
    private String touristEmail;
    private String touristToken;
    private UUID touristId;

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
        experienceId = approvedExperience("Sendero de la reconciliación");

        touristEmail = uniqueEmail("turista");
        touristId = registerTourist(touristEmail, true);
        touristToken = tokenFor(touristEmail);
    }

    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    // ---------------------------------------------------------------- lock exitoso

    @Test
    void lockCreatesPendingBookingAndDiscountsSlots() throws Exception {
        String scheduleId = schedule(4);
        UUID key = UUID.randomUUID();

        MvcResult result = lock(touristToken, key, lockBody(scheduleId, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.scheduleId").value(scheduleId))
                .andExpect(jsonPath("$.experienceId").value(experienceId))
                .andExpect(jsonPath("$.lockedSlots").value(2))
                .andExpect(jsonPath("$.unitPriceCop").value(80000))
                .andExpect(jsonPath("$.totalAmountCop").value(160000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T12:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-05T12:15:00Z"))
                .andExpect(jsonPath("$.ttlSecondsRemaining").value(900))
                .andReturn();
        String bookingId = JsonPath.read(result.getResponse().getContentAsString(), "$.bookingId");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/bookings/" + bookingId);

        assertThat(availableSlots(scheduleId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking.booking_participants WHERE booking_id = ?::uuid",
                Long.class, bookingId)).isEqualTo(2);
        Map<String, Object> history = jdbc.queryForMap("""
                SELECT from_status, to_status, changed_by FROM booking.booking_status_history
                 WHERE booking_id = ?::uuid""", bookingId);
        assertThat(history.get("to_status")).isEqualTo("PENDING");
        assertThat(history.get("changed_by")).isEqualTo(touristId);

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT module, action, actor_id, details::text AS details FROM audit.audit_trail
                 WHERE entity_name = 'bookings' AND entity_id = ?""", bookingId);
        assertThat(audit.get("module")).isEqualTo("booking");
        assertThat(audit.get("action")).isEqualTo("BOOKING_SLOTS_LOCKED");
        assertThat(audit.get("actor_id")).isEqualTo(touristId);
        assertThat((String) audit.get("details")).contains(scheduleId, "160000")
                .doesNotContain("Sura", "Carlos", "1094");
        assertThat(jdbc.queryForList("SELECT * FROM audit.verify_chain()")).isEmpty();

        mvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void healthDataIsEncryptedAndBoundToItsRow() throws Exception {
        String bookingId = bookingIdOf(lock(touristToken, UUID.randomUUID(), lockBody(schedule(4), 2))
                .andExpect(status().isCreated()));

        Map<String, Object> holder = jdbc.queryForMap("""
                SELECT doc_type, doc_number, eps_enc, sensitive_key_version FROM booking.booking_participants
                 WHERE booking_id = ?::uuid AND is_holder""", bookingId);
        byte[] eps = (byte[]) holder.get("eps_enc");
        short version = ((Number) holder.get("sensitive_key_version")).shortValue();
        String aad = "booking.booking_participants.eps_enc:" + bookingId + ":" + holder.get("doc_type") + ":"
                + holder.get("doc_number");
        assertThat(new String(eps, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Sura");
        assertThat(cipher.decrypt(eps, aad, version)).isEqualTo("Sura");
        assertThatThrownBy(() -> cipher.decrypt(eps, aad.replace(bookingId, UUID.randomUUID().toString()), version))
                .isInstanceOf(SensitiveDataException.class);

        Map<String, Object> contact = jdbc.queryForMap("""
                SELECT emergency_contact_name_enc, emergency_contact_phone_enc FROM booking.bookings
                 WHERE id = ?::uuid""", bookingId);
        assertThat(cipher.decrypt((byte[]) contact.get("emergency_contact_phone_enc"),
                "booking.bookings.emergency_contact_phone_enc:" + bookingId, version)).isEqualTo("+573001234567");
        assertThat(cipher.decrypt((byte[]) contact.get("emergency_contact_name_enc"),
                "booking.bookings.emergency_contact_name_enc:" + bookingId, version)).isEqualTo("Carlos Gómez");
    }

    // ------------------------------------------------------------- idempotencia

    @Test
    void sameKeyAndPayloadReplaysWithoutDiscountingTwice() throws Exception {
        String scheduleId = schedule(4);
        UUID key = UUID.randomUUID();
        String bookingId = bookingIdOf(lock(touristToken, key, lockBody(scheduleId, 1)).andExpect(status().isCreated()));

        advance(Duration.ofMinutes(5));
        lock(touristToken, key, lockBody(scheduleId, 1))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.ttlSecondsRemaining").value(600));
        assertThat(availableSlots(scheduleId)).isEqualTo(3);

        lock(touristToken, key, lockBody(scheduleId, 1).replace("Sura", "Sanitas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // Fuera de la ventana de 24 h la clave ya no se reproduce.
        advance(Duration.ofHours(25));
        lock(touristToken, key, lockBody(scheduleId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void replayReportsTheCurrentStatusAfterExpiration() throws Exception {
        String scheduleId = schedule(4);
        UUID key = UUID.randomUUID();
        String bookingId = bookingIdOf(lock(touristToken, key, lockBody(scheduleId, 2)).andExpect(status().isCreated()));

        advance(Duration.ofMinutes(16));
        expireAll();

        lock(touristToken, key, lockBody(scheduleId, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.ttlSecondsRemaining").value(0));
        mvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOneBooking() throws Exception {
        String scheduleId = schedule(6);
        UUID key = UUID.randomUUID();
        String body = lockBody(scheduleId, 2);

        List<Integer> statuses = runConcurrently(2, i -> lock(touristToken, key, body).andReturn()
                .getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking.bookings WHERE idempotency_key = ?::uuid",
                Long.class, key.toString())).isEqualTo(1);
        assertThat(availableSlots(scheduleId)).isEqualTo(4);
    }

    // ------------------------------------------------------------ concurrencia

    @Test
    void contentionForTheLastSlotsNeverOversells() throws Exception {
        String scheduleId = schedule(5);
        String body = lockBody(scheduleId, 1);

        List<Integer> statuses = runConcurrently(20, i -> lock(touristToken, UUID.randomUUID(), body).andReturn()
                .getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(15);
        assertThat(availableSlots(scheduleId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT has_drift FROM booking.v_schedule_slot_reconciliation WHERE schedule_id = ?::uuid""",
                Boolean.class, scheduleId)).isFalse();
    }

    @Test
    void lockTimeoutAnswers503WithRetryAfter() throws Exception {
        String scheduleId = schedule(4);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement select = holder.prepareStatement(
                    "SELECT id FROM catalog.schedules WHERE id = ?::uuid FOR UPDATE")) {
                select.setString(1, scheduleId);
                select.executeQuery().close();
            }
            lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.code").value("CONCURRENCY_LOCK_TIMEOUT"));
            holder.rollback();
        }
        assertThat(availableSlots(scheduleId)).isEqualTo(4);
    }

    // ------------------------------------------------------------- rechazos

    @Test
    void scheduleRulesFollowTheContract() throws Exception {
        String scheduleId = schedule(3);
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_INSUFFICIENT_SLOTS"))
                .andExpect(jsonPath("$.availableSlots").value(3))
                .andExpect(jsonPath("$.invalidParams[0].name").value("requestedSlots"));

        lock(touristToken, UUID.randomUUID(), lockBody(UUID.randomUUID().toString(), 1))
                .andExpect(status().isNotFound());

        String draft = createExperience("Borrador sin publicar");
        lock(touristToken, UUID.randomUUID(), lockBody(createSchedule(draft, 4), 1))
                .andExpect(status().isNotFound());

        String cancelled = schedule(4);
        postJson("/api/v1/host/schedules/" + cancelled + "/cancel", hostToken, "{\"reason\":\"Lluvias\"}")
                .andExpect(status().isOk());
        lock(touristToken, UUID.randomUUID(), lockBody(cancelled, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_SCHEDULE_NOT_BOOKABLE"));

        Instant start = jdbc.queryForObject("SELECT start_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, scheduleId).toInstant();
        clock.set(start.plusSeconds(60));
        touristToken = tokenFor(touristEmail);
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_SCHEDULE_NOT_BOOKABLE"));
        assertThat(availableSlots(scheduleId)).isEqualTo(3);
    }

    @Test
    void bodyValidationAnswers422And400() throws Exception {
        String scheduleId = schedule(4);
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 2).replace("\"requestedSlots\":2",
                "\"requestedSlots\":3"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("participants"));
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 2).replace("1094000222", "1094000111"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("participants[1].docNumber"));
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 2).replace("\"isHolder\":false",
                "\"isHolder\":true"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1).replace("\"requestedSlots\":1",
                "\"requestedSlots\":0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidParams[0].name").value("requestedSlots"));
        lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1).replace("\"requestedSlots\":1",
                "\"requestedSlots\":\"uno\""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/bookings/lock").header(HttpHeaders.AUTHORIZATION, bearer(touristToken))
                        .contentType(MediaType.APPLICATION_JSON).content(lockBody(scheduleId, 1)))
                .andExpect(status().isBadRequest());
        assertThat(availableSlots(scheduleId)).isEqualTo(4);
    }

    @Test
    void healthConsentIsAPrecondition() throws Exception {
        String email = uniqueEmail("sin-consentimiento");
        registerTourist(email, false);
        lock(tokenFor(email), UUID.randomUUID(), lockBody(schedule(4), 1))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONSENT_HEALTH_EMERGENCY_REQUIRED"))
                .andExpect(jsonPath("$.requiredPurposes[0]").value("HEALTH_EMERGENCY"))
                .andExpect(jsonPath("$.policyVersion").value(DEV_POLICY));
    }

    @Test
    void onlyTouristsLockAndOnlyTheOwnerReads() throws Exception {
        String scheduleId = schedule(4);
        // Los fixtures nacen como turistas: se revoca ese rol para tener un usuario que solo sea HOST.
        String hostOnlyEmail = userWithRole("solo-anfitrion", RoleCode.HOST);
        jdbc.update("""
                UPDATE auth.user_roles SET revoked_at = now()
                 WHERE role_id = (SELECT id FROM auth.roles WHERE code = 'TOURIST')
                   AND user_id = (SELECT id FROM auth.users WHERE email = ?)""", hostOnlyEmail);
        lock(tokenFor(hostOnlyEmail), UUID.randomUUID(), lockBody(scheduleId, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RBAC_FORBIDDEN"));
        mvc.perform(post("/api/v1/bookings/lock").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(lockBody(scheduleId, 1)))
                .andExpect(status().isUnauthorized());

        String bookingId = bookingIdOf(lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1))
                .andExpect(status().isCreated()));
        String otherEmail = uniqueEmail("otro-turista");
        registerTourist(otherEmail, true);
        mvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(otherEmail))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/bookings/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- worker TTL

    @Test
    void expirationWorkerReleasesSlotsOnlyForOverdueBookings() throws Exception {
        String scheduleId = schedule(6);
        String overdue = bookingIdOf(lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 2))
                .andExpect(status().isCreated()));
        advance(Duration.ofMinutes(10));
        String fresh = bookingIdOf(lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1))
                .andExpect(status().isCreated()));
        assertThat(availableSlots(scheduleId)).isEqualTo(3);

        clock.advance(Duration.ofMinutes(6));
        expireAll();

        assertThat(bookingStatus(overdue)).isEqualTo("EXPIRED");
        assertThat(bookingStatus(fresh)).isEqualTo("PENDING");
        assertThat(availableSlots(scheduleId)).isEqualTo(5);
        Map<String, Object> history = jdbc.queryForMap("""
                SELECT from_status, reason, changed_by FROM booking.booking_status_history
                 WHERE booking_id = ?::uuid AND to_status = 'EXPIRED'""", overdue);
        assertThat(history.get("from_status")).isEqualTo("PENDING");
        assertThat(history.get("reason")).isEqualTo("TTL_EXPIRED");
        assertThat(history.get("changed_by")).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail
                 WHERE action = 'BOOKING_EXPIRED' AND entity_id = ? AND actor_id IS NULL""", Long.class, overdue))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT has_drift FROM booking.v_schedule_slot_reconciliation WHERE schedule_id = ?::uuid""",
                Boolean.class, scheduleId)).isFalse();
    }

    @Test
    void parallelWorkersNeverExpireTheSameBookingTwice() throws Exception {
        String scheduleId = schedule(10);
        for (int i = 0; i < 8; i++) {
            lock(touristToken, UUID.randomUUID(), lockBody(scheduleId, 1)).andExpect(status().isCreated());
        }
        clock.advance(Duration.ofMinutes(16));

        List<Integer> expired = runConcurrently(2, i -> {
            int total = 0;
            for (int batch = expirationService.expireBatch(3).expired(); batch > 0;
                    batch = expirationService.expireBatch(3).expired()) {
                total += batch;
            }
            return total;
        });

        assertThat(expired.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(8);
        assertThat(availableSlots(scheduleId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM booking.booking_status_history h JOIN booking.bookings b ON b.id = h.booking_id
                 WHERE b.schedule_id = ?::uuid AND h.to_status = 'EXPIRED'""", Long.class, scheduleId)).isEqualTo(8);
    }

    // ---------------------------------------------------------------- fixtures

    /** Adelanta el reloj y renueva el token: el JWT se valida con el mismo reloj de prueba. */
    private void advance(Duration duration) throws Exception {
        clock.advance(duration);
        touristToken = tokenFor(touristEmail);
    }

    private void expireAll() {
        while (expirationService.expireBatch(100).expired() == 100) {
            // otro lote
        }
    }

    private <T> List<T> runConcurrently(int threads, ConcurrentTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<T> call = () -> {
                    start.await();
                    return task.run(index);
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConcurrentTask<T> {
        T run(int index) throws Exception;
    }

    private ResultActions lock(String token, UUID key, String body) throws Exception {
        return mvc.perform(post("/api/v1/bookings/lock").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String bookingIdOf(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.bookingId");
    }

    /** Cuerpo del contrato con {@code slots} participantes; el primero es el titular. */
    private static String lockBody(String scheduleId, int slots) {
        String participants = String.join(",", IntStream.range(0, slots).mapToObj(i -> """
                {"fullName":"Participante %d","docType":"CC","docNumber":"%s","medicalEps":"%s","isHolder":%s}"""
                .formatted(i, i == 0 ? "1094000111" : i == 1 ? "1094000222" : "10940003" + (10 + i),
                        i == 0 ? "Sura" : "Sanitas", i == 0)).toList());
        return """
                {"scheduleId":"%s","requestedSlots":%d,
                 "emergencyContact":{"fullName":"Carlos Gómez","phone":"+573001234567"},
                 "participants":[%s]}""".formatted(scheduleId, slots, participants);
    }

    private int availableSlots(String scheduleId) {
        return jdbc.queryForObject("SELECT available_slots FROM catalog.schedules WHERE id = ?::uuid", Integer.class,
                scheduleId);
    }

    private String bookingStatus(String bookingId) {
        return jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class, bookingId);
    }

    private UUID registerTourist(String email, boolean healthConsent) {
        ConsentAcceptance consent = new ConsentAcceptance(DEV_POLICY, healthConsent
                ? java.util.Set.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.HEALTH_EMERGENCY)
                : java.util.Set.of(ConsentPurpose.PERSONAL_DATA));
        return registrationService.registerTourist(tourist(email, uniqueDocNumber(), consent), CLIENT).userId();
    }

    /** Una franja de 07:00 a 12:00 hora local, 30 días después del reloj de prueba (ver nota en la clase base). */
    private String schedule(int slots) throws Exception {
        return createSchedule(experienceId, slots);
    }

    private String createSchedule(String experience, int slots) throws Exception {
        LocalDate day = LocalDate.ofInstant(START, BOGOTA).plusDays(30 + SEQ.incrementAndGet() % 300);
        String json = postJson("/api/v1/host/experiences/" + experience + "/schedules", hostToken,
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":%d}]}"
                        .formatted(day, day, slots))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private String createExperience(String title) throws Exception {
        return JsonPath.read(postJson("/api/v1/host/experiences", hostToken, """
                {"title":"%s","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Llevar agua.",
                 "physicalIntensity":"MEDIUM","durationMinutes":240,"municipalityCode":"05001","vereda":"La Esperanza",
                 "meetingPoint":"Parque principal","latitude":6.25,"longitude":-75.56,"maxCapacityPerSlot":12,
                 "unitPriceCop":80000,"categoryCodes":["SENDERISMO_MEMORIA"]}""".formatted(title))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String approvedExperience(String title) throws Exception {
        String id = createExperience(title);
        postJson("/api/v1/host/experiences/" + id + "/submit", hostToken, null).andExpect(status().isOk());
        postJson("/api/v1/admin/experiences/" + id + "/approve", adminToken, null).andExpect(status().isOk());
        return id;
    }

    private String createAssociation() throws Exception {
        long seq = SEQ.incrementAndGet();
        return JsonPath.read(postJson("/api/v1/admin/associations", adminToken, """
                {"nit":"8%08d-%d","rntNumber":"%d","legalName":"Asociación Campesina La Esperanza",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 500_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
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
