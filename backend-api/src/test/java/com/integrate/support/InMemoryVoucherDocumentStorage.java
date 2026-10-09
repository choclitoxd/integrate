package com.integrate.support;

import com.integrate.common.storage.VoucherDocumentStorage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Doble del bucket de vouchers para pruebas (ADR-0010 §10); puede simular una caída de S3. */
public class InMemoryVoucherDocumentStorage implements VoucherDocumentStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final AtomicBoolean failing = new AtomicBoolean();
    private final Clock clock;

    public InMemoryVoucherDocumentStorage(Clock clock) {
        this.clock = clock;
    }

    /** Mientras esté activo, {@link #put} falla como un S3 caído. */
    public void failWrites(boolean fail) {
        failing.set(fail);
    }

    /** Simula un objeto perdido en S3; devuelve su contenido para restaurarlo con {@link #put}. */
    public Optional<byte[]> remove(String key) {
        return Optional.ofNullable(objects.remove(key));
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(objects.get(key)).map(byte[]::clone);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        if (failing.get()) {
            throw new IllegalStateException("Simulated S3 outage");
        }
        objects.put(key, content.clone());
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    @Override
    public PresignedDownload presignDownload(String key, Duration ttl) {
        return new PresignedDownload(URI.create("https://s3.test.invalid/" + key + "?X-Amz-Signature=test"),
                clock.instant().plus(ttl));
    }
}
