package com.integrate.common.crypto;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Llaves AES-256 de datos sensibles por versión (ADR-0007 §4): {@code keys.<versión>} es la llave
 * de 32 bytes en Base64 ({@code SENSITIVE_DATA_KEY_V1}, ...) y {@code currentVersion} la que cifra
 * los valores nuevos ({@code SENSITIVE_DATA_KEY_CURRENT_VERSION}). Las versiones anteriores se
 * conservan para descifrar filas antiguas.
 *
 * <p>Una versión declarada sin valor (p. ej. {@code SENSITIVE_DATA_KEY_V2} sin definir antes de una
 * rotación, ADR-0046) se descarta aquí, para que el cifrador y {@code IdentityProtection} vean el mismo
 * conjunto de llaves. Si la versión vigente queda sin valor, el cifrador falla al arrancar.
 */
@ConfigurationProperties("integrate.crypto.sensitive")
public record SensitiveDataProperties(@DefaultValue Map<Short, String> keys, @DefaultValue("1") short currentVersion) {

    public SensitiveDataProperties {
        keys = keys.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public String toString() {
        // Nunca imprimir las llaves.
        return "SensitiveDataProperties[versions=" + keys.keySet() + ", currentVersion=" + currentVersion + "]";
    }
}
