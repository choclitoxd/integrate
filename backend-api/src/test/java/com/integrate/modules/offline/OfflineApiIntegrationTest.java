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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Padrón offline, lista delta de revocación y canje manual de contingencia (ADR-0032): alcance por asociación,
 * dispositivo, tabla de rechazos y doble canje concurrente.
 */
@AutoConfigureMockMvc
class OfflineApiIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String HOLDER_DOC = "30950000";

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
    private String touristEmail;
    private String associationId;
    private String gestorEmail;
    private String device;

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
    }

    // -------------------------------------------------------------------- padrón

    @Test
    void rosterListsTheIssuedVouchersOfTheDayForTheManagersAssociationOnly() throws Exception {
        String scheduleId = schedule();
        String bookingId = confirmedBooking(scheduleId, 2);
        UUID voucherId = issuedVoucher(bookingId);
        LocalDate day = dayOf(scheduleId);
        String otherDay = confirmedBooking(schedule(), 1);
        issuedVoucher(otherDay);
        String otherManager = accreditedGestor(createAssociation());
        goTo(scheduleId);

        String gestor = tokenFor(gestorEmail);
        mvc.perform(roster(gestor, device, day.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.rosterDate").value(day.toString()))
                .andExpect(jsonPath("$.associationId").value(associationId))
                .andExpect(jsonPath("$.signingKeys[0].kid").value(VOUCHER_KID))
                .andExpect(jsonPath("$.signingKeys[0].publicKeyPem").value(org.hamcrest.Matchers.startsWith("-----BEGIN")))
                .andExpect(jsonPath("$.vouchers.length()").value(1))
                .andExpect(jsonPath("$.vouchers[0].voucherId").value(voucherId.toString()))
                .andExpect(jsonPath("$.vouchers[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$.vouchers[0].scheduleId").value(scheduleId))
                .andExpect(jsonPath("$.vouchers[0].slotsCount").value(2))
                .andExpect(jsonPath("$.vouchers[0].holderDocument").value("CC:" + HOLDER_DOC + "00"))
                .andExpect(jsonPath("$.vouchers[0].participants.length()").value(2))
                .andExpect(jsonPath("$.vouchers[0].emergencyContact.name").value("Carlos Gomez"))
                .andExpect(jsonPath("$.vouchers[0].shortCode")
                        .value(org.hamcrest.Matchers.matchesPattern("^[A-HJ-NP-Z2-9]{8}$")))
                .andExpect(jsonPath("$.vouchers[0].tokenSha256").value(org.hamcrest.Matchers.hasLength(64)))
                .andExpect(jsonPath("$.vouchers[0].participants[0].epsEnc").doesNotExist())
                .andExpect(jsonPath("$.vouchers[0].participants[0].medicalEps").doesNotExist())
                .andExpect(jsonPath("$.revocations.items.length()").value(0))
                .andReturn();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM offline.roster_downloads r JOIN offline.devices d ON d.id = r.device_id
                 WHERE d.device_identifier = ? AND r.vouchers_included = 1""", Long.class, device)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_trail WHERE action = 'OFFLINE_ROSTER_DOWNLOADED'", Long.class))
                .isPositive();

        // Otro gestor, de otra asociación, no ve nada.
        String otherToken = tokenFor(otherManager);
        mvc.perform(roster(otherToken, "GESTOR-MOVIL-" + SEQ.incrementAndGet(), day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vouchers.length()").value(0));
    }

    @Test
    void rosterExcludesRevokedAndConsumedVouchersAndListsTheRevokedOnes() throws Exception {
        String scheduleId = schedule();
        UUID kept = issuedVoucher(confirmedBooking(scheduleId, 1));
        UUID revoked = issuedVoucher(confirmedBooking(scheduleId, 1));
        UUID consumed = issuedVoucher(confirmedBooking(scheduleId, 1));
        jdbc.update("UPDATE voucher.vouchers SET status = 'REVOKED', revoked_at = now(), revocation_reason = 'Cancelada'"
                + " WHERE id = ?", revoked);
        jdbc.update("UPDATE voucher.vouchers SET status = 'CONSUMED', consumed_at = now() WHERE id = ?", consumed);
        UUID late = issuedVoucher(confirmedBooking(scheduleId, 1));
        goTo(scheduleId);

        String gestor = tokenFor(gestorEmail);
        String body = mvc.perform(roster(gestor, device, dayOf(scheduleId).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vouchers.length()").value(2))
                .andExpect(jsonPath("$.vouchers[?(@.voucherId=='" + kept + "')]").isNotEmpty())
                .andExpect(jsonPath("$.revocations.items.length()").value(1))
                .andExpect(jsonPath("$.revocations.items[0].voucherId").value(revoked.toString()))
                .andExpect(jsonPath("$.revocations.items[0].revocationReason").value("Cancelada"))
                .andReturn().getResponse().getContentAsString();
        String watermark = JsonPath.read(body, "$.revocations.watermark");

        // Delta: desde el watermark se repite la última (solapamiento >=); una revocación nueva se suma.
        jdbc.update("UPDATE voucher.vouchers SET status = 'REVOKED', revoked_at = now() + interval '1 minute',"
                + " revocation_reason = 'Tardia' WHERE id = ?", late);
        mvc.perform(get("/api/v1/offline/revocations").param("since", watermark)
                        .header(HttpHeaders.AUTHORIZATION, bearer(gestor)).header("X-Device-Id", device))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.watermark").isString());
        mvc.perform(get("/api/v1/offline/revocations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(gestor)).header("X-Device-Id", device))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void theRosterRequiresALinkManagerAndAHealthyDevice() throws Exception {
        String scheduleId = schedule();
        String secondManager = accreditedGestor(associationId);
        goTo(scheduleId);
        String day = dayOf(scheduleId).toString();
        String gestor = tokenFor(gestorEmail);

        mvc.perform(get("/api/v1/offline/roster").param("date", day)
                        .header(HttpHeaders.AUTHORIZATION, bearer(gestor)))
                .andExpect(status().isBadRequest());
        mvc.perform(roster(tokenFor(touristEmail), device, day)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/offline/roster").param("date", day)).andExpect(status().isUnauthorized());

        mvc.perform(roster(gestor, device, day)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline.devices WHERE device_identifier = ?",
                Long.class, device)).isEqualTo(1);

        // El mismo dispositivo en manos de otro gestor se rechaza.
        String sameAssociationManager = tokenFor(secondManager);
        mvc.perform(roster(sameAssociationManager, device, day))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFLINE_DEVICE_OWNED_BY_OTHER"));

        // Dispositivo perdido: ya no descarga.
        jdbc.update("UPDATE offline.devices SET status = 'LOST', revoked_at = now() WHERE device_identifier = ?", device);
        mvc.perform(roster(gestor, device, day))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OFFLINE_DEVICE_NOT_AUTHORIZED"));
    }

    // ---------------------------------------------------------------- contingencia

    @Test
    void redeemingAValidCodeConsumesTheVoucherAndExecutesTheBooking() throws Exception {
        String scheduleId = schedule();
        String bookingId = confirmedBooking(scheduleId, 2);
        UUID voucherId = issuedVoucher(bookingId);
        String code = shortCode(voucherId);
        goTo(scheduleId);
        String gestor = tokenFor(gestorEmail);

        redeem(gestor, code, device)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.voucherId").value(voucherId.toString()))
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.slotsCount").value(2))
                .andExpect(jsonPath("$.holderName").value("Participante 0"))
                .andExpect(jsonPath("$.redeemedAt").isString());

        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, voucherId))
                .isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT status FROM booking.bookings WHERE id = ?::uuid", String.class, bookingId))
                .isEqualTo("EXECUTED");
        assertThat(jdbc.queryForMap("""
                SELECT channel, local_result, server_outcome FROM offline.offline_validations WHERE voucher_id = ?""",
                voucherId)).containsEntry("channel", "MANUAL_CODE").containsEntry("local_result", "ACCEPTED")
                .containsEntry("server_outcome", "ACCEPTED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_trail WHERE action = 'CONTINGENCY_REDEEMED' AND entity_id = ?",
                Long.class, voucherId.toString())).isEqualTo(1);

        // Segundo intento: doble canje, queda como evidencia.
        clock.advance(java.time.Duration.ofSeconds(5));
        redeem(gestor, code.toLowerCase(), device)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_ALREADY_REDEEMED"))
                .andExpect(jsonPath("$.redeemedAt").isString());
        assertThat(jdbc.queryForMap("""
                SELECT count(*) AS n FROM offline.offline_validations
                 WHERE voucher_id = ? AND local_result = 'REJECTED_DUPLICATE' AND server_outcome = 'REJECTED_DUPLICATE'""",
                voucherId)).containsEntry("n", 1L);
    }

    @Test
    void rejectionsFollowTheDocumentedTableAndPersistTheirEvidence() throws Exception {
        String gestor;
        String scheduleId = schedule();
        UUID revoked = issuedVoucher(confirmedBooking(scheduleId, 1));
        jdbc.update("UPDATE voucher.vouchers SET status = 'REVOKED', revoked_at = now(), revocation_reason = 'Cancelada'"
                + " WHERE id = ?", revoked);
        UUID foreign = issuedVoucher(confirmedBooking(schedule(), 1));
        String wrongDayId = schedule();
        UUID wrongDay = issuedVoucher(confirmedBooking(wrongDayId, 1));
        UUID expired = issuedVoucher(confirmedBooking(scheduleId, 1));
        jdbc.update("UPDATE voucher.vouchers SET status = 'EXPIRED' WHERE id = ?", expired);
        String otherManager = accreditedGestor(createAssociation());
        goTo(scheduleId);
        gestor = tokenFor(gestorEmail);

        redeem(gestor, "AAAAAAAA", device).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_FOUND"));
        redeem(gestor, shortCode(revoked), device).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_REVOKED"));
        redeem(gestor, shortCode(expired), device).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_EXPIRED"));
        redeem(gestor, shortCode(wrongDay), device).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_WRONG_DATE"));
        redeem(gestor, "no valido", device).andExpect(status().isUnprocessableEntity());

        // Un gestor de otra asociación recibe 404, igual que si el código no existiera.
        String otherToken = tokenFor(otherManager);
        redeem(otherToken, shortCode(foreign), "GESTOR-MOVIL-" + SEQ.incrementAndGet())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VOUCHER_NOT_FOUND"));

        assertThat(jdbc.queryForObject("""
                SELECT local_result || '/' || server_outcome FROM offline.offline_validations
                 WHERE voucher_id = ?""", String.class, revoked)).isEqualTo("REJECTED_REVOKED/REJECTED_REVOKED");
        assertThat(jdbc.queryForObject("""
                SELECT local_result FROM offline.offline_validations WHERE voucher_id = ?""", String.class, wrongDay))
                .isEqualTo("REJECTED_WRONG_DATE");
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, wrongDay))
                .isEqualTo("ISSUED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offline.offline_validations WHERE voucher_id = ?",
                Long.class, foreign)).isZero();
    }

    @Test
    void concurrentRedemptionsOfTheSameCodeYieldASingleSuccess() throws Exception {
        String scheduleId = schedule();
        String bookingId = confirmedBooking(scheduleId, 1);
        UUID voucherId = issuedVoucher(bookingId);
        String code = shortCode(voucherId);
        goTo(scheduleId);
        String gestor = tokenFor(gestorEmail);

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return redeem(gestor, code, device).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(200, 409);
            assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM voucher.vouchers WHERE id = ?", String.class, voucherId))
                .isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM offline.offline_validations WHERE voucher_id = ? AND local_result = 'ACCEPTED'""",
                Long.class, voucherId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM booking.booking_status_history WHERE booking_id = ?::uuid
                   AND to_status = 'EXECUTED'""", Long.class, bookingId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- fixtures

    private MockHttpServletRequestBuilder roster(String token, String deviceId, String date) {
        return get("/api/v1/offline/roster").param("date", date)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).header("X-Device-Id", deviceId);
    }

    private ResultActions redeem(String token, String code, String deviceId) throws Exception {
        return mvc.perform(post("/api/v1/offline/contingency/redeem")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"shortCode\":\"" + code + "\"}"));
    }

    private String shortCode(UUID voucherId) {
        return jdbc.queryForObject("SELECT short_code FROM voucher.vouchers WHERE id = ?", String.class, voucherId);
    }

    private LocalDate dayOf(String scheduleId) {
        return jdbc.queryForObject("SELECT start_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, scheduleId).toInstant().atZone(BOGOTA).toLocalDate();
    }

    /** Lleva el reloj de la aplicación a las 8:00 (Bogotá) del día de la franja; emitir tokens después. */
    private void goTo(String scheduleId) {
        clock.set(dayOf(scheduleId).atTime(LocalTime.of(8, 0)).atZone(BOGOTA).toInstant());
    }

    private String confirmedBooking(String scheduleId, int slots) throws Exception {
        String bookingId = lockBooking(scheduleId, slots);
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);
        return bookingId;
    }

    private UUID issuedVoucher(String bookingId) {
        return issuer.issue(UUID.fromString(bookingId)).orElseThrow();
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

    private String lockBooking(String scheduleId, int slots) throws Exception {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            participants.append(i == 0 ? "" : ",").append("""
                    {"fullName":"Participante %d","docType":"CC","docNumber":"%s%02d","medicalEps":"Sura","isHolder":%s}"""
                    .formatted(i, HOLDER_DOC, i, i == 0));
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
                {"nit":"7%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina Offline",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@offline.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 900_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        touristEmail = uniqueEmail("turista-offline");
        registrationService.registerTourist(tourist(touristEmail, uniqueDocNumber(),
                new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA,
                        ConsentPurpose.HEALTH_EMERGENCY))), CLIENT);
        return touristEmail;
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
