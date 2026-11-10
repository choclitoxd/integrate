package com.integrate.modules.catalog.service.model;

import java.math.BigDecimal;

/**
 * Punto de encuentro operativo de una franja, exacto y sin redondear (ADR-0046 §1, ADR-0039). Solo se entrega por
 * consulta autorizada: nunca por la proyección pública del catálogo.
 *
 * @param latitude           {@code null} si el punto dudoso o sin coordenadas
 * @param coordinatesTrusted {@code true} solo si las coordenadas son fiables y están presentes
 */
public record OperationalLocation(String meetingPoint, BigDecimal latitude, BigDecimal longitude,
                                  boolean coordinatesTrusted) {
}
