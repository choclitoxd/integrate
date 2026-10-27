package com.integrate.modules.catalog.meetingpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Funciones puras del algoritmo (ADR-0030 §1): Haversine, normalización y similitud de textos. */
class MeetingPointTextAndMathTest {

    @Test
    @DisplayName("Haversine: las coordenadas viejas de la demo estaban a unos 906 m de la Plaza de Bolívar")
    void haversineMatchesTheKnownDemoError() {
        double meters = GeoMath.haversineMeters(4.533889, -75.681111, 4.5325725, -75.673041);
        assertThat(meters).isCloseTo(906, within(10.0));
    }

    @Test
    @DisplayName("Haversine: mismo punto = 0 y un grado de latitud ≈ 111 km")
    void haversineBasics() {
        assertThat(GeoMath.haversineMeters(6.25, -75.56, 6.25, -75.56)).isZero();
        assertThat(GeoMath.haversineMeters(0, 0, 1, 0)).isCloseTo(111_195, within(200.0));
    }

    @Test
    @DisplayName("normaliza: minúsculas, sin tildes ni signos, espacios simples")
    void normalizes() {
        assertThat(MeetingPointText.normalize("  Plaza de Bolívar,  ARMENIA! ")).isEqualTo("plaza de bolivar armenia");
        assertThat(MeetingPointText.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("términos: sin palabras vacías")
    void termsDropStopWords() {
        assertThat(MeetingPointText.terms("Plaza de la Constitución en Armenia"))
                .containsExactlyInAnyOrder("plaza", "constitucion", "armenia");
    }

    @Test
    @DisplayName("Jaccard: idénticos = 1, sin nada en común = 0, vacío = 0, parcial entre 0 y 1")
    void jaccard() {
        assertThat(MeetingPointText.jaccard("Plaza de Bolívar", "plaza bolivar")).isEqualTo(1.0);
        assertThat(MeetingPointText.jaccard("Plaza de Bolívar", "Terminal de Transportes")).isZero();
        assertThat(MeetingPointText.jaccard("", "plaza")).isZero();
        assertThat(MeetingPointText.jaccard("Plaza de Bolívar", "Plaza Sucre")).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    @DisplayName("quita del texto los términos del municipio para comparar solo el lugar")
    void removesMunicipalityTerms() {
        assertThat(MeetingPointText.withoutTerms("Plaza de Bolívar, Armenia", "Armenia Quindío Colombia"))
                .isEqualTo("plaza bolivar");
    }
}
