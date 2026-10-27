package com.integrate.modules.catalog.meetingpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Tabla de transiciones del autómata (ADR-0030 §1b), celda por celda y con sus propiedades de seguridad. */
@DisplayName("Autómata del punto de encuentro (ADR-0030)")
class MeetingPointAutomatonTest {

    private record Row(MeetingPointState from, MeetingPointEvent event, MeetingPointState to) {
    }

    /** La tabla del ADR, escrita aparte de la implementación para que la prueba no sea un espejo del código. */
    private static List<Row> expected() {
        List<Row> rows = new ArrayList<>();
        add(rows, "PENDIENTE", "ENVIAR", "VERIFICANDO");
        add(rows, "VERIFICANDO", "DIST_OK", "MATCH");
        add(rows, "VERIFICANDO", "DIST_MEDIA_TEXTO_COINCIDE", "MATCH");
        add(rows, "VERIFICANDO", "DIST_MEDIA", "REVIEW");
        add(rows, "VERIFICANDO", "DIST_MEDIA_TEXTO_NO_COINCIDE", "MISMATCH");
        add(rows, "VERIFICANDO", "DIST_ALTA", "MISMATCH");
        add(rows, "VERIFICANDO", "SIN_RESPUESTA", "UNVERIFIED");
        add(rows, "VERIFICANDO", "REINTENTAR", "VERIFICANDO");
        for (String settled : List.of("MATCH", "REVIEW", "MISMATCH", "UNVERIFIED")) {
            add(rows, settled, "REINTENTAR", "VERIFICANDO");
            add(rows, settled, "ENVIAR", "VERIFICANDO");
        }
        for (String doubtful : List.of("REVIEW", "MISMATCH")) {
            add(rows, doubtful, "CONFIRMAR", "CONFIRMADO");
            add(rows, doubtful, "EDITAR_COORDENADAS", "CORREGIDO");
        }
        add(rows, "CONFIRMADO", "EDITAR_COORDENADAS", "CORREGIDO");
        add(rows, "CORREGIDO", "ENVIAR", "VERIFICANDO");
        return rows;
    }

    private static void add(List<Row> rows, String from, String event, String to) {
        rows.add(new Row(MeetingPointState.valueOf(from), MeetingPointEvent.valueOf(event),
                MeetingPointState.valueOf(to)));
    }

    static List<Row> listedTransitions() {
        return expected();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listedTransitions")
    @DisplayName("cada par listado produce el estado indicado")
    void listedTransitionsProduceTheExpectedState(Row row) {
        assertThat(MeetingPointAutomaton.next(row.from(), row.event())).isEqualTo(row.to());
    }

    @Test
    @DisplayName("cada par no listado se rechaza")
    void unlistedPairsAreRejected() {
        Set<String> listed = new java.util.HashSet<>();
        expected().forEach(r -> listed.add(r.from() + "|" + r.event()));
        int rejected = 0;
        for (MeetingPointState state : MeetingPointState.values()) {
            for (MeetingPointEvent event : MeetingPointEvent.values()) {
                if (listed.contains(state + "|" + event)) {
                    continue;
                }
                assertThatThrownBy(() -> MeetingPointAutomaton.next(state, event))
                        .as("%s + %s", state, event)
                        .isInstanceOf(InvalidMeetingPointTransitionException.class);
                rejected++;
            }
        }
        assertThat(rejected).isEqualTo(MeetingPointState.values().length * MeetingPointEvent.values().length
                - listed.size());
    }

    @Test
    @DisplayName("la tabla del autómata no tiene filas fuera de la tabla del ADR")
    void tableHasNoExtraRows() {
        int rows = MeetingPointAutomaton.table().values().stream().mapToInt(Map::size).sum();
        assertThat(rows).isEqualTo(expected().size());
    }

    @Test
    @DisplayName("un punto dudoso solo pasa a aprobable por CONFIRMAR; el resto exige verificar de nuevo")
    void doubtfulPointNeverBecomesApprovableWithoutConfirmation() {
        for (MeetingPointState doubtful : EnumSet.of(MeetingPointState.REVIEW, MeetingPointState.MISMATCH)) {
            for (MeetingPointEvent event : MeetingPointEvent.values()) {
                MeetingPointAutomaton.find(doubtful, event).ifPresent(next -> {
                    if (event == MeetingPointEvent.CONFIRMAR) {
                        assertThat(next.approvable()).isTrue();
                    } else if (doubtful == MeetingPointState.MISMATCH) {
                        assertThat(next.approvable()).as("%s + %s", doubtful, event).isFalse();
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("los únicos eventos que llevan a un estado aprobable son los resultados de VERIFICANDO y CONFIRMAR")
    void onlyVerificationResultsAndConfirmationReachApprovableStates() {
        for (MeetingPointState from : MeetingPointState.values()) {
            for (MeetingPointEvent event : MeetingPointEvent.values()) {
                MeetingPointAutomaton.find(from, event).filter(MeetingPointState::approvable).ifPresent(to -> {
                    boolean verificationResult = from == MeetingPointState.VERIFICANDO;
                    boolean confirmation = event == MeetingPointEvent.CONFIRMAR;
                    assertThat(verificationResult || confirmation).as("%s + %s -> %s", from, event, to).isTrue();
                });
            }
        }
    }

    @Test
    @DisplayName("editar las coordenadas de un punto dudoso o confirmado obliga a verificar de nuevo")
    void editingCoordinatesRequiresANewVerification() {
        for (MeetingPointState from : EnumSet.of(MeetingPointState.REVIEW, MeetingPointState.MISMATCH,
                MeetingPointState.CONFIRMADO)) {
            MeetingPointState corrected = MeetingPointAutomaton.next(from, MeetingPointEvent.EDITAR_COORDENADAS);
            assertThat(corrected).isEqualTo(MeetingPointState.CORREGIDO);
            assertThat(corrected.approvable()).isFalse();
            assertThat(MeetingPointAutomaton.next(corrected, MeetingPointEvent.ENVIAR))
                    .isEqualTo(MeetingPointState.VERIFICANDO);
        }
    }

    @Test
    @DisplayName("solo MATCH, REVIEW, UNVERIFIED y CONFIRMADO permiten aprobar")
    void approvableStatesAreExactlyTheFourOfTheAdr() {
        assertThat(EnumSet.allOf(MeetingPointState.class).stream().filter(MeetingPointState::approvable).toList())
                .containsExactlyInAnyOrder(MeetingPointState.MATCH, MeetingPointState.REVIEW,
                        MeetingPointState.UNVERIFIED, MeetingPointState.CONFIRMADO);
    }

    @Test
    @DisplayName("con REVIEW o MISMATCH las coordenadas no son de fiar para el mapa público")
    void coordinatesAreNotTrustedWhenDoubtful() {
        assertThat(MeetingPointState.REVIEW.coordinatesTrusted()).isFalse();
        assertThat(MeetingPointState.MISMATCH.coordinatesTrusted()).isFalse();
        assertThat(MeetingPointState.MATCH.coordinatesTrusted()).isTrue();
        assertThat(MeetingPointState.CONFIRMADO.coordinatesTrusted()).isTrue();
        assertThat(MeetingPointState.UNVERIFIED.coordinatesTrusted()).isTrue();
    }
}
