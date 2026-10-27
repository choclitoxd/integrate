package com.integrate.modules.catalog.meetingpoint;

import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.CONFIRMAR;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.DIST_ALTA;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.DIST_MEDIA;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.DIST_MEDIA_TEXTO_COINCIDE;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.DIST_MEDIA_TEXTO_NO_COINCIDE;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.DIST_OK;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.EDITAR_COORDENADAS;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.ENVIAR;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.REINTENTAR;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointEvent.SIN_RESPUESTA;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.CONFIRMADO;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.CORREGIDO;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.MATCH;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.MISMATCH;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.PENDIENTE;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.REVIEW;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.UNVERIFIED;
import static com.integrate.modules.catalog.meetingpoint.MeetingPointState.VERIFICANDO;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Máquina de estados finita y determinista del ciclo de verificación del punto de encuentro (ADR-0030 §1b),
 * en el mismo estilo que el autómata del mod portero: una tabla explícita de transiciones, sin lógica
 * dispersa. Es el único lugar donde se decide el estado siguiente; el servicio y los controladores no lo
 * cambian a mano. Lo que no está en la tabla lanza {@link InvalidMeetingPointTransitionException}.
 */
public final class MeetingPointAutomaton {

    /** Estado actual × evento → estado siguiente. */
    private static final Map<MeetingPointState, Map<MeetingPointEvent, MeetingPointState>> TABLE = build();

    private MeetingPointAutomaton() {
    }

    public static MeetingPointState next(MeetingPointState from, MeetingPointEvent event) {
        return find(from, event).orElseThrow(() -> new InvalidMeetingPointTransitionException(from, event));
    }

    public static Optional<MeetingPointState> find(MeetingPointState from, MeetingPointEvent event) {
        return Optional.ofNullable(TABLE.get(from).get(event));
    }

    /** La tabla completa, de solo lectura (la recorren las pruebas). */
    public static Map<MeetingPointState, Map<MeetingPointEvent, MeetingPointState>> table() {
        return TABLE;
    }

    private static Map<MeetingPointState, Map<MeetingPointEvent, MeetingPointState>> build() {
        Map<MeetingPointState, Map<MeetingPointEvent, MeetingPointState>> table = new EnumMap<>(MeetingPointState.class);
        for (MeetingPointState state : MeetingPointState.values()) {
            table.put(state, new EnumMap<>(MeetingPointEvent.class));
        }

        table.get(PENDIENTE).put(ENVIAR, VERIFICANDO);

        Map<MeetingPointEvent, MeetingPointState> verifying = table.get(VERIFICANDO);
        verifying.put(DIST_OK, MATCH);
        verifying.put(DIST_MEDIA_TEXTO_COINCIDE, MATCH);
        verifying.put(DIST_MEDIA, REVIEW);
        verifying.put(DIST_MEDIA_TEXTO_NO_COINCIDE, MISMATCH);
        verifying.put(DIST_ALTA, MISMATCH);
        verifying.put(SIN_RESPUESTA, UNVERIFIED);
        // Fuera de la tabla del ADR: evita quedar atascado si una verificación se pierde (reinicio del servidor).
        verifying.put(REINTENTAR, VERIFICANDO);

        for (MeetingPointState settled : new MeetingPointState[] {MATCH, REVIEW, MISMATCH, UNVERIFIED}) {
            table.get(settled).put(REINTENTAR, VERIFICANDO);
            table.get(settled).put(ENVIAR, VERIFICANDO);
        }

        for (MeetingPointState doubtful : new MeetingPointState[] {REVIEW, MISMATCH}) {
            table.get(doubtful).put(CONFIRMAR, CONFIRMADO);
            table.get(doubtful).put(EDITAR_COORDENADAS, CORREGIDO);
        }
        table.get(CONFIRMADO).put(EDITAR_COORDENADAS, CORREGIDO);

        table.get(CORREGIDO).put(ENVIAR, VERIFICANDO);

        table.replaceAll((state, row) -> Collections.unmodifiableMap(row));
        return Collections.unmodifiableMap(table);
    }
}
