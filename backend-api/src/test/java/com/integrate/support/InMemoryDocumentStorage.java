package com.integrate.support;

import com.integrate.common.storage.DocumentStorage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Doble de {@link DocumentStorage} para pruebas: los "objetos subidos" se registran con {@link #put}. */
public class InMemoryDocumentStorage implements DocumentStorage {

    /** Cabecera de un PDF real: los objetos de prueba "son PDF" salvo que se indique otro contenido. */
    private static final byte[] PDF_HEADER = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    private record Entry(StoredObject meta, byte[] prefix) {
    }

    private final Map<String, Entry> objects = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDocumentStorage(Clock clock) {
        this.clock = clock;
    }

    /** Simula el {@code PUT} del cliente sobre la URL prefirmada; el contenido empieza como un PDF. */
    public void put(String key, String contentType, long sizeBytes) {
        objects.put(key, new Entry(new StoredObject(contentType, sizeBytes), PDF_HEADER));
    }

    /** Ídem con el contenido real del objeto: sirve para subir un archivo que declara ser PDF y no lo es. */
    public void put(String key, String contentType, byte[] content) {
        objects.put(key, new Entry(new StoredObject(contentType, content.length), content));
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
        return new PresignedUpload(URI.create("https://s3.test.invalid/" + key + "?X-Amz-Signature=test"),
                clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        return Optional.ofNullable(objects.get(key)).map(Entry::meta);
    }

    @Override
    public Optional<byte[]> readPrefix(String key, int length) {
        return Optional.ofNullable(objects.get(key))
                .map(entry -> Arrays.copyOf(entry.prefix(), Math.min(length, entry.prefix().length)));
    }
}
