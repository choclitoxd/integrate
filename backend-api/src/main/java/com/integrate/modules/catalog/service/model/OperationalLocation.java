package com.integrate.modules.catalog.service.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Punto de encuentro operativo de una franja, exacto y sin redondear (ADR-0046 §1, ADR-0039), con las paradas del
 * recorrido (ADR-0049). Solo se entrega por consulta autorizada: nunca por la proyección pública del catálogo.
 *
 * @param latitude           {@code null} si el punto dudoso o sin coordenadas
 * @param coordinatesTrusted {@code true} solo si las coordenadas son fiables y están presentes
 * @param stops              paradas en orden de recorrido; vacía si la experiencia no define ninguna
 */
public record OperationalLocation(String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                  boolean coordinatesTrusted, List<Stop> stops) {

    /** Parada del recorrido; {@code description} es opcional. */
    public record Stop(int position, String name, String description) {
    }
}
