package com.integrate.common.audit;

import java.net.InetAddress;
import java.util.Collection;
import java.util.UUID;

/**
 * Rastro de los accesos a datos personales o sensibles en claro (ADR-0035 §2, Ley 1581 art. 8). Todo servicio que
 * descifre un dato sensible para entregarlo debe registrar aquí quién, para qué, qué campos y de quiénes; una prueba de
 * arquitectura falla si llama a {@code SensitiveDataCipher.decrypt} sin depender de este puerto. Exige una
 * transacción abierta y, como {@link AuditTrail}, debe ser de lo último que ocurre en ella.
 */
public interface SensitiveAccessRecorder {

    /**
     * @param module     módulo que entrega el dato ({@code ck_audit_trail_module})
     * @param entityName tipo de la entidad que identifica el acceso (p. ej. el dispositivo que descarga)
     * @param entityId   identificador de esa entidad
     * @param purpose    finalidad del acceso (p. ej. {@code OFFLINE_ROSTER})
     * @param fields     nombres de los campos entregados; nunca sus valores
     * @param subjects   identificadores de los titulares afectados (p. ej. reservas)
     */
    void record(Access access);

    record Access(String module, String entityName, String entityId, String purpose, Collection<String> fields,
                  Collection<String> subjects, UUID actorId, InetAddress actorIp, String userAgent) {
    }
}