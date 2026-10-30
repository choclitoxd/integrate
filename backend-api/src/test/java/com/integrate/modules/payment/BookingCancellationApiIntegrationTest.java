package com.integrate.modules.payment;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.payment.gateway.MockRefundGateway;
import com.integrate.modules.payment.gateway.RefundGateway.Result;
import com.integrate.modules.payment.service.impl.RefundProcessor;
import com.integrate.modules.voucher.service.impl.VoucherIssuer;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Cancelación del turista con la regla de 72 horas y ejecución de reembolsos (ADR-0034 §2 y §3): efectos sobre
 * reserva, voucher, dispersión, cupos y reembolso; rechazos; protección del 85 % comunitario; y la carrera con el canje.
 */
@AutoConfigureMockMvc
class BookingCancellationApiIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String HOLDER_DOC = "30970000";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private VoucherIssuer issuer;

    @Autowired
    private RefundProcessor refundWorker;

    @Autowired
    private MockRefundGateway refundGateway;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String hostToken;
    private String experienceId;
    private String touristEmail;
    private String touristToken;
    private String associationId;
    private String scheduleId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(START);
        adminToken = tokenFor(userWithRole("admin", RoleCode.ADMIN));
        associationId = createAssociation();
        String hostEmail = uniqueEmail("anfitrion");
        UUID hostUserId = registrationService.registerTourist(tourist(hostEmail), CLIENT).userId();
        postJson("/api/v1/admin/host-profiles", adminToken,
                "{\"userId\":\"" + hostUserId + "\",\"associationId\":\"" + associationId + "\"}")
                .andExpect(status().isCreated());
        hostToken = tokenFor(hostEmail);
        experienceId = approvedExperience();
        touristEmail = registerTourist();
        touristToken = tokenFor(touristEmail);
        scheduleId = schedule();
    }

    @AfterEach
    void restoreClock() {
        clock.set(START);
        refundGateway.next(null);
    }

    // ------------------------------------------------------------ cancelación anticipada

    @Test
    void cancellingMoreThanSeventyTwoHoursAheadRefundsEightyFivePercentAndFreesTheSeats() throws Exception {
        Paid paid = paidBooking(2);
        int availableBefore = availableSlots();

        cancel(touristToken, paid.bookingId, "Cambio de planes")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.bookingId").value(paid.bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED_REFUNDED"))
                .andExpect(jsonPath("$.policyApplied").value("EARLY_REFUND"))
                .andExpect(jsonPath("$.touristRefundPct").value(85.0))
                .andExpect(jsonPath("$.refundAmountCop").value(136000))
                .andExpect(jsonPath("$.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.slotsReleased").value(true));

        assertThat(rowStatus("booking.bookings", paid.bookingId)).isEqualTo("CANCELLED_REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, paid.voucherId))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("""
                SELECT d.status || '/' || d.cancellation_reason FROM payments.disbursements d
                  JOIN payments.payment_transactions p ON p.id = d.payment_id WHERE p.booking_id = ?::uuid""",
                String.class, paid.bookingId)).startsWith("CANCELLED/");
        assertThat(availableSlots()).isEqualTo(availableBefore + 2);
        assertThat(jdbc.queryForMap("""
                SELECT policy_applied, tourist_refund_pct, actor_type FROM booking.booking_cancellations
                 WHERE booking_id = ?::uuid""", paid.bookingId))
                .containsEntry("policy_applied", "EARLY_REFUND").containsEntry("actor_type", "TOURIST");
        assertThat(jdbc.queryForMap("""
                SELECT r.reason, r.amount_cop, r.status FROM payments.refunds r
                  JOIN payments.payment_transactions p ON p.id = r.payment_id WHERE p.booking_id = ?::uuid""",
                paid.bookingId)).containsEntry("reason", "EARLY_CANCELLATION").containsEntry("status", "REQUESTED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'BOOKING_CANCELLED' AND entity_id = ?""",
                Long.class, paid.bookingId)).isEqualTo(1);
    }

    @Test
    void theRefundWorkerExecutesTheRefundOnceAndIsIdempotent() throws Exception {
        Paid paid = paidBooking(2);
        cancel(touristToken, paid.bookingId, "Cambio de planes").andExpect(status().isOk());

        assertThat(refundWorker.sweepOnce()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForMap("""
                SELECT r.status, r.gateway_refund_id, r.completed_at FROM payments.refunds r
                  JOIN payments.payment_transactions p ON p.id = r.payment_id WHERE p.booking_id = ?::uuid""",
                paid.bookingId)).containsEntry("status", "COMPLETED")
                .satisfies(row -> {
                    assertThat(row.get("gateway_refund_id")).isNotNull();
                    assertThat(row.get("completed_at")).isNotNull();
                });
        // Un segundo barrido no vuelve a reembolsar nada de esta reserva.
        refundWorker.sweepOnce();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM payments.refunds r JOIN payments.payment_transactions p ON p.id = r.payment_id
                 WHERE p.booking_id = ?::uuid AND r.status = 'COMPLETED'""", Long.class, paid.bookingId)).isEqualTo(1);
    }

    @Test
    void aTransientGatewayErrorKeepsTheRefundOpenAndARejectionClosesItAsFailed() throws Exception {
        Paid first = paidBooking(1);
        cancel(touristToken, first.bookingId, "Cambio de planes").andExpect(status().isOk());

        refundGateway.next(Result.retry());
        refundWorker.sweepOnce();
        assertThat(refundStatus(first.bookingId)).isEqualTo("PROCESSING");
        refundWorker.sweepOnce();
        assertThat(refundStatus(first.bookingId)).isEqualTo("COMPLETED");

        Paid second = paidBooking(1);
        cancel(touristToken, second.bookingId, "Cambio de planes").andExpect(status().isOk());
        refundGateway.next(Result.rejected("Cuenta de origen cerrada"));
        refundWorker.sweepOnce();
        assertThat(jdbc.queryForMap("""
                SELECT r.status, r.failure_reason FROM payments.refunds r
                  JOIN payments.payment_transactions p ON p.id = r.payment_id WHERE p.booking_id = ?::uuid""",
                second.bookingId)).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "Cuenta de origen cerrada");
    }

    @Test
    void aRefundOfABookingInSyncConflictIsNotPaid() throws Exception {
        Paid paid = paidBooking(1);
        cancel(touristToken, paid.bookingId, "Cambio de planes").andExpect(status().isOk());
        // El gestor usó el voucher en campo antes de sincronizar la cancelación (ADR-0033).
        jdbc.update("UPDATE booking.bookings SET status = 'SYNC_CONFLICT' WHERE id = ?::uuid", paid.bookingId);

        refundWorker.sweepOnce();

        assertThat(jdbc.queryForMap("""
                SELECT r.status, r.failure_reason, r.gateway_refund_id FROM payments.refunds r
                  JOIN payments.payment_transactions p ON p.id = r.payment_id WHERE p.booking_id = ?::uuid""",
                paid.bookingId)).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "SYNC_CONFLICT_COMMUNITY_PROTECTED")
                .containsEntry("gateway_refund_id", null);
    }

    @Test
    void anApprovedAfterExpiryRefundOrderIsAlsoExecuted() throws Exception {
        Paid paid = paidBooking(1);
        jdbc.update("""
                INSERT INTO payments.refunds (payment_id, reason, amount_cop, idempotency_key, status)
                VALUES (?::uuid, 'LATE_APPROVAL', 1000, ?, 'REQUESTED')""", paid.paymentId, "refund:late:" + paid.paymentId);

        refundWorker.sweepOnce();

        assertThat(jdbc.queryForObject("SELECT status FROM payments.refunds WHERE idempotency_key = ?", String.class,
                "refund:late:" + paid.paymentId)).isEqualTo("COMPLETED");
    }

    // -------------------------------------------------------------- cancelación tardía

    @Test
    void cancellingWithSeventyTwoHoursOrLessKeepsTheCommunityShareAndTheSeats() throws Exception {
        Paid paid = paidBooking(2);
        int availableBefore = availableSlots();
        // 72 horas exactas: el borde ya es tardío.
        clock.set(startOf(scheduleId).minus(Duration.ofHours(72)));
        String token = tokenFor(touristEmail);

        cancel(token, paid.bookingId, "No puedo asistir")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_NO_REFUND"))
                .andExpect(jsonPath("$.policyApplied").value("LATE_NO_REFUND"))
                .andExpect(jsonPath("$.touristRefundPct").value(0))
                .andExpect(jsonPath("$.refundAmountCop").value(0))
                .andExpect(jsonPath("$.refundStatus").doesNotExist())
                .andExpect(jsonPath("$.slotsReleased").value(false));

        assertThat(rowStatus("booking.bookings", paid.bookingId)).isEqualTo("CANCELLED_NO_REFUND");
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, paid.voucherId))
                .isEqualTo("REVOKED");
        // La dispersión sigue programada: se libera a las 24 h (ADR-0034 §4).
        assertThat(jdbc.queryForObject("""
                SELECT d.status FROM payments.disbursements d JOIN payments.payment_transactions p ON p.id = d.payment_id
                 WHERE p.booking_id = ?::uuid""", String.class, paid.bookingId)).isEqualTo("SCHEDULED");
        assertThat(availableSlots()).isEqualTo(availableBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments.refunds r JOIN payments.payment_transactions p"
                + " ON p.id = r.payment_id WHERE p.booking_id = ?::uuid", Long.class, paid.bookingId)).isZero();
        assertThat(jdbc.queryForObject("SELECT hours_before_start FROM booking.booking_cancellations"
                + " WHERE booking_id = ?::uuid", java.math.BigDecimal.class, paid.bookingId))
                .isEqualByComparingTo("72.00");
    }

    @Test
    void justOverSeventyTwoHoursIsStillAnEarlyRefund() throws Exception {
        Paid paid = paidBooking(1);
        clock.set(startOf(scheduleId).minus(Duration.ofHours(72)).minusSeconds(1));

        cancel(tokenFor(touristEmail), paid.bookingId, "Cambio de planes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyApplied").value("EARLY_REFUND"))
                .andExpect(jsonPath("$.refundAmountCop").value(68000));
    }

    // ------------------------------------------------------------------------ rechazos

    @Test
    void onlyTheOwnerOfAConfirmedBookingThatHasNotStartedMayCancelIt() throws Exception {
        Paid paid = paidBooking(1);
        String other = tokenFor(registerTourist());

        cancel(other, paid.bookingId, "No es mi reserva").andExpect(status().isNotFound());
        cancel(touristToken, UUID.randomUUID().toString(), "No existe").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/" + paid.bookingId + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"sin sesion\"}")).andExpect(status().isUnauthorized());
        cancel(touristToken, paid.bookingId, "ab").andExpect(status().isUnprocessableEntity());

        // Una reserva sin pagar no se cancela: expira sola.
        String pending = lockBooking(scheduleId, 1);
        cancel(touristToken, pending, "Sin pagar").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));

        cancel(touristToken, paid.bookingId, "Primera vez").andExpect(status().isOk());
        cancel(touristToken, paid.bookingId, "Segunda vez").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));

        Paid started = paidBooking(1);
        clock.set(startOf(scheduleId).plusSeconds(1));
        cancel(tokenFor(touristEmail), started.bookingId, "Tarde").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
        assertThat(rowStatus("booking.bookings", started.bookingId)).isEqualTo("CONFIRMED");
    }

    // ----------------------------------------------------------------------- concurrencia

    @Test
    void cancellingAndRedeemingAtTheSameTimeLeaveOneConsistentWinner() throws Exception {
        Paid paid = paidBooking(1);
        String gestorEmail = accreditedGestor();
        // Una hora antes de que empiece la franja: el día local ya es el válido y la cancelación es tardía.
        clock.set(startOf(scheduleId).minus(Duration.ofHours(1)));
        String tourist = tokenFor(touristEmail);
        String gestor = tokenFor(gestorEmail);
        String code = jdbc.queryForObject("SELECT short_code FROM voucher.vouchers WHERE id = ?", String.class,
                paid.voucherId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> cancelled = pool.submit(() -> {
                go.await();
                return cancel(tourist, paid.bookingId, "Cambio de planes").andReturn().getResponse().getStatus();
            });
            Future<Integer> redeemed = pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/v1/offline/contingency/redeem")
                                .header(HttpHeaders.AUTHORIZATION, bearer(gestor))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"shortCode\":\"" + code + "\"}"))
                        .andReturn().getResponse().getStatus();
            });
            go.countDown();
            int cancelStatus = cancelled.get(60, TimeUnit.SECONDS);
            int redeemStatus = redeemed.get(60, TimeUnit.SECONDS);

            // Exactamente una acción gana y el estado final es coherente con ella.
            assertThat(List.of(cancelStatus, redeemStatus).stream().filter(s -> s == 200).count()).isEqualTo(1);
            if (cancelStatus == 200) {
                assertThat(redeemStatus).isEqualTo(409);
                assertThat(rowStatus("booking.bookings", paid.bookingId)).isEqualTo("CANCELLED_NO_REFUND");
                assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class,
                        paid.voucherId)).isEqualTo("REVOKED");
            } else {
                assertThat(cancelStatus).isEqualTo(409);
                assertThat(rowStatus("booking.bookings", paid.bookingId)).isEqualTo("EXECUTED");
                assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class,
                        paid.voucherId)).isEqualTo("CONSUMED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- fixtures

    private record Paid(String bookingId, String paymentId, UUID voucherId) {
    }

    private ResultActions cancel(String token, String bookingId, String reason) throws Exception {
        return postJson("/api/v1/bookings/" + bookingId + "/cancel", token, "{\"reason\":\"" + reason + "\"}");
    }

    private String rowStatus(String table, String id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?::uuid", String.class, id);
    }

    private String refundStatus(String bookingId) {
        return jdbc.queryForObject("""
                SELECT r.status FROM payments.refunds r JOIN payments.payment_transactions p ON p.id = r.payment_id
                 WHERE p.booking_id = ?::uuid""", String.class, bookingId);
    }

    private int availableSlots() {
        return jdbc.queryForObject("SELECT available_slots FROM catalog.schedules WHERE id = ?::uuid", Integer.class,
                scheduleId);
    }

    private Instant startOf(String schedule) {
        return jdbc.queryForObject("SELECT start_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, schedule).toInstant();
    }

    /** Reserva pagada por el flujo real: deja la dispersión del 85 % en custodia y emite el voucher. */
    private Paid paidBooking(int slots) throws Exception {
        String bookingId = lockBooking(scheduleId, slots);
        String paymentId = JsonPath.read(postJson("/api/v1/bookings/" + bookingId + "/payment", touristToken,
                        "{\"paymentMethod\":\"PSE\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.paymentId");
        postJson("/api/v1/dev/payments/" + paymentId + "/simulate", touristToken, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isOk());
        return new Paid(bookingId, paymentId, issuer.issue(UUID.fromString(bookingId)).orElseThrow());
    }

    private String accreditedGestor() throws Exception {
        String email = uniqueEmail("gestor");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        roleAssignmentService.grant(userId, RoleCode.LINK_MANAGER);
        postJson("/api/v1/admin/link-managers", adminToken,
                "{\"userId\":\"%s\",\"associationId\":\"%s\",\"accreditedOn\":\"%s\"}"
                        .formatted(userId, associationId, LocalDate.ofInstant(START, BOGOTA)))
                .andExpect(status().isCreated());
        return email;
    }

    private String lockBooking(String forSchedule, int slots) throws Exception {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            participants.append(i == 0 ? "" : ",").append("""
                    {"fullName":"Participante %d","docType":"CC","docNumber":"%s%02d","medicalEps":"Sura","isHolder":%s}"""
                    .formatted(i, HOLDER_DOC, i, i == 0));
        }
        String body = """
                {"scheduleId":"%s","requestedSlots":%d,
                 "emergencyContact":{"fullName":"Carlos Gomez","phone":"+573001234567"},"participants":[%s]}"""
                .formatted(forSchedule, slots, participants);
        return JsonPath.read(mvc.perform(post("/api/v1/bookings/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.bookingId");
    }

    private String schedule() throws Exception {
        LocalDate day = LocalDate.ofInstant(START, BOGOTA).plusDays(30 + SEQ.incrementAndGet() % 300);
        String json = postJson("/api/v1/host/experiences/" + experienceId + "/schedules", hostToken,
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":12}]}"
                        .formatted(day, day))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private String approvedExperience() throws Exception {
        String id = JsonPath.read(postJson("/api/v1/host/experiences", hostToken, """
                {"title":"Sendero de la memoria","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Agua.",
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
                {"nit":"9%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina Pagos",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@pagos.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 600_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        String email = uniqueEmail("turista-pagos");
        registrationService.registerTourist(tourist(email, uniqueDocNumber(),
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA,
                        ConsentPurpose.HEALTH_EMERGENCY))), CLIENT);
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
