package com.integrate.common.storage;

import java.net.URI;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Clientes de S3 (ADR-0007 §5). Se crean sin red: las credenciales se resuelven en la primera llamada. */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties));
        if (hasEndpoint(properties)) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties));
        if (hasEndpoint(properties)) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean
    DocumentStorage documentStorage(S3Client client, S3Presigner presigner, StorageProperties properties, Clock clock) {
        if (properties.sensitiveBucket() == null || properties.sensitiveBucket().isBlank()) {
            throw new IllegalStateException("integrate.storage.sensitive-bucket is required (AWS_S3_SENSITIVE_BUCKET_NAME)");
        }
        return new S3DocumentStorage(client, presigner, properties.sensitiveBucket(), clock);
    }

    @Bean
    VoucherDocumentStorage voucherDocumentStorage(S3Client client, S3Presigner presigner, StorageProperties properties,
                                                  Clock clock) {
        if (properties.voucherBucket() == null || properties.voucherBucket().isBlank()) {
            throw new IllegalStateException("integrate.storage.voucher-bucket is required (AWS_S3_BUCKET_NAME)");
        }
        return new S3VoucherDocumentStorage(client, presigner, properties.voucherBucket(), clock);
    }

    private static AwsCredentialsProvider credentials(StorageProperties properties) {
        return properties.hasStaticCredentials()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()))
                : DefaultCredentialsProvider.builder().build();
    }

    /** LocalStack no resuelve subdominios por bucket: con endpoint propio se usa el estilo de ruta. */
    private static S3Configuration s3Configuration(StorageProperties properties) {
        return S3Configuration.builder().pathStyleAccessEnabled(hasEndpoint(properties)).build();
    }

    private static boolean hasEndpoint(StorageProperties properties) {
        return properties.endpoint() != null && !properties.endpoint().isBlank();
    }
}
