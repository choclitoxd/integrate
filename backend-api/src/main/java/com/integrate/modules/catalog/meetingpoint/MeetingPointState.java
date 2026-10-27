package com.integrate.modules.catalog.meetingpoint;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del autómata de verificación del punto de encuentro (ADR-0030 §1b). Se guardan como texto en
 * {@code catalog.experiences.meeting_point_check_status}.
 */
public enum MeetingPointState {

    /** Aún no se envió a revisión: no hay resultado. */
    PENDIENTE,
    /** Hay una verificación en curso (o pendiente de reintento). */
    VERIFICANDO,
    /** La dirección y las coordenadas coinciden. */
    MATCH,
    /** Diferencia moderada: el administrador la ve y puede aprobar. */
    REVIEW,
    /** Diferencia grande o coordenadas fuera del municipio: exige confirmación o corrección. */
    MISMATCH,
    /** No se pudo verificar (sin coordenadas, sin resultado o servicio caído): no bloquea. */
    UNVERIFIED,
    /** El administrador aceptó un punto dudoso. */
    CONFIRMADO,
    /** Se cambiaron las coordenadas: hay que verificar de nuevo. */
    CORREGIDO;

    private static final Set<MeetingPointState> APPROVABLE = EnumSet.of(MATCH, REVIEW, UNVERIFIED, CONFIRMADO);
    private static final Set<MeetingPointState> DOUBTFUL = EnumSet.of(REVIEW, MISMATCH);

    /** Solo desde estos estados puede aprobarse la experiencia (ADR-0030 §4). */
    public boolean approvable() {
        return APPROVABLE.contains(this);
    }

    /** Con {@code REVIEW} o {@code MISMATCH} el mapa público se centra por la dirección y no por las coordenadas. */
    public boolean coordinatesTrusted() {
        return !DOUBTFUL.contains(this);
    }
}
