interface MeetingPointMapProps {
  readonly latitude: number;
  readonly longitude: number;
  /** Nombre de la experiencia, para el título accesible del mapa. */
  readonly title: string;
}

/** El contrato admite coordenadas nulas pero no valida el rango; un valor fuera de rango no se dibuja. */
function inRange(latitude: number, longitude: number): boolean {
  return Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
}

/** Medio lado de la ventana del mapa, en grados (~550 m): se ve el pueblo, no solo una esquina. */
const HALF_SPAN = 0.005;

/**
 * Mapa del punto de encuentro con su marcador (OpenStreetMap, modo de incrustación sin llave de API
 * ni librería). Antes usaba la incrustación sin llave de Google Maps, que Google dejó de servir
 * (responde 404 con `X-Frame-Options: SAMEORIGIN`). Se carga diferido: no cuesta datos hasta que la
 * sección entra en pantalla. Sin conexión el marco queda vacío; el botón "Abrir en Google Maps" de
 * al lado sigue disponible (RNF-04).
 */
export function MeetingPointMap({ latitude, longitude, title }: MeetingPointMapProps) {
  if (!inRange(latitude, longitude)) return null;

  const bbox = [
    longitude - HALF_SPAN,
    latitude - HALF_SPAN,
    longitude + HALF_SPAN,
    latitude + HALF_SPAN,
  ]
    .map((value) => value.toFixed(6))
    .join(',');
  const src = `https://www.openstreetmap.org/export/embed.html?bbox=${encodeURIComponent(bbox)}&layer=mapnik&marker=${encodeURIComponent(`${String(latitude)},${String(longitude)}`)}`;

  return (
    <div className="mt-4 overflow-hidden rounded-lg border border-border-subtle bg-canvas">
      <iframe
        title={`Mapa del punto de encuentro de ${title}`}
        src={src}
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        sandbox="allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox"
        className="block h-64 w-full border-0 lg:h-80"
      />
    </div>
  );
}
