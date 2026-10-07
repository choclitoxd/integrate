package com.integrate.common.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Llaves AES-256 de datos sensibles por versión (ADR-0007 §4): {@code keys.<versión>} es la llave
 * de 32 bytes en Base64 ({@code SENSITIVE_DATA_KEY_V1}, ...) y {@code currentVersion} la que cifra
 * los valores nuevos ({@code SENSITIVE_DATA_KEY_CURRENT_VERSION}). Las versiones anteriores se
 * conservan para descifrar filas antiguas.
 */
@ConfigurationProperties("integrate.crypto.sensitive")
public record SensitiveDataProperties(@DefaultValue Map<Short, String> keys, @DefaultValue("1") short currentVersion) {

    @Override
    public String toString() {
        // Nunca imprimir las llaves.
        return "SensitiveDataProperties[versions=" + keys.keySet() + ", currentVersion=" + currentVersion + "]";
    }
}
