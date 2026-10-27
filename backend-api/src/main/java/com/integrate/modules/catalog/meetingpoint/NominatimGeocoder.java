package com.integrate.modules.catalog.meetingpoint;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Geocodificador sobre Nominatim de OpenStreetMap (ADR-0030 §1). Cumple su política de uso: {@code User-Agent}
 * propio, como máximo una solicitud por intervalo ({@code min-interval}, 1,1 s por defecto), tiempo límite corto
 * y caché por texto para no repetir consultas. No usa llaves ni envía datos personales: solo el texto del lugar.
 */
@Component
public class NominatimGeocoder implements GeocodingPort {

    private final MeetingPointProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    private final HttpClient http;
    private final Object gate = new Object();
    private long lastCallNanos = System.nanoTime() - 10_000_000_000L;
    private final Map<String, Cached> cache;

    private record Cached(Instant at, Object value) {
    }

    public NominatimGeocoder(MeetingPointProperties properties, JsonMapper json, Clock clock) {
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        int max = properties.cacheSize();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > max;
            }
        });
    }

    @Override
    public List<GeoHit> search(String query) {
        String key = "s:" + MeetingPointText.normalize(query);
        List<GeoHit> cached = fromCache(key);
        if (cached != null) {
            return cached;
        }
        JsonNode body = get("/search?format=jsonv2&limit=3&countrycodes=co&q=" + encode(query));
        List<GeoHit> hits = new ArrayList<>();
        for (JsonNode node : body) {
            hit(node).ifPresent(hits::add);
        }
        List<GeoHit> result = List.copyOf(hits);
        cache.put(key, new Cached(clock.instant(), result));
        return result;
    }

    @Override
    public Optional<GeoHit> reverse(double latitude, double longitude) {
        String key = "r:" + String.format(java.util.Locale.ROOT, "%.5f,%.5f", latitude, longitude);
        Optional<GeoHit> cached = fromCache(key);
        if (cached != null) {
            return cached;
        }
        JsonNode body = get(String.format(java.util.Locale.ROOT, "/reverse?format=jsonv2&zoom=18&lat=%.6f&lon=%.6f",
                latitude, longitude));
        Optional<GeoHit> result = body.has("error") ? Optional.empty() : hit(body);
        cache.put(key, new Cached(clock.instant(), result));
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T fromCache(String key) {
        Cached cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.at().plus(properties.cacheTtl()).isBefore(clock.instant())) {
            cache.remove(key);
            return null;
        }
        return (T) cached.value();
    }

    private Optional<GeoHit> hit(JsonNode node) {
        if (!node.hasNonNull("lat") || !node.hasNonNull("lon")) {
            return Optional.empty();
        }
        Bounds bounds = null;
        JsonNode box = node.path("boundingbox");
        if (box.isArray() && box.size() == 4) {
            bounds = new Bounds(box.get(0).asDouble(), box.get(1).asDouble(), box.get(2).asDouble(),
                    box.get(3).asDouble());
        }
        return Optional.of(new GeoHit(node.get("lat").asDouble(), node.get("lon").asDouble(),
                node.path("name").asString(""), node.path("display_name").asString(""), bounds));
    }

    private JsonNode get(String pathAndQuery) {
        awaitTurn();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + pathAndQuery))
                .timeout(properties.timeout())
                .header("User-Agent", properties.userAgent())
                .header("Accept", "application/json")
                .header("Accept-Language", "es")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new GeocodingUnavailableException("Nominatim answered HTTP " + response.statusCode(), null);
            }
            return json.readTree(response.body());
        } catch (IOException | JacksonException e) {
            throw new GeocodingUnavailableException("Nominatim request failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodingUnavailableException("Nominatim request interrupted", e);
        }
    }

    /** Política de Nominatim: espacia las solicitudes (una por intervalo), de forma global para este proceso. */
    private void awaitTurn() {
        synchronized (gate) {
            long wait = properties.minInterval().toNanos() - (System.nanoTime() - lastCallNanos);
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GeocodingUnavailableException("Interrupted while waiting for the rate limit", e);
                }
            }
            lastCallNanos = System.nanoTime();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
