package com.integrate.modules.catalog.meetingpoint;

import com.integrate.modules.catalog.meetingpoint.GeocodingPort.Bounds;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort.GeoHit;
import com.integrate.modules.catalog.meetingpoint.GeocodingPort.GeocodingUnavailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Algoritmo de verificación del punto de encuentro (ADR-0030 §1): geocodifica la dirección escrita, mide la
 * distancia a las coordenadas declaradas y devuelve <b>un evento</b> para el autómata. No decide estados ni
 * toca la base de datos; solo produce el evento y, si hay diferencia, unas coordenadas sugeridas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingPointVerifier {

    /** Margen (≈ 1 km) alrededor de la caja del municipio: los linderos de OpenStreetMap son aproximados. */
    private static final double MUNICIPALITY_MARGIN_DEGREES = 0.01;

    private final GeocodingPort geocoder;
    private final MeetingPointProperties properties;

    /**
     * @param event               evento para el autómata
     * @param distanceMeters      distancia entre lo declarado y la dirección geocodificada; nula si no hubo resultado
     * @param suggestedLatitude   coordenadas sugeridas (solo si hay diferencia); nunca se aplican solas
     * @param suggestedLongitude  ver {@code suggestedLatitude}
     */
    public record Verification(MeetingPointEvent event, Integer distanceMeters, BigDecimal suggestedLatitude,
                               BigDecimal suggestedLongitude) {

        static Verification noResult() {
            return new Verification(MeetingPointEvent.SIN_RESPUESTA, null, null, null);
        }
    }

    public Verification verify(String meetingPoint, BigDecimal latitude, BigDecimal longitude, String municipality,
                               String department) {
        if (!properties.enabled() || latitude == null || longitude == null || meetingPoint == null
                || meetingPoint.isBlank()) {
            return Verification.noResult();
        }
        double declaredLat = latitude.doubleValue();
        double declaredLon = longitude.doubleValue();
        try {
            Optional<Bounds> area = geocoder.search(municipality + ", " + department + ", Colombia").stream()
                    .findFirst().map(GeoHit::bounds);
            boolean outsideMunicipality = area.filter(b -> !b.contains(declaredLat, declaredLon,
                    MUNICIPALITY_MARGIN_DEGREES)).isPresent();

            List<GeoHit> hits = geocoder.search(meetingPoint + ", " + municipality + ", " + department + ", Colombia");
            Optional<GeoHit> best = hits.stream()
                    .filter(h -> area.isEmpty() || area.get().contains(h.latitude(), h.longitude(),
                            MUNICIPALITY_MARGIN_DEGREES))
                    .findFirst();
            if (best.isEmpty()) {
                return outsideMunicipality ? new Verification(MeetingPointEvent.DIST_ALTA, null, null, null)
                        : Verification.noResult();
            }

            GeoHit hit = best.get();
            int distance = (int) Math.round(GeoMath.haversineMeters(declaredLat, declaredLon, hit.latitude(),
                    hit.longitude()));
            if (!outsideMunicipality && distance <= properties.matchMeters()) {
                return new Verification(MeetingPointEvent.DIST_OK, distance, null, null);
            }
            BigDecimal suggestedLat = BigDecimal.valueOf(hit.latitude()).setScale(6, RoundingMode.HALF_UP);
            BigDecimal suggestedLon = BigDecimal.valueOf(hit.longitude()).setScale(6, RoundingMode.HALF_UP);
            MeetingPointEvent event = outsideMunicipality || distance > properties.reviewMeters()
                    ? MeetingPointEvent.DIST_ALTA
                    : mediumDistanceEvent(meetingPoint, declaredLat, declaredLon, municipality, department);
            return new Verification(event, distance, suggestedLat, suggestedLon);
        } catch (GeocodingUnavailableException e) {
            log.warn("Meeting point geocoding unavailable: {}", e.getMessage());
            return Verification.noResult();
        }
    }

    /** Segunda señal (inversa): el nombre del lugar más cercano a las coordenadas contra el texto escrito. */
    private MeetingPointEvent mediumDistanceEvent(String meetingPoint, double latitude, double longitude,
                                                  String municipality, String department) {
        Optional<GeoHit> nearby = geocoder.reverse(latitude, longitude);
        String placeName = nearby.map(h -> h.name() == null || h.name().isBlank() ? "" : h.name()).orElse("");
        String written = MeetingPointText.withoutTerms(meetingPoint, municipality + " " + department + " colombia");
        if (placeName.isBlank() || written.isBlank()) {
            return MeetingPointEvent.DIST_MEDIA;
        }
        double similarity = MeetingPointText.jaccard(written, placeName);
        if (similarity >= properties.textThreshold()) {
            return MeetingPointEvent.DIST_MEDIA_TEXTO_COINCIDE;
        }
        return similarity == 0.0 ? MeetingPointEvent.DIST_MEDIA_TEXTO_NO_COINCIDE : MeetingPointEvent.DIST_MEDIA;
    }
}
