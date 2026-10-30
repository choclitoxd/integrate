package com.integrate.common.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redacción de claves sensibles de la bitácora (ADR-0035 §3, RNF-01): la bitácora guarda referencias e importes, nunca
 * datos personales sensibles ni credenciales. Una clave de la lista cerrada (sin distinguir mayúsculas, guiones ni
 * guiones bajos) conserva la clave y pierde el valor. Recorre mapas y listas anidados. Función pura.
 */
public final class AuditRedactor {

    public static final String REDACTED = "[REDACTED]";

    /** Claves normalizadas: minúsculas y solo letras y dígitos. */
    private static final Set<String> SENSITIVE = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken", "secret", "apikey",
            "eps", "medicaleps", "phone", "phonenumber",
            "emergencycontact", "emergencycontactname", "emergencycontactphone",
            "docnumber", "documentnumber", "holderdocument",
            "accountnumber", "victimcondition", "ruvregistrationnumber", "victimizationtype");

    private AuditRedactor() {
    }

    public static Map<String, Object> redact(Map<String, Object> source) {
        Map<String, Object> clean = new LinkedHashMap<>();
        source.forEach((key, value) -> clean.put(key, isSensitive(key) ? REDACTED : redactValue(value)));
        return clean;
    }

    /** {@code true} si alguna clave, en cualquier nivel, sería redactada. */
    public static boolean containsSensitiveKey(Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (isSensitive(entry.getKey()) || containsSensitiveValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return redact((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> clean = new ArrayList<>(list.size());
            list.forEach(item -> clean.add(redactValue(item)));
            return clean;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static boolean containsSensitiveValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return containsSensitiveKey((Map<String, Object>) map);
        }
        return value instanceof List<?> list && list.stream().anyMatch(AuditRedactor::containsSensitiveValue);
    }

    private static boolean isSensitive(String key) {
        return SENSITIVE.contains(key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
    }
}