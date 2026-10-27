package com.integrate.modules.catalog.meetingpoint;

/**
 * Eventos del autómata (ADR-0030 §1b). Los resultados de la verificación llegan ya combinados: el
 * verificador decide la distancia y la similitud del texto, y el autómata solo ve un evento.
 */
public enum MeetingPointEvent {

    /** Se envió a revisión o se cambió el punto de encuentro. */
    ENVIAR,
    /** El anfitrión pidió repetir la verificación. */
    REINTENTAR,
    /** Distancia de hasta 250 m. */
    DIST_OK,
    /** Distancia media (250 m a 1 000 m), sin señal del texto. */
    DIST_MEDIA,
    /** Distancia media y el nombre del lugar cercano coincide con el texto (Jaccard ≥ 0,5). */
    DIST_MEDIA_TEXTO_COINCIDE,
    /** Distancia media y el nombre del lugar cercano no comparte ninguna palabra con el texto. */
    DIST_MEDIA_TEXTO_NO_COINCIDE,
    /** Distancia alta (más de 1 000 m) o coordenadas fuera del municipio. */
    DIST_ALTA,
    /** Sin coordenadas, sin coincidencia o servicio externo caído. */
    SIN_RESPUESTA,
    /** El administrador aceptó el punto dudoso. */
    CONFIRMAR,
    /** El anfitrión cambió las coordenadas de un punto dudoso o confirmado. */
    EDITAR_COORDENADAS
}
