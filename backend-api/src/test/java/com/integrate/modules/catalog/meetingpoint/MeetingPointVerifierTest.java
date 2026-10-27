package com.integrate.modules.catalog.meetingpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrate.modules.catalog.meetingpoint.GeocodingPort.Bounds;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort.GeoHit;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort.GeocodingUnavailableException;
import com.integrate.modules.catalog.meetingpoint.MeetingPointVerifier.Verification;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Algoritmo de verificación (ADR-0030 §1) con un geocodificador simulado: sin red. */
class MeetingPointVerifierTest {

    private static final String TEXT = "Plaza de Bolívar, Armenia";
    /** Caja aproximada del municipio de Armenia (Quindío). */
    private static final Bounds ARMENIA = new Bounds(4.45, 4.58, -75.75, -75.62);
    private static final GeoHit AREA = new GeoHit(4.5339, -75.6811, "Armenia", "Armenia, Quindío, Colombia", ARMENIA);
    /** Dónde OpenStreetMap ubica la plaza. */
    private static final GeoHit PLAZA = new GeoHit(4.5325725, -75.673041, "Plaza de Bolívar",
            "Plaza de Bolívar, Centro, Armenia", null);

    private static final BigDecimal OLD_LAT = new BigDecimal("4.533889");
    private static final BigDecimal OLD_LNG = new BigDecimal("-75.681111");

    private static final MeetingPointProperties ON = props(true);

    private static MeetingPointProperties props(boolean enabled) {
        return new MeetingPointProperties(enabled, false, null, null, null, Duration.ZERO, 0, null, 0, 0, 0.0);
    }

    /** Geocodificador simulado: la consulta del municipio devuelve {@code area}; la de la dirección, {@code address}. */
    private static final class FakeGeocoder implements GeocodingPort {
        List<GeoHit> area = List.of(AREA);
        List<GeoHit> address = List.of(PLAZA);
        Optional<GeoHit> reverse = Optional.empty();
        boolean down;
        int calls;

        @Override
        public List<GeoHit> search(String query) {
            calls++;
            if (down) {
                throw new GeocodingUnavailableException("down", null);
            }
            return query.startsWith("Armenia,") ? area : address;
        }

        @Override
        public Optional<GeoHit> reverse(double latitude, double longitude) {
            calls++;
            if (down) {
                throw new GeocodingUnavailableException("down", null);
            }
            return reverse;
        }
    }

    private static Verification run(FakeGeocoder geocoder, MeetingPointProperties props, BigDecimal lat, BigDecimal lng) {
        return new MeetingPointVerifier(geocoder, props).verify(TEXT, lat, lng, "Armenia", "Quindío");
    }

    @Test
    @DisplayName("coordenadas correctas → DIST_OK, sin sugerencia")
    void exactCoordinatesMatch() {
        Verification v = run(new FakeGeocoder(), ON, new BigDecimal("4.532573"), new BigDecimal("-75.673041"));
        assertThat(v.event()).isEqualTo(MeetingPointEvent.DIST_OK);
        assertThat(v.distanceMeters()).isLessThanOrEqualTo(5);
        assertThat(v.suggestedLatitude()).isNull();
    }

    @Test
    @DisplayName("hasta 250 m sigue siendo MATCH")
    void within250MetersMatches() {
        // ≈ 0,002° de longitud a esta latitud ≈ 222 m.
        Verification v = run(new FakeGeocoder(), ON, new BigDecimal("4.532573"), new BigDecimal("-75.675041"));
        assertThat(v.event()).isEqualTo(MeetingPointEvent.DIST_OK);
        assertThat(v.distanceMeters()).isBetween(200, 250);
    }

    @Test
    @DisplayName("caso real de la demo: 906 m → distancia media y se sugieren las coordenadas de la plaza")
    void theRealDemoCaseIsMediumAndSuggestsTheRightPoint() {
        Verification v = run(new FakeGeocoder(), ON, OLD_LAT, OLD_LNG);
        assertThat(v.event()).isEqualTo(MeetingPointEvent.DIST_MEDIA);
        assertThat(v.distanceMeters()).isBetween(890, 920);
        assertThat(v.suggestedLatitude()).isEqualByComparingTo("4.532573");
        assertThat(v.suggestedLongitude()).isEqualByComparingTo("-75.673041");
    }

