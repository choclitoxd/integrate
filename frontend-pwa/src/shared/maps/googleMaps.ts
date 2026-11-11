/**
 * Carga perezosa de Google Maps JavaScript API (ADR-0056, Fase 1).
 * La llave es pública (VITE_GOOGLE_MAPS_BROWSER_KEY) y se restringe por referrer en Google Cloud.
 * Sin llave, o si la carga falla, quien llama debe mostrar la captura manual de coordenadas.
 */

export interface LatLngLiteral {
  readonly lat: number;
  readonly lng: number;
}

/** Subconjunto tipado de la API que usa el selector; evita depender de @types/google.maps. */
export interface GoogleMapInstance {
  setCenter(position: LatLngLiteral): void;
  setZoom(zoom: number): void;
  addListener(event: 'click', handler: (event: { latLng?: GoogleLatLng | null }) => void): void;
}

export interface GoogleLatLng {
  lat(): number;
  lng(): number;
}

export interface GoogleMarkerInstance {
  setPosition(position: LatLngLiteral): void;
  addListener(event: 'dragend', handler: (event: { latLng?: GoogleLatLng | null }) => void): void;
}

export interface GoogleMapsNamespace {
  Map: new (element: HTMLElement, options: Record<string, unknown>) => GoogleMapInstance;
  Marker: new (options: Record<string, unknown>) => GoogleMarkerInstance;
}

declare global {
  interface Window {
    google?: { maps?: GoogleMapsNamespace };
  }
}

export function getGoogleMapsBrowserKey(): string | undefined {
  const key = import.meta.env.VITE_GOOGLE_MAPS_BROWSER_KEY?.trim();
  if (!key) return undefined;
  return key;
}

let loading: Promise<GoogleMapsNamespace> | undefined;

/** Carga el script una sola vez. Si falla, la siguiente llamada vuelve a intentarlo. */
export function loadGoogleMaps(key: string): Promise<GoogleMapsNamespace> {
  const ready = window.google?.maps;
  if (ready) return Promise.resolve(ready);
  loading ??= new Promise<GoogleMapsNamespace>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&language=es&region=CO`;
    script.async = true;
    script.onload = () => {
      const maps = window.google?.maps;
      if (maps) resolve(maps);
      else {
        loading = undefined;
        reject(new Error('Google Maps no quedó disponible tras cargar el script.'));
      }
    };
    script.onerror = () => {
      loading = undefined;
      script.remove();
      reject(new Error('No se pudo cargar Google Maps.'));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/** Redondea a 6 decimales (~11 cm), precisión suficiente para un punto de encuentro. */
export function roundCoordinate(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

/** Convierte el texto de latitud o longitud a número (coma o punto decimal); `null` si está vacío o no es numérico. */
export function parseCoordinate(text: string): number | null {
  const normalized = text.trim().replace(',', '.');
  if (normalized === '') return null;
  const value = Number(normalized);
  return Number.isFinite(value) ? value : null;
}

/** Colombia, centrado para un primer uso sin punto previo. */
export const COLOMBIA_CENTER: LatLngLiteral = { lat: 4.570868, lng: -74.297333 };
