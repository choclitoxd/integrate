package com.integrate.modules.booking;

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
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

/** "Mis experiencias" (ADR-0023 §4): propiedad, clasificación por {@code scope}, orden, paginación y privacidad. */
@AutoConfigureMockMvc
class MyBookingsApiIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private JdbcTemplate jdbc;

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

    @Test
    void upcomingAndPastAreClassifiedAndOrdered() throws Exception {
        String later = lockBooking(schedule(60), 2);
        String sooner = lockBooking(schedule(40), 1);
        confirm(later);
        confirm(sooner);
        String failed = lockBooking(schedule(50), 1);
        jdbc.update("UPDATE booking.bookings SET status = 'PAYMENT_FAILED' WHERE id = ?::uuid", failed);
        String executed = lockBooking(schedule(45), 1);
        confirm(executed);
        jdbc.update("UPDATE booking.bookings SET status = 'EXECUTED' WHERE id = ?::uuid", executed);
        String pending = lockBooking(schedule(55), 1);

        String upcoming = list("upcoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(upcoming, "$.items[*].bookingId"))
                .as("la más próxima primero").containsExactly(sooner, pending, later);
        assertThat(JsonPath.<Boolean>read(upcoming, "$.items[0].hasVoucher")).isTrue();
        assertThat(JsonPath.<Boolean>read(upcoming, "$.items[1].hasVoucher")).as("PENDING no tiene voucher").isFalse();
        assertThat(JsonPath.<String>read(upcoming, "$.items[0].experienceTitle")).isEqualTo("Sendero de la historia");
        assertThat(JsonPath.<String>read(upcoming, "$.items[0].municipality")).isEqualTo("MEDELLÍN");
        assertThat(JsonPath.<Integer>read(upcoming, "$.items[2].slots")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(upcoming, "$.items[2].totalAmountCop")).isEqualTo(160000);
        assertThat(upcoming).doesNotContain("Participante", "Sura", "Carlos Gomez", "+573001234567", "docNumber");

        String past = list("past").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(past, "$.items[*].bookingId"))
                .as("la más reciente primero").containsExactly(failed, executed);
        assertThat(JsonPath.<String>read(past, "$.items[1].status")).isEqualTo("EXECUTED");
        assertThat(JsonPath.<Boolean>read(past, "$.items[1].hasVoucher")).isTrue();
        assertThat(JsonPath.<Boolean>read(past, "$.items[0].hasVoucher")).isFalse();
    }

    @Test
    void expiredHoldsAndFinishedExperiencesMoveToPast() throws Exception {
        String pending = lockBooking(schedule(40), 1);
        String confirmed = lockBooking(schedule(41), 1);
        confirm(confirmed);

        // Vence el TTL de la PENDING: ya no se puede pagar y sale de las próximas aunque el worker no haya corrido.
        advance(Duration.ofMinutes(16));
        assertThat(ids(list("upcoming"))).containsExactly(confirmed);
        assertThat(ids(list("past"))).containsExactly(pending);

        // Termina la franja de la CONFIRMED: pasa al historial.
        advance(Duration.ofDays(45));
        assertThat(ids(list("upcoming"))).isEmpty();
        assertThat(ids(list("past"))).containsExactly(confirmed, pending);
    }

    @Test
    void onlyTheAuthenticatedTouristsBookingsArePagedAndValidated() throws Exception {
        for (int i = 0; i < 3; i++) {
            confirm(lockBooking(schedule(30 + i), 1));
        }
        String other = tokenFor(registerTourist());
        mvc.perform(get("/api/v1/me/bookings").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/me/bookings").param("size", "2").param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get("/api/v1/me/bookings").param("scope", "todas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/me/bookings").param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/me/bookings")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- fixtures

    private static List<String> ids(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.items[*].bookingId");
    }

    private ResultActions list(String scope) throws Exception {
        return mvc.perform(get("/api/v1/me/bookings").param("scope", scope)
                .header(HttpHeaders.AUTHORIZATION, bearer(touristToken)));
    }

    private void confirm(String bookingId) {
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);
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
                    {"fullName":"Participante %d","docType":"CC","docNumber":"5094000%03d","medicalEps":"Sura","isHolder":%s}"""
                    .formatted(i, i, i == 0));
        }
        return JsonPath.read(mvc.perform(post("/api/v1/bookings/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"scheduleId":"%s","requestedSlots":%d,
                                 "emergencyContact":{"fullName":"Carlos Gomez","phone":"+573001234567"},
                                 "participants":[%s]}""".formatted(scheduleId, slots, participants)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.bookingId");
    }

    /** Franja de 07:00 a 12:00 hora local, {@code daysAhead} días después del reloj de prueba. */
    private String schedule(int daysAhead) throws Exception {
        LocalDate day = LocalDate.ofInstant(START, BOGOTA).plusDays(daysAhead);
        String json = postJson("/api/v1/host/experiences/" + experienceId + "/schedules", hostToken,
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":6}]}"
                        .formatted(day, day))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private String approvedExperience() throws Exception {
        String id = JsonPath.read(postJson("/api/v1/host/experiences", hostToken, """
                {"title":"Sendero de la historia","description":"Recorrido guiado por la comunidad.","safetyGuidelines":"Agua.",
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
                {"nit":"4%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina del Historial",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 950_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        String email = uniqueEmail("turista-historial");
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