    @Test
    @DisplayName("distancia media y el lugar cercano se llama igual que el texto → coincide")
    void mediumDistanceWithMatchingNameCoincides() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.reverse = Optional.of(new GeoHit(4.5339, -75.6811, "Plaza Bolívar", "Plaza Bolívar, Armenia", null));
        assertThat(run(geocoder, ON, OLD_LAT, OLD_LNG).event()).isEqualTo(MeetingPointEvent.DIST_MEDIA_TEXTO_COINCIDE);
    }

    @Test
    @DisplayName("distancia media y el lugar cercano no comparte palabras → no coincide")
    void mediumDistanceWithUnrelatedNameDoesNotCoincide() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.reverse = Optional.of(new GeoHit(4.5339, -75.6811, "Terminal de Transportes", "Terminal, Armenia", null));
        assertThat(run(geocoder, ON, OLD_LAT, OLD_LNG).event())
                .isEqualTo(MeetingPointEvent.DIST_MEDIA_TEXTO_NO_COINCIDE);
    }

    @Test
    @DisplayName("distancia media y similitud parcial → solo distancia media")
    void mediumDistanceWithPartialSimilarityStaysMedium() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.reverse = Optional.of(new GeoHit(4.5339, -75.6811, "Plaza Sucre", "Plaza Sucre, Armenia", null));
        assertThat(run(geocoder, ON, OLD_LAT, OLD_LNG).event()).isEqualTo(MeetingPointEvent.DIST_MEDIA);
    }

    @Test
    @DisplayName("más de 1 000 m → DIST_ALTA")
    void farAwayIsHigh() {
        Verification v = run(new FakeGeocoder(), ON, new BigDecimal("4.5100"), new BigDecimal("-75.6800"));
        assertThat(v.event()).isEqualTo(MeetingPointEvent.DIST_ALTA);
        assertThat(v.distanceMeters()).isGreaterThan(1000);
        assertThat(v.suggestedLatitude()).isNotNull();
    }

    @Test
    @DisplayName("coordenadas fuera del municipio → DIST_ALTA aunque no haya coincidencia de la dirección")
    void outsideTheMunicipalityIsHigh() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.address = List.of();
        Verification v = run(geocoder, ON, new BigDecimal("6.25"), new BigDecimal("-75.56"));
        assertThat(v.event()).isEqualTo(MeetingPointEvent.DIST_ALTA);
        assertThat(v.distanceMeters()).isNull();
    }

    @Test
    @DisplayName("una coincidencia fuera del municipio se descarta")
    void hitsOutsideTheMunicipalityAreDiscarded() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.address = List.of(new GeoHit(6.25, -75.56, "Plaza de Bolívar", "Plaza de Bolívar, Medellín", null));
        assertThat(run(geocoder, ON, new BigDecimal("4.532573"), new BigDecimal("-75.673041")).event())
                .isEqualTo(MeetingPointEvent.SIN_RESPUESTA);
    }

    @Test
    @DisplayName("sin coincidencias → SIN_RESPUESTA")
    void noHitsIsNoResponse() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.address = List.of();
        assertThat(run(geocoder, ON, new BigDecimal("4.532573"), new BigDecimal("-75.673041")).event())
                .isEqualTo(MeetingPointEvent.SIN_RESPUESTA);
    }

    @Test
    @DisplayName("servicio caído → SIN_RESPUESTA, sin lanzar la excepción")
    void serviceDownIsNoResponse() {
        FakeGeocoder geocoder = new FakeGeocoder();
        geocoder.down = true;
        assertThat(run(geocoder, ON, OLD_LAT, OLD_LNG).event()).isEqualTo(MeetingPointEvent.SIN_RESPUESTA);
    }

    @Test
    @DisplayName("sin coordenadas, o con la verificación apagada, no llama al servicio")
    void noCoordinatesOrDisabledDoesNotCallTheService() {
        FakeGeocoder geocoder = new FakeGeocoder();
        assertThat(run(geocoder, ON, null, null).event()).isEqualTo(MeetingPointEvent.SIN_RESPUESTA);
        assertThat(run(geocoder, props(false), OLD_LAT, OLD_LNG).event()).isEqualTo(MeetingPointEvent.SIN_RESPUESTA);
        assertThat(geocoder.calls).isZero();
    }

    @Test
    @DisplayName("los umbrales son configurables")
    void thresholdsAreConfigurable() {
        MeetingPointProperties strict = new MeetingPointProperties(true, false, null, null, null, Duration.ZERO, 0, null,
                50, 100, 0.0);
        assertThat(run(new FakeGeocoder(), strict, new BigDecimal("4.532573"), new BigDecimal("-75.675041")).event())
                .isEqualTo(MeetingPointEvent.DIST_ALTA);
    }
}
