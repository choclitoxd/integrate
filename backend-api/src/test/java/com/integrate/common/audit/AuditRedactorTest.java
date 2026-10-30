package com.integrate.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Redacción de claves sensibles de la bitácora (ADR-0035 §3): sin datos personales ni credenciales en claro. */
class AuditRedactorTest {

    @Test
    void sensitiveKeysAreRedactedWhateverTheirCaseOrSeparators() {
        Map<String, Object> clean = AuditRedactor.redact(Map.of(
                "Phone", "+573001234567", "doc_number", "1234567", "emergencyContact", Map.of("name", "Carlos"),
                "medicalEps", "Sura", "access-token", "abc", "password", "x", "accountNumber", "99"));

        assertThat(clean).allSatisfy((key, value) -> assertThat(value).isEqualTo(AuditRedactor.REDACTED));
    }

    @Test
    void referencesAndAmountsAreKept() {
        Map<String, Object> source = Map.of("bookingId", "7f3c", "amountCop", 85000, "tokenSha256", "ab12",
                "fields", List.of("emergencyContact.name"), "subjects", List.of("a", "b"));

        assertThat(AuditRedactor.redact(source)).isEqualTo(source);
    }

    @Test
    void nestedMapsAndListsAreRedactedRecursively() {
        Map<String, Object> clean = AuditRedactor.redact(Map.of("holder", Map.of("docNumber", "1", "name", "n"),
                "participants", List.of(Map.of("phone", "3", "slot", 1))));

        assertThat(clean).containsEntry("holder", Map.of("docNumber", AuditRedactor.REDACTED, "name", "n"));
        assertThat(clean).containsEntry("participants", List.of(Map.of("phone", AuditRedactor.REDACTED, "slot", 1)));
    }

    @Test
    void reportsWhetherAnythingWasRedacted() {
        assertThat(AuditRedactor.containsSensitiveKey(Map.of("phone", "1"))).isTrue();
        assertThat(AuditRedactor.containsSensitiveKey(Map.of("bookingId", "1"))).isFalse();
        assertThat(AuditRedactor.containsSensitiveKey(Map.of("a", Map.of("eps", "x")))).isTrue();
    }
}