package com.integrate.support;

import com.integrate.common.storage.DocumentStorage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Doble de {@link DocumentStorage} para pruebas: los "objetos subidos" se registran con {@link #put}. */
public class InMemoryDocumentStorage implements DocumentStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDocumentStorage(Clock clock) {
        this.clock = clock;
    }

    /** Simula el {@code PUT} del cliente sobre la URL prefirmada. */
    public void put(String key, String contentType, long sizeBytes) {
        objects.put(key, new StoredObject(contentType, sizeBytes));
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
        return new PresignedUpload(URI.create("https://s3.test.invalid/" + key + "?X-Amz-Signature=test"),
                clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        return Optional.ofNullable(objects.get(key));
    }
}
