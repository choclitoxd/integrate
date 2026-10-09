package com.integrate.modules.voucher.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Emisión de vouchers (ADR-0010 §2, §9). SEC-DIR-004: la llave privada llega por variable de entorno, es
 * obligatoria (fail-fast) y nunca se imprime ({@link #toString()}).
 *
 * @param signingKey       llave privada RSA ≥ 2048 bits, PKCS#8 PEM en Base64 ({@code VOUCHER_SIGNING_KEY})
 * @param signingKeyId     {@code kid} con el que se publica su llave pública ({@code VOUCHER_SIGNING_KEY_ID})
 * @param validityAfterEnd vigencia del voucher después del fin de la franja (claim {@code exp})
 * @param downloadUrlTtl   vigencia de la URL prefirmada del PDF
 */
@ConfigurationProperties("integrate.voucher")
public record VoucherProperties(String signingKey, String signingKeyId, Duration validityAfterEnd,
                                Duration downloadUrlTtl, Sweep sweep, Executor executor) {

    public VoucherProperties {
        if (signingKeyId == null || !signingKeyId.matches("^[A-Za-z0-9._-]{4,64}$")) {
            throw new IllegalStateException("VOUCHER_SIGNING_KEY_ID must match ^[A-Za-z0-9._-]{4,64}$");
        }
        validityAfterEnd = Objects.requireNonNullElse(validityAfterEnd, Duration.ofHours(24));
        downloadUrlTtl = Objects.requireNonNullElse(downloadUrlTtl, Duration.ofMinutes(5));
        sweep = Objects.requireNonNullElse(sweep, new Sweep(true, Duration.ofSeconds(60), 50));
        executor = Objects.requireNonNullElse(executor, new Executor(2, 4, 200));
    }

    /** Barrido durable de emisiones y PDF pendientes (ADR-0010 §5). */
    public record Sweep(boolean enabled, Duration interval, int batchSize) {
    }

    /** Ejecutor acotado de la emisión inmediata ({@code @Async}). */
    public record Executor(int core, int max, int queue) {
    }

    @Override
    public String toString() {
        return "VoucherProperties[signingKeyId=" + signingKeyId + ", validityAfterEnd=" + validityAfterEnd + "]";
    }
}
