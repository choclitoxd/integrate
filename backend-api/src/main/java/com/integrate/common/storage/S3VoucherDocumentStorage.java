package com.integrate.common.storage;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/** Adaptador de AWS SDK v2 sobre el bucket de vouchers. */
public class S3VoucherDocumentStorage implements VoucherDocumentStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Clock clock;

    public S3VoucherDocumentStorage(S3Client client, S3Presigner presigner, String bucket, Clock clock) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.clock = clock;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        client.putObject(p -> p.bucket(bucket).key(key).contentType(contentType).contentLength((long) content.length),
                RequestBody.fromBytes(content));
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(h -> h.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // HeadObject no trae cuerpo: un objeto inexistente llega como 404 genérico.
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(client.getObjectAsBytes(g -> g.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public PresignedDownload presignDownload(String key, Duration ttl) {
        PresignedGetObjectRequest presigned = presigner.presignGetObject(p -> p
                .signatureDuration(ttl)
                .getObjectRequest(o -> o.bucket(bucket).key(key)));
        return new PresignedDownload(URI.create(presigned.url().toString()), clock.instant().plus(ttl));
    }
}
