package com.integrate.quality.support;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.DEV_POLICY;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueDocNumber;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.modules.voucher.service.impl.VoucherMaintenance;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de la suite de calidad (ADR-0019 §2): mismo contexto Spring que las pruebas de integración por módulo
 * (PostgreSQL real, cadena real de Spring Security) y los fixtures de catálogo, reserva y pago que los
 * escenarios del Plan de Gestión de la Calidad necesitan.
 */
@AutoConfigureMockMvc
@Tag("quality")
public abstract class QualityIntegrationTest extends PostgresIntegrationTest {

    protected static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    protected static final long PRICE = 80_000;
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected RoleAssignmentService roleAssignmentService;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected VoucherRepository voucherRepository;

    @Autowired
    protected VoucherMaintenance voucherMaintenance;

    @AfterEach
    void resetQualityClock() {
        clock.set(START);
    }

    // ------------------------------------------------------------------- actores

    /** Asociación, anfitrión avalado y una experiencia aprobada con la capacidad de carga indicada. */
    protected Scenario scenario(int maxCapacityPerSlot) throws Exception {
        String adminToken = tokenFor(userWithRole("admin", RoleCode.ADMIN));
        String associationId = createAssociation(adminToken);
        String hostEmail = uniqueEmail("anfitrion");
        UUID hostUserId = registrationService.registerTourist(tourist(hostEmail), CLIENT).userId();
        postJson("/api/v1/admin/host-profiles", adminToken,
                "{\"userId\":\"" + hostUserId + "\",\"associationId\":\"" + associationId + "\"}")
                .andExpect(status().isCreated());
        String hostToken = tokenFor(hostEmail);
        String experienceId = createExperience(hostToken, maxCapacityPerSlot);
        postJson("/api/v1/host/experiences/" + experienceId + "/submit", hostToken, null).andExpect(status().isOk());
        postJson("/api/v1/admin/experiences/" + experienceId + "/approve", adminToken, null)
                .andExpect(status().isOk());
        String touristEmail = registerTourist();
        return new Scenario(adminToken, hostToken, experienceId, touristEmail, tokenFor(touristEmail));
    }

    protected record Scenario(String adminToken, String hostToken, String experienceId, String touristEmail,
                              String touristToken) {
    }

    /** Turista con consentimiento de datos personales y de salud (precondición de la reserva). */
    protected String registerTourist() {
        String email = uniqueEmail("turista-calidad");
        registrationService.registerTourist(tourist(email, uniqueDocNumber(), new ConsentAcceptance(DEV_POLICY,
                Set.of(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.HEALTH_EMERGENCY))), CLIENT);
        return email;
    }

    protected String userWithRole(String prefix, RoleCode role) {
        String email = uniqueEmail(prefix);
        UUID id = registrationService.registerTourist(tourist(email), CLIENT).userId();
        roleAssignmentService.grant(id, role);
        return email;
    }

