package com.integrate.modules.catalog.meetingpoint;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Verificación del punto de encuentro (ADR-0030). Sin llaves ni secretos: el geocodificador es Nominatim de
 * OpenStreetMap, cuya política de uso exige un {@code User-Agent} propio y como máximo 1 solicitud por segundo.
 *
 * @param enabled       {@code false}: no se llama a ningún servicio externo y toda verificación termina en
 *                      {@code UNVERIFIED} (perfil de pruebas y entornos sin salida a internet)
 * @param async         {@code true}: la verificación posterior al envío corre en un hilo aparte; {@code false}
 *                      la ejecuta en el mismo hilo, tras la confirmación de la transacción (pruebas)
 * @param matchMeters   hasta esta distancia las coordenadas coinciden con la dirección (MATCH)
 * @param reviewMeters  hasta esta distancia (y por encima de {@code matchMeters}) el punto queda en REVIEW
 * @param textThreshold similitud mínima (Jaccard) entre el texto y el nombre del lugar cercano
 */
@ConfigurationProperties("integrate.catalog.meeting-point")
public record MeetingPointProperties(boolean enabled, boolean async, String baseUrl, String userAgent,
                                     Duration timeout, Duration minInterval, int cacheSize, Duration cacheTtl,
                                     int matchMeters, int reviewMeters, double textThreshold) {

    public MeetingPointProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://nominatim.openstreetmap.org" : baseUrl;
        userAgent = userAgent == null || userAgent.isBlank()
                ? "integrate-meeting-point-check/1.0 (proyecto academico)" : userAgent;
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        minInterval = minInterval == null ? Duration.ofMillis(1100) : minInterval;
        cacheSize = cacheSize <= 0 ? 500 : cacheSize;
        cacheTtl = cacheTtl == null ? Duration.ofHours(24) : cacheTtl;
        matchMeters = matchMeters <= 0 ? 250 : matchMeters;
        reviewMeters = reviewMeters <= 0 ? 1000 : reviewMeters;
        textThreshold = textThreshold <= 0 ? 0.5 : textThreshold;
        if (reviewMeters < matchMeters) {
            throw new IllegalStateException("integrate.catalog.meeting-point.review-meters must be >= match-meters");
        }
    }
}
