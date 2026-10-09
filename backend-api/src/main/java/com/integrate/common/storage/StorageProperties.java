package com.integrate.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Conexión a S3 (ADR-0007 §5, ADR-0010 §7). {@code sensitiveBucket} ({@code AWS_S3_SENSITIVE_BUCKET_NAME}) y
 * {@code voucherBucket} ({@code AWS_S3_BUCKET_NAME}) son obligatorios.
 * {@code endpoint} solo se fija en local (LocalStack, {@code AWS_ENDPOINT_URL}) y activa el estilo de ruta.
 *
 * <p>{@code accessKeyId}/{@code secretAccessKey} son opcionales: el perfil {@code dev} lee
 * {@code .env.development} como propiedades de Spring, no como variables del proceso, y la cadena del SDK no
 * las vería. Sin ellas se usa la cadena por defecto del SDK (rol IAM en despliegue, SEC-DIR-004).
 */
@ConfigurationProperties("integrate.storage")
public record StorageProperties(@DefaultValue("us-east-1") String region, String endpoint, String sensitiveBucket,
                                String voucherBucket, String accessKeyId, String secretAccessKey) {

    boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank();
    }

    @Override
    public String toString() {
        // Nunca imprimir el secreto.
        return "StorageProperties[region=" + region + ", endpoint=" + endpoint + ", sensitiveBucket=" + sensitiveBucket
                + ", voucherBucket=" + voucherBucket
                + ", staticCredentials=" + hasStaticCredentials() + "]";
    }
}
