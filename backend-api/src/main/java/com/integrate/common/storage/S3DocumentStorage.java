package com.integrate.common.storage;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** Adaptador de AWS SDK v2 sobre el bucket de documentos sensibles. */
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Clock clock;

    public S3DocumentStorage(S3Client client, S3Presigner presigner, String bucket, Clock clock) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.clock = clock;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(p -> p
                .signatureDuration(ttl)
                .putObjectRequest(o -> o.bucket(bucket).key(key).contentType(contentType)));
        return new PresignedUpload(URI.create(presigned.url().toString()), clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse head = client.headObject(h -> h.bucket(bucket).key(key));
            return Optional.of(new StoredObject(head.contentType(), head.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HeadObject no trae cuerpo: un objeto inexistente llega como 404 genérico.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<byte[]> readPrefix(String key, int length) {
        try {
            // Rango HTTP: S3 entrega solo esos bytes, no el archivo completo.
            return Optional.of(client.getObjectAsBytes(g -> g.bucket(bucket).key(key)
                    .range("bytes=0-" + (length - 1))).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // Un objeto vacío no admite rango (416): no tiene bytes que leer.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            if (e.statusCode() == 416) {
                return Optional.of(new byte[0]);
            }
            throw e;
        }
    }
}
