package com.integrate.modules.offline;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.voucher.service.impl.VoucherIssuer;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
 * Sincronización por lotes (ADR-0033): idempotencia por {@code syncBatchId}, tabla de decisión, protección del 85 %
 * comunitario y concurrencia entre lotes.
 */
@AutoConfigureMockMvc
class OfflineSyncApiIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String HOLDER_DOC = "30960000";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private VoucherIssuer issuer;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String hostToken;
    private String experienceId;
    private String touristToken;
    private String associationId;
    private String gestorEmail;
    private String device;
    private String scheduleId;
    private Instant scanTime;

    /** El reloj es compartido por todo el contexto de pruebas: no se deja adelantado para las demás clases. */
    @AfterEach
    void restoreClock() {
        clock.set(START);
    }

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
        touristToken = tokenFor(registerTourist());
        gestorEmail = accreditedGestor(associationId);
        device = "GESTOR-MOVIL-" + SEQ.incrementAndGet();
        scheduleId = schedule();
    }

    // ------------------------------------------------------------------- camino feliz

    @Test
    void aBatchOfValidScansConsumesTheVouchersAndExecutesTheBookings() throws Exception {
        Fixture a = confirmedVoucher(2);
        Fixture b = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        UUID batch = UUID.randomUUID();
        String body = batch(batch, item(a, scanTime), item(b, scanTime.plusSeconds(60)));

        sync(gestor, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncBatchId").value(batch.toString()))
                .andExpect(jsonPath("$.deviceId").value(device))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.summary.received").value(2))
                .andExpect(jsonPath("$.summary.confirmed").value(2))
                .andExpect(jsonPath("$.summary.conflicts").value(0))
                .andExpect(jsonPath("$.summary.rejected").value(0))
                .andExpect(jsonPath("$.items[0].localValidationId").value(a.localId.toString()))
                .andExpect(jsonPath("$.items[0].voucherId").value(a.jti.toString()))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].serverOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].bookingStatus").value("EXECUTED"))
                .andExpect(jsonPath("$.items[0].incidentType").doesNotExist())
                .andExpect(jsonPath("$.items[1].localValidationId").value(b.localId.toString()));

        for (Fixture f : List.of(a, b)) {
            assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, f.voucherId))
                    .isEqualTo("CONSUMED");
            assertThat(jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class,
                    f.bookingId)).isEqualTo("EXECUTED");
            assertThat(jdbc.queryForMap("""
                    SELECT channel, local_result, server_outcome FROM offline.offline_validations
                     WHERE local_validation_id = ?""", f.localId))
                    .containsEntry("channel", "OFFLINE_PWA").containsEntry("local_result", "ACCEPTED")
                    .containsEntry("server_outcome", "ACCEPTED");
        }
        assertThat(jdbc.queryForMap("SELECT status, items_received FROM offline.sync_batches WHERE id = ?", batch))
                .containsEntry("status", "PROCESSED").containsEntry("items_received", 2);
        assertThat(jdbc.queryForObject("SELECT payload_sha256 IS NOT NULL AND result_payload IS NOT NULL"
                + " FROM offline.sync_batches WHERE id = ?", Boolean.class, batch)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'OFFLINE_SYNC_PROCESSED' AND entity_id = ?""",
                Long.class, batch.toString())).isEqualTo(1);
    }

    @Test
    void theRosterExposesTheJtiTheBatchUses() throws Exception {
        Fixture a = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();

        mvc.perform(get("/api/v1/offline/roster").param("date", dayOf().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(gestor)).header("X-Device-Id", device))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vouchers[?(@.voucherId=='" + a.voucherId + "')].jti")
                        .value(org.hamcrest.Matchers.contains(a.jti.toString())));
    }

    // ------------------------------------------------------------------- idempotencia

    @Test
    void resendingTheSameBatchReplaysTheStoredResultAndADifferentPayloadIsRejected() throws Exception {
        Fixture a = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        UUID batch = UUID.randomUUID();
        String body = batch(batch, item(a, scanTime));

        String first = sync(gestor, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String received = JsonPath.read(first, "$.serverReceivedAt");
        clock.advance(Duration.ofMinutes(3));

        sync(gestor, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.serverReceivedAt").value(received))
                .andExpect(jsonPath("$.items[0].serverOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].bookingStatus").value("EXECUTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline.offline_validations WHERE voucher_id = ?",
                Long.class, a.voucherId)).isEqualTo(1);

        // Mismo syncBatchId con otro contenido.
        sync(gestor, batch(batch, item(a, scanTime.plusSeconds(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_BATCH_PAYLOAD_MISMATCH"));
    }

    @Test
    void aLocalValidationResentInAnotherBatchReturnsItsStoredResult() throws Exception {
        Fixture a = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        sync(gestor, batch(UUID.randomUUID(), item(a, scanTime))).andExpect(status().isOk());

        sync(gestor, batch(UUID.randomUUID(), item(a, scanTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].serverOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.summary.confirmed").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline.offline_validations WHERE local_validation_id = ?",
                Long.class, a.localId)).isEqualTo(1);
    }

    // ------------------------------------------------------------- protección del 85 %

    @Test
    void aRevokedVoucherUsedInTheFieldProtectsTheCommunityShare() throws Exception {
        Fixture a = paidVoucher();
        // Cancelación > 72 h aceptada en el servidor antes de que el gestor sincronice (T-2.6 la automatizará).
        jdbc.update("UPDATE voucher.vouchers SET status = 'REVOKED', revoked_at = now(), revocation_reason = 'Cancelada'"
                + " WHERE id = ?", a.voucherId);
        jdbc.update("UPDATE booking.bookings SET status = 'CANCELLED_REFUNDED' WHERE id = ?::uuid", a.bookingId);
        jdbc.update("""
                UPDATE payments.disbursements SET status = 'CANCELLED', cancellation_reason = 'Reembolso anticipado'
                 WHERE payment_id IN (SELECT id FROM payments.payment_transactions WHERE booking_id = ?::uuid)""",
                a.bookingId);
        String gestor = goToTheDayAndRegisterTheDevice();

        String body = sync(gestor, batch(UUID.randomUUID(), item(a, scanTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.conflicts").value(1))
                .andExpect(jsonPath("$.items[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.items[0].serverOutcome").value("REJECTED_REVOKED"))
                .andExpect(jsonPath("$.items[0].bookingStatus").value("SYNC_CONFLICT"))
                .andExpect(jsonPath("$.items[0].incidentType").value("OVER_REDEMPTION_COMMUNITY_PROTECTED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class,
                a.bookingId)).isEqualTo("SYNC_CONFLICT");
        assertThat(jdbc.queryForObject("""
                SELECT d.status FROM payments.disbursements d JOIN payments.payment_transactions p ON p.id = d.payment_id
                 WHERE p.booking_id = ?::uuid""", String.class, a.bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForMap("""
                SELECT i.incident_type, i.severity, i.status FROM offline.sync_incidents i
                  JOIN offline.offline_validations v ON v.id = i.validation_id WHERE v.voucher_id = ?""", a.voucherId))
                .containsEntry("incident_type", "OVER_REDEMPTION_COMMUNITY_PROTECTED")
                .containsEntry("severity", "HIGH").containsEntry("status", "OPEN");
        assertThat(JsonPath.<String>read(body, "$.items[0].incidentId")).isNotBlank();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'SYNC_INCIDENT_OPENED' AND severity = 'HIGH'""",
                Long.class)).isPositive();
        // El voucher no se consume ni la reserva se ejecuta.
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, a.voucherId))
                .isEqualTo("REVOKED");
    }

    // ------------------------------------------------------------------ duplicados

    @Test
    void theEarliestScanWinsInsideTheBatchAndAcrossBatches() throws Exception {
        Fixture a = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        UUID lateId = UUID.randomUUID();
        UUID earlyId = UUID.randomUUID();
        // El escaneo tardío se lista primero; gana el más temprano.
        String body = batch(UUID.randomUUID(), item(a, lateId, scanTime.plusSeconds(600)),
                item(a, earlyId, scanTime));

        sync(gestor, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.confirmed").value(1))
                .andExpect(jsonPath("$.summary.conflicts").value(1))
                .andExpect(jsonPath("$.items[0].localValidationId").value(lateId.toString()))
                .andExpect(jsonPath("$.items[0].serverOutcome").value("REJECTED_DUPLICATE"))
                .andExpect(jsonPath("$.items[0].incidentType").value("DUPLICATE_OFFLINE_REDEMPTION"))
                .andExpect(jsonPath("$.items[1].serverOutcome").value("ACCEPTED"));

        // Otro dispositivo llega después con un escaneo aún más temprano: el ACCEPTED no se revierte.
        sync(gestor, batch(UUID.randomUUID(), item(a, scanTime.minusSeconds(3600))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].serverOutcome").value("REJECTED_DUPLICATE"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM offline.offline_validations WHERE voucher_id = ? AND server_outcome = 'ACCEPTED'""",
                Long.class, a.voucherId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class,
                a.bookingId)).isEqualTo("EXECUTED");
    }

    @Test
    void aVoucherAlreadyRedeemedByContingencyIsADuplicateForTheBatch() throws Exception {
        Fixture a = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        mvc.perform(post("/api/v1/offline/contingency/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearer(gestor)).header("X-Device-Id", device)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shortCode\":\"" + a.shortCode + "\"}"))
                .andExpect(status().isOk());

        sync(gestor, batch(UUID.randomUUID(), item(a, scanTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].serverOutcome").value("REJECTED_DUPLICATE"));
    }

    // ------------------------------------------------------------------- rechazos

    @Test
    void rejectionsFollowTheContractTable() throws Exception {
        Fixture a = confirmedVoucher(1);
        Fixture otherDay = confirmedVoucher(1, schedule());
        Fixture foreign = confirmedVoucherOfAnotherAssociation();
        String gestor = goToTheDayAndRegisterTheDevice();
        UUID unknownJti = UUID.randomUUID();
        UUID skewId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        UUID badCodeId = UUID.randomUUID();
        UUID otherDayId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();

        String body = """
                {"syncBatchId":"%s","deviceId":"%s","deviceSentAt":"%s","validations":[%s,%s,%s,%s,%s]}"""
                .formatted(UUID.randomUUID(), device, clock.instant(),
                        raw(skewId, a.jti, "ABCDEFGH", a.signature(), clock.instant().plus(Duration.ofHours(1))),
                        raw(unknownId, unknownJti, "ABCDEFGH", a.signature(), scanTime),
                        raw(badCodeId, a.jti, "ZZZZZZZZ", a.signature(), scanTime),
                        raw(otherDayId, otherDay.jti, otherDay.shortCode, otherDay.signature(), scanTime),
                        raw(foreignId, foreign.jti, foreign.shortCode, foreign.signature(), scanTime));

        sync(gestor, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.received").value(5))
                .andExpect(jsonPath("$.items[0].serverOutcome").value("REJECTED_CLOCK_SKEW"))
                .andExpect(jsonPath("$.items[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.items[0].incidentType").value("CLOCK_SKEW_FUTURE_SCAN"))
                .andExpect(jsonPath("$.items[1].serverOutcome").value("REJECTED_UNKNOWN_VOUCHER"))
                .andExpect(jsonPath("$.items[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[2].serverOutcome").value("REJECTED_UNKNOWN_VOUCHER"))
                .andExpect(jsonPath("$.items[3].serverOutcome").value("REJECTED_OUT_OF_DATE"))
                .andExpect(jsonPath("$.items[4].serverOutcome").value("REJECTED_UNKNOWN_VOUCHER"))
                .andExpect(jsonPath("$.summary.rejected").value(4))
                .andExpect(jsonPath("$.summary.conflicts").value(1));

        // Nada se consumió; el voucher inexistente o ajeno no deja fila (FK) y el resto sí.
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, a.voucherId))
                .isEqualTo("ISSUED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline.offline_validations WHERE voucher_id = ?",
                Long.class, foreign.voucherId)).isZero();
        assertThat(jdbc.queryForObject("SELECT server_outcome FROM offline.offline_validations WHERE local_validation_id = ?",
                String.class, otherDayId)).isEqualTo("REJECTED_OUT_OF_DATE");
        assertThat(jdbc.queryForObject("SELECT status FROM offline.sync_batches ORDER BY created_at DESC LIMIT 1",
                String.class)).isEqualTo("PARTIAL");
    }

    @Test
    void invalidRequestsAreRejectedBeforeTouchingTheDatabase() throws Exception {
        Fixture a = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        UUID batch = UUID.randomUUID();

        // Sin ítems.
        sync(gestor, """
                {"syncBatchId":"%s","deviceId":"%s","deviceSentAt":"%s","validations":[]}"""
                .formatted(batch, device, clock.instant())).andExpect(status().isUnprocessableEntity());
        // localValidationId repetido en el lote.
        UUID same = UUID.randomUUID();
        sync(gestor, batch(batch, item(a, same, scanTime), item(a, same, scanTime.plusSeconds(5))))
                .andExpect(status().isUnprocessableEntity());
        // OFFLINE_PWA sin signatureHash.
        sync(gestor, """
                {"syncBatchId":"%s","deviceId":"%s","deviceSentAt":"%s","validations":[{"localValidationId":"%s",
                 "voucherId":"%s","scannedAt":"%s","shortCode":"%s","channel":"OFFLINE_PWA"}]}"""
                .formatted(batch, device, clock.instant(), UUID.randomUUID(), a.jti, scanTime, a.shortCode))
                .andExpect(status().isUnprocessableEntity());
        // Más de 100 ítems.
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(raw(UUID.randomUUID(), a.jti, a.shortCode, a.signature(), scanTime));
        }
        sync(gestor, """
                {"syncBatchId":"%s","deviceId":"%s","deviceSentAt":"%s","validations":[%s]}"""
                .formatted(batch, device, clock.instant(), String.join(",", many)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline.sync_batches WHERE id = ?", Long.class, batch))
                .isZero();
    }

    @Test
    void onlyAnActiveDeviceOfTheCallerMaySync() throws Exception {
        Fixture a = confirmedVoucher(1);
        String secondManager = accreditedGestor(associationId);
        String gestor = goToTheDayAndRegisterTheDevice();
        String body = batch(UUID.randomUUID(), item(a, scanTime));

        // Dispositivo de otro gestor y dispositivo nunca registrado.
        sync(tokenFor(secondManager), body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OFFLINE_DEVICE_NOT_AUTHORIZED"));
        sync(gestor, body.replace(device, "GESTOR-FANTASMA-1")).andExpect(status().isForbidden());
        // Turista sin rol.
        sync(tokenFor(registerTourist()), body).andExpect(status().isForbidden());
        // Dispositivo perdido.
        jdbc.update("UPDATE offline.devices SET status = 'LOST', revoked_at = now() WHERE device_identifier = ?", device);
        sync(gestor, body).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, a.voucherId))
                .isEqualTo("ISSUED");
    }

    // ----------------------------------------------------------------- concurrencia

    @Test
    void twoBatchesRacingForTheSameVouchersAcceptEachVoucherOnlyOnce() throws Exception {
        Fixture a = confirmedVoucher(1);
        Fixture b = confirmedVoucher(1);
        String gestor = goToTheDayAndRegisterTheDevice();
        String first = batch(UUID.randomUUID(), item(a, scanTime), item(b, scanTime.plusSeconds(1)));
        String second = batch(UUID.randomUUID(), item(b, scanTime.plusSeconds(2)), item(a, scanTime.plusSeconds(3)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (String body : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    go.await();
                    return sync(gestor, body).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        for (Fixture f : List.of(a, b)) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM offline.offline_validations WHERE voucher_id = ? AND server_outcome = 'ACCEPTED'""",
                    Long.class, f.voucherId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class,
                    f.bookingId)).isEqualTo("EXECUTED");
        }
    }

    // ---------------------------------------------------------------- fixtures

    private record Fixture(UUID voucherId, UUID jti, String bookingId, String shortCode, UUID localId, String scheduleId) {
        String signature() {
            return "a".repeat(64);
        }
    }

    private String item(Fixture f, Instant scannedAt) {
        return item(f, f.localId, scannedAt);
    }

    private String item(Fixture f, UUID localId, Instant scannedAt) {
        return raw(localId, f.jti, f.shortCode, f.signature(), scannedAt);
    }

    private static String raw(UUID localId, UUID jti, String shortCode, String signature, Instant scannedAt) {
        return """
                {"localValidationId":"%s","voucherId":"%s","scannedAt":"%s","signatureHash":"%s","shortCode":"%s",
                 "channel":"OFFLINE_PWA"}""".formatted(localId, jti, scannedAt, signature, shortCode);
    }

    private String batch(UUID batchId, String... items) {
        return """
                {"syncBatchId":"%s","deviceId":"%s","deviceSentAt":"%s","validations":[%s]}"""
                .formatted(batchId, device, clock.instant(), String.join(",", items));
    }

    private ResultActions sync(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/offline/sync").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private LocalDate dayOf() {
        return jdbc.queryForObject("SELECT start_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, scheduleId).toInstant().atZone(BOGOTA).toLocalDate();
    }

    /** Lleva el reloj al día de la franja, emite el token y registra el dispositivo bajando el padrón. */
    private String goToTheDayAndRegisterTheDevice() throws Exception {
        clock.set(dayOf().atTime(LocalTime.of(10, 0)).atZone(BOGOTA).toInstant());
        scanTime = clock.instant().minusSeconds(3600);
        String gestor = tokenFor(gestorEmail);
        mvc.perform(get("/api/v1/offline/roster").param("date", dayOf().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(gestor)).header("X-Device-Id", device))
                .andExpect(status().isOk());
        return gestor;
    }

    private Fixture confirmedVoucher(int slots) throws Exception {
        return confirmedVoucher(slots, scheduleId);
    }

    private Fixture confirmedVoucher(int slots, String forSchedule) throws Exception {
        String bookingId = lockBooking(forSchedule, slots);
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);
        return fixture(bookingId, forSchedule);
    }

    /** Reserva pagada por el flujo real: deja la dispersión del 85 % en custodia. */
    private Fixture paidVoucher() throws Exception {
        String bookingId = lockBooking(scheduleId, 1);
        String paymentId = JsonPath.read(postJson("/api/v1/bookings/" + bookingId + "/payment", touristToken,
                        "{\"paymentMethod\":\"PSE\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.paymentId");
        postJson("/api/v1/dev/payments/" + paymentId + "/simulate", touristToken, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isOk());
        return fixture(bookingId, scheduleId);
    }

    private Fixture fixture(String bookingId, String forSchedule) {
        UUID voucherId = issuer.issue(UUID.fromString(bookingId)).orElseThrow();
        return new Fixture(voucherId,
                jdbc.queryForObject("SELECT jti FROM voucher.vouchers WHERE id = ?", UUID.class, voucherId), bookingId,
                jdbc.queryForObject("SELECT short_code FROM voucher.vouchers WHERE id = ?", String.class, voucherId),
                UUID.randomUUID(), forSchedule);
    }

    /** Voucher de una experiencia de otra asociación: para el gestor es como si no existiera. */
    private Fixture confirmedVoucherOfAnotherAssociation() throws Exception {
        String otherAssociation = createAssociation();
        String otherHost = uniqueEmail("anfitrion-ajeno");
        UUID otherHostId = registrationService.registerTourist(tourist(otherHost), CLIENT).userId();
        postJson("/api/v1/admin/host-profiles", adminToken,
                "{\"userId\":\"" + otherHostId + "\",\"associationId\":\"" + otherAssociation + "\"}")
                .andExpect(status().isCreated());
        String otherHostToken = tokenFor(otherHost);
        String otherExperience = approvedExperience(otherHostToken);
        LocalDate day = dayOf();
        String otherSchedule = JsonPath.read(postJson("/api/v1/host/experiences/" + otherExperience + "/schedules",
                        otherHostToken,
                        "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":6}]}"
                                .formatted(day, day))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$[0].id");
        return confirmedVoucher(1, otherSchedule);
    }

    private String accreditedGestor(String association) throws Exception {
        String email = uniqueEmail("gestor");
        UUID userId = registrationService.registerTourist(tourist(email), CLIENT).userId();
        roleAssignmentService.grant(userId, RoleCode.LINK_MANAGER);
        postJson("/api/v1/admin/link-managers", adminToken,
                "{\"userId\":\"%s\",\"associationId\":\"%s\",\"accreditedOn\":\"%s\"}"
                        .formatted(userId, association, LocalDate.ofInstant(START, BOGOTA)))
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
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":6}]}"
                        .formatted(day, day))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private String approvedExperience() throws Exception {
        return approvedExperience(hostToken);
    }

    private String approvedExperience(String token) throws Exception {
        String id = JsonPath.read(postJson("/api/v1/host/experiences", token, """
                {"title":"Sendero de la memoria","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Agua.",
                 "physicalIntensity":"MEDIUM","durationMinutes":240,"municipalityCode":"05001","vereda":"La Esperanza",
                 "meetingPoint":"Parque principal","latitude":6.25,"longitude":-75.56,"maxCapacityPerSlot":12,
                 "unitPriceCop":80000,"categoryCodes":["SENDERISMO_MEMORIA"]}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/v1/host/experiences/" + id + "/submit", token, null).andExpect(status().isOk());
        postJson("/api/v1/admin/experiences/" + id + "/approve", adminToken, null).andExpect(status().isOk());
        return id;
    }

    private String createAssociation() throws Exception {
        long seq = SEQ.incrementAndGet();
        return JsonPath.read(postJson("/api/v1/admin/associations", adminToken, """
                {"nit":"8%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina Sync",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@sync.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 700_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        String email = uniqueEmail("turista-sync");
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
