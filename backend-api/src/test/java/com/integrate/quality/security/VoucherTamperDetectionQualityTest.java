package com.integrate.quality.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.integrate.quality.support.QualityIntegrationTest;
import com.integrate.quality.support.QualityMetrics;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * RNF-05 (Seguridad · integridad y autenticidad): el QR del voucher lleva un JWT RS256 que la PWA verifica sin
 * conexión con la clave pública. Se emite un voucher real y se le aplican todas las alteraciones posibles de un
 * falsificador: cada claim cambiado, la firma de otro voucher, la firma truncada o con bits invertidos y
 * {@code alg: none}. Se verifica con RSASSA-PKCS1-v1_5/SHA-256, el mismo algoritmo de Web Crypto en la PWA.
 */
@Tag("rnf-05")
@Tag("iso-seguridad")
@DisplayName("[RNF-05] Firma criptográfica y verificación asimétrica de vouchers")
class VoucherTamperDetectionQualityTest extends QualityIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("[RNF-05] 100 % de los vouchers alterados son detectados con la clave pública")
    void everyAlterationIsDetected() throws Exception {
        Scenario s = scenario(12);
        String scheduleId = schedule(s, 6);
        String genuine = issuedToken(s, lockBooking(s.touristToken(), scheduleId, 2));
        String other = issuedToken(s, lockBooking(s.touristToken(), scheduleId, 1));
        PublicKey key = VOUCHER_KEY.getPublic();
        assertThat(verifies(genuine, key)).as("el voucher auténtico verifica").isTrue();

        String[] parts = genuine.split("\\.");
        Map<String, Object> claims = decode(parts[1]);
        List<String> forgeries = new ArrayList<>();
        for (String claim : claims.keySet()) {
            Map<String, Object> altered = new LinkedHashMap<>(claims);
            Object value = altered.get(claim);
            altered.put(claim, value instanceof Number n ? n.longValue() + 1 : value + "X");
            forgeries.add(parts[0] + "." + encode(altered) + "." + parts[2]);
        }
        Map<String, Object> moreSlots = new LinkedHashMap<>(claims);
        moreSlots.put("slots", 30);
        forgeries.add(parts[0] + "." + encode(moreSlots) + "." + parts[2]);
        forgeries.add(parts[0] + "." + parts[1] + "." + other.split("\\.")[2]);
        forgeries.add(parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 8));
        forgeries.add(parts[0] + "." + parts[1] + ".");
        forgeries.add(encode(Map.of("alg", "none", "typ", "voucher+jwt")) + "." + parts[1] + ".");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        for (int i = 0; i < signature.length; i += 16) {
            byte[] flipped = signature.clone();
            flipped[i] ^= 0x01;
            forgeries.add(parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(flipped));
        }

        long detected = forgeries.stream().filter(f -> !verifies(f, key)).count();
        QualityMetrics.record("RNF-05", "Tasa de deteccion de vouchers alterados",
                QualityMetrics.percent(detected, forgeries.size()), "%", "= 100 %", detected == forgeries.size(),
                forgeries.size() + " alteraciones: " + claims.size() + " claims, cupos inflados, firma ajena, "
                        + "truncada, vacía, alg=none y " + (signature.length + 15) / 16 + " bits invertidos");
        assertThat(detected).isEqualTo(forgeries.size());
    }

    private String issuedToken(Scenario s, String bookingId) throws Exception {
        approvePayment(s.touristToken(), startPayment(s.touristToken(), bookingId));
        awaitVoucher(bookingId);
        return JsonPath.read(getJson("/api/v1/bookings/" + bookingId + "/voucher", s.touristToken())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    /** Verificación del lado del validador: encabezado RS256 obligatorio y firma con la clave pública. */
    private static boolean verifies(String token, PublicKey key) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !"RS256".equals(decode(parts[0]).get("alg"))) {
                return false;
            }
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
        } catch (Exception malformed) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decode(String part) {
        return JSON.readValue(Base64.getUrlDecoder().decode(part), LinkedHashMap.class);
    }

    private static String encode(Map<String, Object> json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(json));
    }
}
