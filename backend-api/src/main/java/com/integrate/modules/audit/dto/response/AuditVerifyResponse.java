package com.integrate.modules.audit.dto.response;

/**
 * Resultado de verificar la cadena de hashes (ADR-0035 §1).
 *
 * @param checked  filas revisadas del rango
 * @param brokenId primera fila rota, o {@code null} si la cadena es íntegra
 */
public record AuditVerifyResponse(boolean intact, long fromId, long toId, long checked, Long brokenId,
                                  String problem) {
}