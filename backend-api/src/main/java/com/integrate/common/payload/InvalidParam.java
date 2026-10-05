package com.integrate.common.payload;

/**
 * Error de validación por campo del contrato ({@code invalidParams[]} en ProblemDetails).
 *
 * @param name   ruta del campo con notación de punto o índice, p. ej. {@code participants[1].docNumber}
 * @param reason motivo legible del rechazo
 */
public record InvalidParam(String name, String reason) {
}
