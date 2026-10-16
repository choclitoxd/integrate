package com.integrate.quality.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RNF-01 (Seguridad · confidencialidad, Ley 1581): cifrado en reposo de los atributos sensibles y anonimización del
 * catálogo público. El cifrado TLS 1.3 en tránsito (calificación A+) no se puede medir sin el despliegue en AWS: queda
 * en la matriz como "No automatizable en local".
 *
 * <p>Las pruebas marcadas {@code nonconformity} verifican el criterio literal del plan sobre lo que el sistema hace
 * hoy distinto; se excluyen del build normal y corren en {@code -Pquality} para dejar la no conformidad medida.
 */
@Tag("rnf-01")
@Tag("iso-seguridad")
@DisplayName("[RNF-01] Cifrado en reposo y anonimización de atributos sensibles")
class SensitiveDataAtRestQualityTest extends QualityIntegrationTest {

    @Test
    @DisplayName("[RNF-01] Toda columna sensible (*_enc) usa el dominio de cifrado AES-256-GCM")
    void everySensitiveColumnIsCiphertext() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT table_schema || '.' || table_name || '.' || column_name AS col, domain_name
                  FROM information_schema.columns
                 WHERE column_name LIKE '%\\_enc' ESCAPE '\\'
                   AND table_schema NOT IN ('pg_catalog', 'information_schema')""");
        long ciphertext = columns.stream().filter(c -> "ciphertext".equals(c.get("domain_name"))).count();

        QualityMetrics.record("RNF-01", "Columnas sensibles bajo el dominio de cifrado",
                QualityMetrics.percent(ciphertext, columns.size()), "%", "= 100 %", ciphertext == columns.size(),
                columns.size() + " columnas *_enc (víctima, RUV, salud/EPS, contacto de emergencia, cuentas de pago)");
        assertThat(columns).hasSizeGreaterThanOrEqualTo(8);
        assertThat(ciphertext).isEqualTo(columns.size());
    }

    @Test
    @DisplayName("[RNF-01] Los datos de salud y contacto de una reserva no aparecen en claro en la base")
    void bookingHealthDataIsUnreadableAtRest() throws Exception {
        Scenario s = scenario(12);
        String bookingId = lockBooking(s.touristToken(), schedule(s, 6), 2);

        List<byte[]> stored = jdbc.query("""
                SELECT eps_enc AS v FROM booking.booking_participants WHERE booking_id = ?::uuid
                UNION ALL SELECT emergency_contact_name_enc FROM booking.bookings WHERE id = ?::uuid
                UNION ALL SELECT emergency_contact_phone_enc FROM booking.bookings WHERE id = ?::uuid""",
                (rs, i) -> rs.getBytes("v"), bookingId, bookingId, bookingId);
        long leaks = stored.stream().map(b -> new String(b, StandardCharsets.ISO_8859_1))
                .filter(v -> v.contains("Sura") || v.contains("Carlos") || v.contains("3001234567")).count();

        QualityMetrics.record("RNF-01", "Valores sensibles de reserva legibles en reposo", leaks, "valores",
                "= 0", leaks == 0, stored.size() + " valores cifrados (EPS por participante y contacto de emergencia)");
        assertThat(stored).hasSize(4);
        assertThat(leaks).isZero();
    }

    @Test
    @DisplayName("[RNF-01] El catálogo público no expone datos del anfitrión ni de víctima")
    void publicCatalogExposesNoHostOrVictimData() throws Exception {
        Scenario s = scenario(12);
        String list = getJson("/api/v1/experiences", null).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        String detail = getJson("/api/v1/experiences/" + s.experienceId(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String hostDoc = jdbc.queryForObject("""
                SELECT u.doc_number FROM catalog.experiences e
                  JOIN catalog.host_profiles h ON h.id = e.host_id
                  JOIN auth.users u ON u.id = h.user_id WHERE e.id = ?::uuid""", String.class, s.experienceId());

        List<String> forbidden = List.of("victim", "ruv", "victimization", "docNumber", "email", "phone", hostDoc);
        long exposed = forbidden.stream().filter(f -> (list + detail).toLowerCase().contains(f.toLowerCase())).count();

        QualityMetrics.record("RNF-01", "Atributos del anfitrion o de victima en endpoints publicos", exposed,
                "atributos", "= 0", exposed == 0, "GET /experiences y /experiences/{id} sin autenticación");
        assertThat(exposed).isZero();
    }

    @Test
    @Tag("nonconformity")
    @DisplayName("[RNF-01] NC: el detalle público expone coordenadas exactas (el plan exige 0 % y ofuscación por radio)")
    void publicDetailMustNotExposeExactCoordinates() throws Exception {
        Scenario s = scenario(12);
        String detail = getJson("/api/v1/experiences/" + s.experienceId(), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Object> found = JsonPath.read(detail, "$..latitude");
        Object latitude = found.isEmpty() ? null : found.getFirst();
        int decimals = latitude == null ? 0 : new BigDecimal(latitude.toString()).stripTrailingZeros().scale();

        // Dos decimales ≈ 1,1 km: por encima de eso el punto identifica una vereda o una vivienda.
        QualityMetrics.record("RNF-01", "Decimales de latitud expuestos en el detalle publico", decimals,
                "decimales", "<= 2 (ofuscación por radio)", decimals <= 2,
                "experiencia creada con latitud 6.251234; respuesta: " + latitude);
        assertThat(decimals).isLessThanOrEqualTo(2);
    }

    @Test
    @Tag("nonconformity")
    @DisplayName("[RNF-01] NC: documento de identidad y teléfono del usuario se guardan en claro")
    void identityDocumentsAndPhonesMustBeEncrypted() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, data_type, domain_name FROM information_schema.columns
                 WHERE table_schema = 'auth' AND table_name = 'users' AND column_name IN ('doc_number', 'phone')""");
        long encrypted = columns.stream().filter(c -> "ciphertext".equals(c.get("domain_name"))).count();

        QualityMetrics.record("RNF-01", "Documento y telefono de auth.users cifrados",
                QualityMetrics.percent(encrypted, columns.size()), "%", "= 100 %", encrypted == columns.size(),
                "auth.users.doc_number y auth.users.phone (dominios " + columns.stream()
                        .map(c -> String.valueOf(c.get("domain_name"))).toList() + ")");
        assertThat(encrypted).isEqualTo(columns.size());
    }
}
