interface MeetingPointMapProps {
  readonly latitude: number;
  readonly longitude: number;
  /** Nombre de la experiencia, para el título accesible del mapa. */
  readonly title: string;
  /**
   * Dirección escrita del punto de encuentro. Si se da, las coordenadas no coinciden con ella (ADR-0030 §5) y el
   * mapa se centra por la dirección: solo la Embed API de Google busca por texto; sin llave no se dibuja mapa.
   */
  readonly addressQuery?: string;
}

/** El contrato admite coordenadas nulas pero no valida el rango; un valor fuera de rango no se dibuja. */
function inRange(latitude: number, longitude: number): boolean {
  return Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
}

/** Medio lado de la ventana del mapa de OpenStreetMap, en grados (~550 m). */
const HALF_SPAN = 0.005;

/**
 * Dirección del marco: Google Maps (Embed API) si el despliegue trae una llave pública en
 * `VITE_GOOGLE_MAPS_EMBED_KEY` (restringida por dominio en Google Cloud); si no, OpenStreetMap, que no
 * pide llave. La incrustación antigua de Google sin llave dejó de servirse (404 con
 * `X-Frame-Options: SAMEORIGIN`).
 */
function embedSrc(
  latitude: number,
  longitude: number,
  addressQuery: string | undefined,
): { src: string; sandbox: string | undefined } | null {
  const point = addressQuery ?? `${String(latitude)},${String(longitude)}`;
  const key = import.meta.env.VITE_GOOGLE_MAPS_EMBED_KEY?.trim();
  if (key) {
    // La Embed API de Google no dibuja dentro de un marco con `sandbox` (se probó con allow-forms,
    // allow-modals y allow-presentation): es su propio dominio y la llave va atada al dominio del sitio.
    return {
      src: `https://www.google.com/maps/embed/v1/place?key=${encodeURIComponent(key)}&q=${encodeURIComponent(point)}&zoom=15&language=es`,
      sandbox: undefined,
    };
  }
  // OpenStreetMap no busca por texto: con coordenadas dudosas es mejor no dibujar un marcador equivocado.
  if (addressQuery !== undefined) return null;
  const bbox = [
    longitude - HALF_SPAN,
    latitude - HALF_SPAN,
    longitude + HALF_SPAN,
    latitude + HALF_SPAN,
  ]
    .map((value) => value.toFixed(6))
    .join(',');
  return {
    src: `https://www.openstreetmap.org/export/embed.html?bbox=${encodeURIComponent(bbox)}&layer=mapnik&marker=${encodeURIComponent(point)}`,
    sandbox: 'allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox',
  };
}

/**
 * Mapa del punto de encuentro con su marcador, sin librería. Se carga diferido: no cuesta datos hasta
 * que la sección entra en pantalla. Sin conexión el marco queda vacío; el botón "Abrir en Google
 * Maps" de al lado sigue disponible (RNF-04).
 */
export function MeetingPointMap({
  latitude,
  longitude,
  title,
  addressQuery,
}: MeetingPointMapProps) {
  if (!inRange(latitude, longitude)) return null;

  const embed = embedSrc(latitude, longitude, addressQuery);
  if (embed === null) return null;
  const { src, sandbox } = embed;
  return (
    <div className="mt-4 overflow-hidden rounded-lg border border-border-subtle bg-canvas">
      <iframe
        title={`Mapa del punto de encuentro de ${title}`}
        src={src}
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        sandbox={sandbox}
        className="block h-64 w-full border-0 lg:h-80"
      />
    </div>
  );
}
