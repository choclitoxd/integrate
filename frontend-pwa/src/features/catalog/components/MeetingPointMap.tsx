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

/**
 * Mapa del punto de encuentro con su marcador (Google Maps, modo de incrustación sin llave de API
 * ni librería). Se carga diferido: no cuesta datos hasta que la sección entra en pantalla. Sin
 * conexión el marco queda vacío; el botón "Abrir en Google Maps" de al lado sigue disponible
 * (RNF-04).
 */
export function MeetingPointMap({ latitude, longitude, title }: MeetingPointMapProps) {
  if (!inRange(latitude, longitude)) return null;

  const src = `https://maps.google.com/maps?q=${encodeURIComponent(`${String(latitude)},${String(longitude)}`)}&z=15&hl=es&output=embed`;

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