    protected String tokenFor(String email) throws Exception {
        return JsonPath.read(login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    protected ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    // ------------------------------------------------------------ catálogo

    protected String createAssociation(String adminToken) throws Exception {
        long seq = SEQ.incrementAndGet();
        return JsonPath.read(postJson("/api/v1/admin/associations", adminToken, """
                {"nit":"5%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina de Calidad",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 900_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected String createExperience(String hostToken, int maxCapacityPerSlot) throws Exception {
        return createExperience(hostToken, maxCapacityPerSlot, PRICE);
    }

    protected String createExperience(String hostToken, int maxCapacityPerSlot, long unitPriceCop) throws Exception {
        return JsonPath.read(postJson("/api/v1/host/experiences", hostToken, """
                {"title":"Sendero de calidad %d","description":"Recorrido guiado por la comunidad.",
                 "safetyGuidelines":"Llevar agua.","physicalIntensity":"MEDIUM","durationMinutes":240,
                 "municipalityCode":"05001","vereda":"La Esperanza","meetingPoint":"Parque principal",
                 "latitude":6.251234,"longitude":-75.563456,"maxCapacityPerSlot":%d,"unitPriceCop":%d,
                 "categoryCodes":["SENDERISMO_MEMORIA"]}""".formatted(SEQ.incrementAndGet(), maxCapacityPerSlot,
                        unitPriceCop))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Una franja de 07:00 a 12:00 en un día futuro distinto por llamada. */
    protected String schedule(Scenario s, int slots) throws Exception {
        return JsonPath.read(createSchedule(s, slots).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$[0].id");
    }

    protected ResultActions createSchedule(Scenario s, int slots) throws Exception {
        return createSchedule(s.hostToken(), s.experienceId(), slots);
    }

    protected ResultActions createSchedule(String hostToken, String experienceId, int slots) throws Exception {
        LocalDate day = LocalDate.ofInstant(START, BOGOTA).plusDays(30 + SEQ.incrementAndGet() % 300);
        return postJson("/api/v1/host/experiences/" + experienceId + "/schedules", hostToken,
                "{\"schedules\":[{\"localStart\":\"%sT07:00:00\",\"localEnd\":\"%sT12:00:00\",\"totalSlots\":%d}]}"
                        .formatted(day, day, slots));
    }

    /** Consulta pública de disponibilidad de un mes (la lectura que hace el turista en el catálogo). */
    protected static String availabilityPath(String experienceId) {
        LocalDate from = LocalDate.ofInstant(START, BOGOTA).plusDays(30);
        return "/api/v1/experiences/" + experienceId + "/availability?dateFrom=" + from + "&dateTo="
                + from.plusDays(29);
    }

    protected int availableSlots(String scheduleId) {
        return jdbc.queryForObject("SELECT available_slots FROM catalog.schedules WHERE id = ?::uuid", Integer.class,
                scheduleId);
    }

    protected int totalSlots(String scheduleId) {
        return jdbc.queryForObject("SELECT total_slots FROM catalog.schedules WHERE id = ?::uuid", Integer.class,
                scheduleId);
    }

    // ------------------------------------------------------------- reserva y pago

    protected ResultActions lock(String token, String scheduleId, int slots) throws Exception {
        return mvc.perform(post("/api/v1/bookings/lock").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(lockBody(scheduleId, slots)));
    }

    protected String lockBooking(String token, String scheduleId, int slots) throws Exception {
        return JsonPath.read(lock(token, scheduleId, slots).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.bookingId");
    }

    protected static String lockBody(String scheduleId, int slots) {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            participants.append(i == 0 ? "" : ",").append("""
                    {"fullName":"Participante %d","docType":"CC","docNumber":"40940%05d","medicalEps":"Sura","isHolder":%s}"""
                    .formatted(i, i, i == 0));
        }
        return """
                {"scheduleId":"%s","requestedSlots":%d,
                 "emergencyContact":{"fullName":"Carlos Gomez","phone":"+573001234567"},"participants":[%s]}"""
                .formatted(scheduleId, slots, participants);
    }

    protected String startPayment(String token, String bookingId) throws Exception {
        return JsonPath.read(postJson("/api/v1/bookings/" + bookingId + "/payment", token,
                "{\"paymentMethod\":\"PSE\"}").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.paymentId");
    }

    /** Aprueba el pago con el simulador de {@code dev}/{@code test} (ADR-0009 §1). */
    protected void approvePayment(String token, String paymentId) throws Exception {
        postJson("/api/v1/dev/payments/" + paymentId + "/simulate", token, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isOk());
    }

    /**
     * Espera a que el voucher de la reserva exista con su PDF. La emisión es asíncrona (ADR-0010 §5): se fuerza el
     * barrido durable en cada vuelta para no depender del orden con el listener del evento de confirmación.
     */
    protected Voucher awaitVoucher(String bookingId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            voucherMaintenance.sweepOnce();
            Optional<Voucher> voucher = voucherRepository.findByBookingId(UUID.fromString(bookingId));
            if (voucher.isPresent() && voucher.get().getPdfS3Key() != null) {
                return voucher.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("The voucher of booking " + bookingId + " was not issued in time");
    }

    protected ResultActions getJson(String path, String token) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mvc.perform(request);
    }

    protected ResultActions postJson(String path, String token, String body) throws Exception {
        MockHttpServletRequestBuilder request = post(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------- concurrencia

    /** Lanza {@code tasks} tareas con {@code threads} hilos que arrancan a la vez (latch de salida). */
    protected static <T> List<T> runConcurrently(int threads, int tasks, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int index = i;
                Callable<T> call = () -> {
                    go.await();
                    return task.run(index);
                };
                futures.add(pool.submit(call));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    protected interface IndexedTask<T> {
        T run(int index) throws Exception;
    }
}
