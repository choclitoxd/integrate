package com.integrate.modules.voucher;

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

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RoleAssignmentService;
import com.integrate.modules.booking.service.BookingVouchers;
import com.integrate.modules.catalog.service.ScheduleDirectory;
import com.integrate.modules.voucher.entity.Voucher;
import com.integrate.modules.voucher.repository.SigningKeyRepository;
import com.integrate.modules.voucher.repository.VoucherRepository;
import com.integrate.modules.voucher.service.impl.SigningKeyRegistrar;
import com.integrate.modules.voucher.service.impl.VoucherIssuer;
import com.integrate.modules.voucher.service.impl.VoucherMaintenance;
import com.integrate.common.crypto.RsaSigningKeys;
import com.integrate.support.PostgresIntegrationTest;
import com.integrate.support.TestKeys;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Voucher de punta a punta (ADR-0010 §10): emisión al confirmar el pago, token RS256 verificable y re-derivable,
 * PDF/A con QR legible, barrido durable, carreras y rotación de la llave.
 */
@AutoConfigureMockMvc
class VoucherApiIntegrationTest extends PostgresIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String HOLDER_DOC = "30940000";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private SigningKeyRepository signingKeyRepository;

    @Autowired
    private VoucherIssuer issuer;

    @Autowired
    private VoucherMaintenance maintenance;

    @Autowired
    private SigningKeyRegistrar registrar;

    @Autowired
    private BookingVouchers bookingVouchers;

    @Autowired
    private ScheduleDirectory scheduleDirectory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String hostToken;
    private String experienceId;
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
        touristToken = tokenFor(registerTourist());
    }

    @AfterEach
    void restoreStorage() {
        voucherStorage.failWrites(false);
    }

    // ------------------------------------------------------------- emisión al pagar

    @Test
    void paidBookingGetsASignedVoucherAndAnArchivalPdf() throws Exception {
        String scheduleId = schedule();
        String bookingId = lockBooking(scheduleId, 2);
        payAndApprove(bookingId);
        Voucher voucher = awaitVoucherWithPdf(bookingId);

        String body = mvc.perform(get("/api/v1/bookings/" + bookingId + "/voucher")
                        .header(HttpHeaders.AUTHORIZATION, bearer(touristToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.voucherId").value(voucher.getId().toString()))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.pdfStatus").value("READY"))
                .andExpect(jsonPath("$.shortCode").value(org.hamcrest.Matchers.matchesPattern("^[A-HJ-NP-Z2-9]{8}$")))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        assertThat((String) JsonPath.read(body, "$.pdfUrl")).contains(voucher.getPdfS3Key());

        // Firma RS256 verificable con la llave pública publicada; encabezado y claims mínimos.
        String[] parts = token.split("\\.");
        assertThat(verifies(parts, VOUCHER_KEY.getPublic())).isTrue();
        Map<String, Object> headerJson = decode(parts[0]);
        assertThat(headerJson).containsExactly(Map.entry("alg", "RS256"), Map.entry("typ", "voucher+jwt"),
                Map.entry("kid", VOUCHER_KID));
        Map<String, Object> claims = decode(parts[1]);
        assertThat(claims.keySet()).containsExactly("iss", "jti", "bid", "sid", "xid", "vd", "doc", "slots", "iat",
                "exp");
        LocalDate validDate = jdbc.queryForObject("SELECT start_time FROM catalog.schedules WHERE id = ?::uuid",
                java.sql.Timestamp.class, scheduleId).toInstant().atZone(BOGOTA).toLocalDate();
        assertThat(claims).containsEntry("bid", bookingId).containsEntry("sid", scheduleId)
                .containsEntry("xid", experienceId).containsEntry("vd", validDate.toString())
                .containsEntry("doc", "CC:" + HOLDER_DOC + "00").containsEntry("slots", 2)
                .containsEntry("jti", voucher.getJti().toString());
        assertThat(JsonPath.<String>read(body, "$.validDate")).isEqualTo(validDate.toString());
        assertThat(token).doesNotContain("Participante").doesNotContain("Sura");
        assertThat(sha256(token)).isEqualTo(voucher.getTokenSha256());

        // La llave pública registrada es la de la llave privada configurada.
        assertThat(signingKeyRepository.findByKid(VOUCHER_KID)).hasValueSatisfying(k -> {
            assertThat(k.isActive()).isTrue();
            assertThat(k.getPublicKeyPem()).contains(Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(VOUCHER_KEY.getPublic().getEncoded()).substring(0, 64));
        });

        // PDF/A-2b: XMP pdfaid, OutputIntent sRGB, fuentes incrustadas y un QR que contiene el mismo token.
        byte[] pdf = voucherStorage.get(voucher.getPdfS3Key()).orElseThrow();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertThat(xmp).contains("pdfaid:part").contains("pdfaid:conformance");
            assertThat(document.getDocumentCatalog().getOutputIntents()).hasSize(1);
            PDResources resources = document.getPage(0).getResources();
            for (var name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                assertThat(font.isEmbedded()).as("fuente %s incrustada", font.getName()).isTrue();
            }
            assertThat(decodeQr(new PDFRenderer(document).renderImageWithDPI(0, 200))).isEqualTo(token);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.audit_trail WHERE action = 'VOUCHER_ISSUED' AND entity_id = ?""",
                Long.class, voucher.getId().toString())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ consulta

    @Test
    void onlyTheOwnerOfAConfirmedBookingSeesTheVoucher() throws Exception {
        String pending = lockBooking(schedule(), 1);
        getVoucher(touristToken, pending).andExpect(status().isNotFound());

        // Confirmada sin pasar por el webhook: aún no hay voucher → 409 con Retry-After.
        String confirmed = lockBooking(schedule(), 1);
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", confirmed);
        getVoucher(touristToken, confirmed)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_READY"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"));

        maintenance.sweepOnce();
        getVoucher(tokenFor(registerTourist()), confirmed).andExpect(status().isNotFound());
        getVoucher(touristToken, confirmed).andExpect(status().isOk());
        getVoucher(touristToken, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------- barrido y carreras

    @Test
    void sweepIssuesMissingVouchersAndCompletesPdfsAfterAStorageOutage() throws Exception {
        String bookingId = lockBooking(schedule(), 1);
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);

        voucherStorage.failWrites(true);
        maintenance.sweepOnce();
        Voucher voucher = voucherRepository.findByBookingId(UUID.fromString(bookingId)).orElseThrow();
        assertThat(voucher.getPdfS3Key()).isNull();
        getVoucher(touristToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfStatus").value("GENERATING"))
                .andExpect(jsonPath("$.pdfUrl").doesNotExist())
                .andExpect(jsonPath("$.token").isString());

        voucherStorage.failWrites(false);
        maintenance.sweepOnce();
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getPdfS3Key()).isNotNull();
        getVoucher(touristToken, bookingId).andExpect(jsonPath("$.pdfStatus").value("READY"));
    }

    @Test
    void concurrentIssuersCreateASingleVoucher() throws Exception {
        String bookingId = lockBooking(schedule(), 1);
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);

        List<Optional<UUID>> ids = runConcurrently(3, () -> issuer.issue(UUID.fromString(bookingId)));

        assertThat(ids).allSatisfy(id -> assertThat(id).isPresent());
        assertThat(ids.stream().map(Optional::get).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voucher.vouchers WHERE booking_id = ?::uuid",
                Long.class, bookingId)).isEqualTo(1);
    }

    @Test
    void aVoucherSignedWithAnotherKeyIsNotReDerived() throws Exception {
        String bookingId = lockBooking(schedule(), 1);
        jdbc.update("UPDATE booking.bookings SET status = 'CONFIRMED' WHERE id = ?::uuid", bookingId);
        var subject = bookingVouchers.subjectOf(UUID.fromString(bookingId)).orElseThrow();
        var schedule = scheduleDirectory.describe(subject.scheduleId()).orElseThrow();
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Voucher foreign = Voucher.issue(UUID.randomUUID(), subject.bookingId(), UUID.randomUUID(), (short) -1,
                "0".repeat(64), "ABCDEFGH", now, now.plusSeconds(3600));

        assertThat(issuer.rederive(foreign, subject, schedule)).isEmpty();
    }

    @Test
    void startingWithANewKeyIdRetiresThePreviousKey() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            var rotated = registrar.register(RsaSigningKeys.load(TestKeys.toBase64Pem(TestKeys.generate(2048)),
                    "VOUCHER_SIGNING_KEY"), "voucher-rotated-" + SEQ.incrementAndGet());
            assertThat(signingKeyRepository.findByKid(VOUCHER_KID).orElseThrow().getStatus()).isEqualTo("RETIRED");
            assertThat(signingKeyRepository.findByKid(rotated.kid()).orElseThrow().isActive()).isTrue();
            status.setRollbackOnly();
        });
        assertThat(signingKeyRepository.findByKid(VOUCHER_KID).orElseThrow().isActive()).isTrue();
    }

    // ---------------------------------------------------------------- fixtures

    private Voucher awaitVoucherWithPdf(String bookingId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            Optional<Voucher> voucher = voucherRepository.findByBookingId(UUID.fromString(bookingId));
            if (voucher.isPresent() && voucher.get().getPdfS3Key() != null) {
                return voucher.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The voucher of booking " + bookingId + " was not issued in time");
    }

    private void payAndApprove(String bookingId) throws Exception {
        String paymentId = JsonPath.read(postJson("/api/v1/bookings/" + bookingId + "/payment", touristToken,
                        "{\"paymentMethod\":\"PSE\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.paymentId");
        postJson("/api/v1/dev/payments/" + paymentId + "/simulate", touristToken, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
    }

    private ResultActions getVoucher(String token, String bookingId) throws Exception {
        return mvc.perform(get("/api/v1/bookings/" + bookingId + "/voucher")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static boolean verifies(String[] parts, java.security.PublicKey key) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decode(String part) {
        return JSON.readValue(Base64.getUrlDecoder().decode(part), java.util.LinkedHashMap.class);
    }

    private static String sha256(String token) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String decodeQr(BufferedImage image) throws Exception {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
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
                {"nit":"6%08d-%d","rntNumber":"%d","legalName":"Asociacion Campesina del Voucher",
                 "associationType":"ASOCIACION_VICTIMAS","municipalityCode":"05001","address":"Vereda La Esperanza",
                 "contactEmail":"contacto@esperanza.org","contactPhone":"+573001234567"}"""
                .formatted(seq % 100_000_000, seq % 10, 800_000 + seq))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerTourist() {
        String email = uniqueEmail("turista-voucher");
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
