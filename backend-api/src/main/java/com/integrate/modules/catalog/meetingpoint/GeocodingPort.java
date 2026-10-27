package com.integrate.modules.catalog.meetingpoint;

import java.util.List;
import java.util.Optional;

/**
 * Puerto del geocodificador (ADR-0030 §1): la implementación por defecto es Nominatim de OpenStreetMap, y las
 * pruebas usan una simulada. Si el servicio no responde, lanza {@link GeocodingUnavailableException}.
 */
public interface GeocodingPort {

    /** Mejores coincidencias de un texto, la más relevante primero (hasta 3). */
    List<GeoHit> search(String query);

    /** Lugar con nombre más cercano a unas coordenadas, si lo hay. */
    Optional<GeoHit> reverse(double latitude, double longitude);

    /**
     * @param name        nombre corto del lugar (puede estar vacío)
     * @param displayName dirección completa
     * @param bounds      caja del lugar; en el municipio sirve para saber si un punto cae dentro
     */
    record GeoHit(double latitude, double longitude, String name, String displayName, Bounds bounds) {
    }

    /** Caja geográfica en grados. */
    record Bounds(double south, double north, double west, double east) {

        /** Si el punto cae dentro de la caja, con un margen en grados (≈ 111 km por grado). */
        public boolean contains(double latitude, double longitude, double marginDegrees) {
            return latitude >= south - marginDegrees && latitude <= north + marginDegrees
                    && longitude >= west - marginDegrees && longitude <= east + marginDegrees;
        }
    }

    /** El servicio externo no respondió a tiempo o devolvió un error. */
    class GeocodingUnavailableException extends RuntimeException {

        public GeocodingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
