package com.integrate.modules.catalog;

import static com.integrate.modules.auth.AuthTestData.CLIENT;
import static com.integrate.modules.auth.AuthTestData.PASSWORD;
import static com.integrate.modules.auth.AuthTestData.tourist;
import static com.integrate.modules.auth.AuthTestData.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort.Bounds;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort.GeoHit;
import com.integrate.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Verificación del punto de encuentro de punta a punta (ADR-0030): el autómata gobierna la aprobación sobre la
 * cadena real de Spring Security y PostgreSQL, con el geocodificador simulado (sin red).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "integrate.catalog.meeting-point.enabled=true")
class MeetingPointApiIntegrationTest extends PostgresIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    /** Caja amplia alrededor de Medellín (05001), el municipio del cuerpo de prueba. */
    private static final Bounds MEDELLIN = new Bounds(6.10, 6.40, -75.75, -75.45);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private RoleAssignmentService roleAssignmentService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private GeocodingPort geocoder;

    private String adminToken;
    private String hostToken;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = uniqueEmail("admin");
        roleAssignmentService.grant(registrationService.registerTourist(tourist(adminEmail), CLIENT).userId(),
                RoleCode.ADMIN);
        adminToken = tokenFor(adminEmail);

        String association = JsonPath.read(mvc.perform(postJson("/api/v1/admin/associations", adminToken,
                        associationBody())).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id");
        String hostEmail = uniqueEmail("anfitrion");
        UUID hostUser = registrationService.registerTourist(tourist(hostEmail), CLIENT).userId();
        mvc.perform(postJson("/api/v1/admin/host-profiles", adminToken,
                "{\"userId\":\"" + hostUser + "\",\"associationId\":\"" + association + "\"}"))
                .andExpect(status().isCreated());
        hostToken = tokenFor(hostEmail);

        when(geocoder.reverse(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        stubAddress(List.of(new GeoHit(6.25, -75.56, "Parque principal", "Parque principal, Medellín", null)));
    }

    /** El municipio devuelve su caja; la dirección, las coincidencias indicadas. */
    private void stubAddress(List<GeoHit> addressHits) {
        when(geocoder.search(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            // La consulta del municipio empieza por su nombre; la de la dirección, por el texto del punto de encuentro.
            return query.startsWith("Medell")
                    ? List.of(new GeoHit(6.25, -75.56, "Medellín", "Medellín, Antioquia", MEDELLIN))
                    : addressHits;
        });
    }

    // ----------------------------------------------------------------------------- aprobación

    @Test
    void matchingPointIsApprovedAndPublicDetailTrustsTheCoordinates() throws Exception {
        String id = createExperience(6.25, -75.56);
        submit(id).andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("MATCH");
        assertThat(history(id)).containsExactly("PENDIENTE|ENVIAR|VERIFICANDO", "VERIFICANDO|DIST_OK|MATCH");

        approve(id).andExpect(status().isOk());
        mvc.perform(get("/api/v1/experiences/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPointCoordinatesTrusted").value(true));
    }

    @Test
    void mismatchBlocksApprovalUntilTheAdminConfirmsIt() throws Exception {
        // Coordenadas a ~8 km de donde la dirección dice que está el parque.
        String id = createExperience(6.32, -75.50);
        submit(id).andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("MISMATCH");

        approve(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_MEETING_POINT_MISMATCH"))
                .andExpect(jsonPath("$.meetingPointCheckStatus").value("MISMATCH"));
        assertThat(experienceStatus(id)).isEqualTo("PENDING_REVIEW");

        // La respuesta del administrador trae la distancia y la sugerencia, sin cambiar las coordenadas.
        mvc.perform(get("/api/v1/admin/experiences?status=PENDING_REVIEW")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].meetingPointCheck.status").value("MISMATCH"))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].meetingPointCheck.suggestedLatitude")
                        .value(6.25))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].latitude").value(6.32));

        call(adminToken, "/api/v1/admin/experiences/" + id + "/meeting-point/confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPointCheck.status").value("CONFIRMADO"));
        approve(id).andExpect(status().isOk());
        assertThat(history(id)).contains("MISMATCH|CONFIRMAR|CONFIRMADO");
    }

    @Test
    void mediumDistanceIsReviewAndStillApprovableButTheMapTurnsToTheAddress() throws Exception {
        // ≈ 0,006° de latitud ≈ 670 m: entre 250 m y 1 000 m; sin lugar cercano que comparar → REVIEW.
        String id = createExperience(6.256, -75.56);
        submit(id).andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("REVIEW");

        approve(id).andExpect(status().isOk());
        mvc.perform(get("/api/v1/experiences/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPointCoordinatesTrusted").value(false));
    }

    @Test
    void confirmingAPointThatIsNotDoubtfulIsRejected() throws Exception {
        String id = createExperience(6.25, -75.56);
        submit(id).andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("MATCH");
        call(adminToken, "/api/v1/admin/experiences/" + id + "/meeting-point/confirm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATUS_TRANSITION"));
    }

    @Test
    void unreachableGeocoderLeavesTheExperienceUnverifiedButApprovable() throws Exception {
        when(geocoder.search(anyString())).thenThrow(
                new GeocodingPort.GeocodingUnavailableException("down", null));
        String id = createExperience(6.25, -75.56);
        submit(id).andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("UNVERIFIED");
        approve(id).andExpect(status().isOk());
    }

    @Test
    void noCoordinatesIsUnverified() throws Exception {
        String id = createExperience(null, null);
        submit(id).andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("UNVERIFIED");
        approve(id).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ anfitrión: repetir y corregir

    @Test
    void hostRecheckAndCoordinateCorrectionFollowTheAutomaton() throws Exception {
        String id = createExperience(6.32, -75.50);
        assertThat(checkStatus(id)).isEqualTo("PENDIENTE");

        call(hostToken, "/api/v1/host/experiences/" + id + "/meeting-point/check").andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPointCheck.status").value("MISMATCH"))
                .andExpect(jsonPath("$.meetingPointCheck.distanceMeters").isNumber());

        // Corrige las coordenadas: de MISMATCH pasa a CORREGIDO y exige verificar otra vez.
        mvc.perform(put("/api/v1/host/experiences/" + id).header(HttpHeaders.AUTHORIZATION, bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(experienceBody(6.25, -75.56)))
                .andExpect(status().isOk());
        assertThat(checkStatus(id)).isEqualTo("CORREGIDO");

        call(hostToken, "/api/v1/host/experiences/" + id + "/meeting-point/check").andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPointCheck.status").value("MATCH"));
        assertThat(history(id)).containsSubsequence("MISMATCH|EDITAR_COORDENADAS|CORREGIDO",
                "CORREGIDO|ENVIAR|VERIFICANDO", "VERIFICANDO|DIST_OK|MATCH");
    }

    @Test
    void anotherHostCannotCheckAnExperienceItDoesNotOwn() throws Exception {
        String id = createExperience(6.25, -75.56);
        String strangerEmail = uniqueEmail("ajeno");
        UUID stranger = registrationService.registerTourist(tourist(strangerEmail), CLIENT).userId();
        String association = jdbc.queryForObject("SELECT association_id::text FROM catalog.host_profiles LIMIT 1",
                String.class);
        mvc.perform(postJson("/api/v1/admin/host-profiles", adminToken,
                "{\"userId\":\"" + stranger + "\",\"associationId\":\"" + association + "\"}"))
                .andExpect(status().isCreated());
        call(tokenFor(strangerEmail), "/api/v1/host/experiences/" + id + "/meeting-point/check")
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------- ayudantes

    private String createExperience(Double latitude, Double longitude) throws Exception {
        return JsonPath.read(mvc.perform(postJson("/api/v1/host/experiences", hostToken,
                        experienceBody(latitude, longitude)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meetingPointCheck.status").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions submit(String id) throws Exception {
        return call(hostToken, "/api/v1/host/experiences/" + id + "/submit");
    }

    private ResultActions approve(String id) throws Exception {
        return call(adminToken, "/api/v1/admin/experiences/" + id + "/approve");
    }

    private String checkStatus(String id) {
        return jdbc.queryForObject("SELECT meeting_point_check_status FROM catalog.experiences WHERE id = ?::uuid",
                String.class, id);
    }

    private String experienceStatus(String id) {
        return jdbc.queryForObject("SELECT status FROM catalog.experiences WHERE id = ?::uuid", String.class, id);
    }

    private List<String> history(String id) {
        return jdbc.queryForList("""
                SELECT from_state || '|' || event || '|' || to_state FROM catalog.meeting_point_check_history
                 WHERE experience_id = ?::uuid ORDER BY id""", String.class, id);
    }

    private ResultActions call(String token, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String tokenFor(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String path, String token, String body) {
        return post(path).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String experienceBody(Double latitude, Double longitude) {
        String coordinates = latitude == null ? "" : "\"latitude\":%s,\"longitude\":%s,".formatted(latitude, longitude);
        return """
                {"title":"Recorrido de prueba","description":"Recorrido guiado por la comunidad.",
                 "safetyGuidelines":"Llevar agua.","physicalIntensity":"MEDIUM","durationMinutes":240,
                 "municipalityCode":"05001","vereda":"La Esperanza","meetingPoint":"Parque principal",
                 %s"maxCapacityPerSlot":12,"unitPriceCop":80000,"categoryCodes":["SENDERISMO_MEMORIA"]}"""
                .formatted(coordinates);
    }

    private static String associationBody() {
        return """
                {"nit":"%s","rntNumber":"%s","legalName":"Asociación Campesina La Esperanza",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(String.format("9%08d-%d", SEQ.incrementAndGet() % 100_000_000, SEQ.get() % 10),
                        100_000 + SEQ.incrementAndGet());
    }
}
