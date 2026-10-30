package com.integrate.modules.payment;

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
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.payment.gateway.MockPayoutGateway;
import com.integrate.modules.payment.gateway.PayoutGateway.Result;
import com.integrate.modules.payment.service.impl.PayoutProcessor;
import com.integrate.modules.payment.service.impl.ReleaseProcessor;
import com.integrate.modules.voucher.service.impl.VoucherIssuer;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * Liberación de la dispersión comunitaria, {@code NO_SHOW}, pago por lotes a la asociación y comprobante contable
 * (ADR-0034 §4, §5 y §6).
 */
@AutoConfigureMockMvc
class DisbursementPayoutIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String HOLDER_DOC = "30980000";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private VoucherIssuer issuer;

    @Autowired
    private ReleaseProcessor releaseProcessor;

    @Autowired
    private PayoutProcessor payoutProcessor;

    @Autowired
    private MockPayoutGateway payoutGateway;

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
        payoutGateway.next(null);
    }

    // --------------------------------------------------------------------- liberación

    @Test
    void redeemingAVoucherReleasesTheCommunityShareInTheSameTransaction() throws Exception {
        Paid paid = paidBooking(1);
        String gestorEmail = accreditedGestor();
        clock.set(startOf().minus(Duration.ofHours(1)));
        String gestor = tokenFor(gestorEmail);
        String code = jdbc.queryForObject("SELECT short_code FROM voucher.vouchers WHERE id = ?", String.class,
                paid.voucherId);

        mvc.perform(post("/api/v1/offline/contingency/redeem").header(HttpHeaders.AUTHORIZATION, "Bearer " + gestor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shortCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForMap("""
                SELECT d.status, d.released_at FROM payments.disbursements d
                  JOIN payments.payment_transactions p ON p.id = d.payment_id WHERE p.booking_id = ?::uuid""",
                paid.bookingId)).containsEntry("status", "RELEASED")
                .satisfies(row -> assertThat(row.get("released_at")).isNotNull());
    }

    @Test
    void anUnattendedBookingBecomesNoShowAndItsShareIsReleasedAfterTheGrace() throws Exception {
        Paid paid = paidBooking(1);

        clock.set(endOf().plus(Duration.ofHours(23)));
        assertThat(releaseProcessor.sweepOnce()).isZero();
        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("SCHEDULED");
        assertThat(bookingStatus(paid.bookingId)).isEqualTo("CONFIRMED");

        clock.set(endOf().plus(Duration.ofHours(24)).plusSeconds(60));
        assertThat(releaseProcessor.sweepOnce()).isGreaterThanOrEqualTo(1);

        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("RELEASED");
        assertThat(bookingStatus(paid.bookingId)).isEqualTo("NO_SHOW");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'DISBURSEMENT_RELEASED'
                   AND details ->> 'bookingId' = ?""", Long.class, paid.bookingId)).isEqualTo(1);
        // Un segundo barrido no vuelve a tocar nada de esta reserva.
        releaseProcessor.sweepOnce();
        assertThat(bookingStatus(paid.bookingId)).isEqualTo("NO_SHOW");
    }

    @Test
    void aLateCancelledBookingStillGivesTheCommunityItsShareAtTheGrace() throws Exception {
        Paid paid = paidBooking(1);
        clock.set(startOf().minus(Duration.ofHours(1)));
        postJson("/api/v1/bookings/" + paid.bookingId + "/cancel", tokenFor(touristEmail),
                "{\"reason\":\"No puedo asistir\"}").andExpect(status().isOk());
        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("SCHEDULED");

        clock.set(endOf().plus(Duration.ofHours(25)));
        releaseProcessor.sweepOnce();

        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("RELEASED");
        assertThat(bookingStatus(paid.bookingId)).isEqualTo("CANCELLED_NO_REFUND");
    }

    @Test
    void aBookingInSyncConflictIsNotReleasedBySweep() throws Exception {
        Paid paid = paidBooking(1);
        clock.set(startOf().minus(Duration.ofHours(1)));
        postJson("/api/v1/bookings/" + paid.bookingId + "/cancel", tokenFor(touristEmail),
                "{\"reason\":\"No puedo asistir\"}").andExpect(status().isOk());
        jdbc.update("UPDATE booking.bookings SET status = 'SYNC_CONFLICT' WHERE id = ?::uuid", paid.bookingId);

        clock.set(endOf().plus(Duration.ofHours(25)));
        releaseProcessor.sweepOnce();

        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("SCHEDULED");
    }

    // ---------------------------------------------------------------------------- pago

    @Test
    void payoutPaysAllTheReleasedSharesInOneBatchAndIssuesTheSettlementReceipt() throws Exception {
        Paid a = paidBooking(1);
        Paid b = paidBooking(2);
        Paid c = paidBooking(3);
        jdbc.update("UPDATE payments.payment_transactions SET gateway_fee_cop = 2400 WHERE id = ?::uuid", a.paymentId);
        jdbc.update("UPDATE payments.payment_transactions SET gateway_fee_cop = 4800 WHERE id = ?::uuid", b.paymentId);
        jdbc.update("UPDATE payments.payment_transactions SET gateway_fee_cop = 7200 WHERE id = ?::uuid", c.paymentId);
        UUID account = payoutAccount();
        long mailsBefore = jdbc.queryForObject("SELECT count(*) FROM notification.notification_outbox", Long.class);
        clock.set(endOf().plus(Duration.ofHours(25)));
        releaseProcessor.sweepOnce();

        payoutProcessor.sweepOnce();

        UUID batch = jdbc.queryForObject("""
                SELECT d.payout_batch_id FROM payments.disbursements d
                  JOIN payments.payment_transactions p ON p.id = d.payment_id WHERE p.booking_id = ?::uuid""",
                UUID.class, a.bookingId);
        assertThat(batch).isNotNull();
        for (Paid p : List.of(a, b, c)) {
            assertThat(disbursementStatus(p.bookingId)).isEqualTo("PAID");
            assertThat(jdbc.queryForObject("""
                    SELECT d.payout_batch_id FROM payments.disbursements d
                      JOIN payments.payment_transactions pt ON pt.id = d.payment_id WHERE pt.booking_id = ?::uuid""",
                    UUID.class, p.bookingId)).isEqualTo(batch);
        }
        assertThat(jdbc.queryForMap("""
                SELECT status, payout_account_id, gateway_payout_id IS NOT NULL AS has_gateway_id,
                       paid_at IS NOT NULL AS has_paid_at FROM payments.payout_batches WHERE id = ?""", batch))
                .containsEntry("status", "PAID").containsEntry("payout_account_id", account)
                .containsEntry("has_gateway_id", true).containsEntry("has_paid_at", true);

        // Desglose exacto: 80000 + 160000 + 240000 con CEIL(85 %) = 68000 + 136000 + 204000.
        Map<String, Object> receipt = jdbc.queryForMap("""
                SELECT gross_amount_cop, gateway_fee_cop, platform_fee_cop, net_community_cop, pdf_s3_key
                  FROM payments.settlement_receipts WHERE payout_batch_id = ?""", batch);
        assertThat((BigDecimal) receipt.get("gross_amount_cop")).isEqualByComparingTo("480000");
        assertThat((BigDecimal) receipt.get("net_community_cop")).isEqualByComparingTo("408000");
        assertThat((BigDecimal) receipt.get("platform_fee_cop")).isEqualByComparingTo("72000");
        assertThat((BigDecimal) receipt.get("gateway_fee_cop")).isEqualByComparingTo("14400");
        byte[] pdf = voucherStorage.get((String) receipt.get("pdf_s3_key")).orElseThrow();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification.notification_outbox", Long.class))
                .isGreaterThan(mailsBefore);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'PAYOUT_COMPLETED' AND entity_id = ?""",
                Long.class, batch.toString())).isEqualTo(1);

        // Nada nuevo que pagar: el segundo barrido no crea otro lote para esta asociación.
        payoutProcessor.sweepOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments.payout_batches WHERE association_id = ?::uuid",
                Long.class, associationId)).isEqualTo(1);
    }

    @Test
    void withoutAVerifiedDefaultAccountTheShareStaysReleasedUntilOneExists() throws Exception {
        Paid paid = paidBooking(1);
        clock.set(endOf().plus(Duration.ofHours(25)));
        releaseProcessor.sweepOnce();

        payoutProcessor.sweepOnce();

        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments.payout_batches WHERE association_id = ?::uuid",
                Long.class, associationId)).isZero();

        payoutAccount();
        payoutProcessor.sweepOnce();
        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("PAID");
    }

    @Test
    void aRejectedPayoutFailsTheBatchAndTheNextSweepRetriesInANewBatch() throws Exception {
        Paid paid = paidBooking(1);
        payoutAccount();
        clock.set(endOf().plus(Duration.ofHours(25)));
        releaseProcessor.sweepOnce();

        payoutGateway.next(Result.rejected("Cuenta destino inactiva"));
        payoutProcessor.sweepOnce();

        assertThat(jdbc.queryForMap("""
                SELECT status, failure_reason FROM payments.payout_batches WHERE association_id = ?::uuid""",
                associationId)).containsEntry("status", "FAILED").containsEntry("failure_reason", "Cuenta destino inactiva");
        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("""
                SELECT d.payout_batch_id FROM payments.disbursements d
                  JOIN payments.payment_transactions p ON p.id = d.payment_id WHERE p.booking_id = ?::uuid""",
                UUID.class, paid.bookingId)).isNull();

        payoutProcessor.sweepOnce();

        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments.payout_batches WHERE association_id = ?::uuid",
                Long.class, associationId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM payments.settlement_receipts r JOIN payments.payout_batches b
                    ON b.id = r.payout_batch_id WHERE b.association_id = ?::uuid""", Long.class, associationId))
                .isEqualTo(1);
    }

    @Test
    void aTransientPayoutErrorLeavesTheBatchSubmittedAndTheNextSweepFinishesIt() throws Exception {
        Paid paid = paidBooking(1);
        payoutAccount();
        clock.set(endOf().plus(Duration.ofHours(25)));
        releaseProcessor.sweepOnce();

        payoutGateway.next(Result.retry());
        payoutProcessor.sweepOnce();

        UUID batch = jdbc.queryForObject("SELECT id FROM payments.payout_batches WHERE association_id = ?::uuid",
                UUID.class, associationId);
        assertThat(jdbc.queryForObject("SELECT status FROM payments.payout_batches WHERE id = ?", String.class, batch))
                .isEqualTo("SUBMITTED");
        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("RELEASED");

        payoutProcessor.sweepOnce();

        assertThat(jdbc.queryForObject("SELECT status FROM payments.payout_batches WHERE id = ?", String.class, batch))
                .isEqualTo("PAID");
        assertThat(disbursementStatus(paid.bookingId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments.payout_batches WHERE association_id = ?::uuid",
                Long.class, associationId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- fixtures

    private record Paid(String bookingId, String paymentId, UUID voucherId) {
    }

    private String disbursementStatus(String bookingId) {
        return jdbc.queryForObject("""
                SELECT d.status FROM payments.disbursements d
                  JOIN payments.payment_transactions p ON p.id = d.payment_id WHERE p.booking_id = ?::uuid""",
                String.class, bookingId);
    }

    private String bookingStatus(String bookingId) {
        return jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class, bookingId);
    }

    private Instant startOf() {
        return jdbc.queryForObject("SELECT start_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, scheduleId).toInstant();
    }

    private Instant endOf() {
        return jdbc.queryForObject("SELECT end_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, scheduleId).toInstant();
    }

    /** Cuenta Nequi por defecto y verificada de la asociación (datos cifrados ficticios: la base solo exige el tamaño). */
    private UUID payoutAccount() {
        return jdbc.queryForObject("""
                INSERT INTO payments.payout_accounts (association_id, account_type, account_number_enc, account_last4,
                    holder_name_enc, holder_doc_type, holder_doc_number_enc, sensitive_key_version, is_default,
                    verified_at)
                VALUES (?::uuid, 'NEQUI', ?, '1234', ?, 'NIT', ?, 1, TRUE, now()) RETURNING id""", UUID.class,
                associationId, new byte[40], new byte[40], new byte[40]);
    }

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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + touristToken)
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
                {"nit":"5%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina Dispersion",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@dispersion.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 500_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        String email = uniqueEmail("turista-dispersion");
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
        MockHttpServletRequestBuilder request = post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }
}
