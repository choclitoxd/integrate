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

export interface GooglePlaceResult {
  readonly name?: string;
  readonly formatted_address?: string;
  readonly place_id?: string;
  readonly geometry?: { readonly location?: GoogleLatLng };
}

export interface GoogleAutocompleteInstance {
  addListener(event: 'place_changed', handler: () => void): void;
  getPlace(): GooglePlaceResult;
}

export interface GoogleAddressComponent {
  readonly long_name: string;
  readonly types: readonly string[];
}

export interface GoogleGeocoderInstance {
  geocode(request: Record<string, unknown>): Promise<{
    readonly results: readonly { readonly address_components: readonly GoogleAddressComponent[] }[];
  }>;
}

export interface GoogleMapsNamespace {
  Map: new (element: HTMLElement, options: Record<string, unknown>) => GoogleMapInstance;
  Marker: new (options: Record<string, unknown>) => GoogleMarkerInstance;
  Geocoder: new () => GoogleGeocoderInstance;
  places: {
    Autocomplete: new (
      input: HTMLInputElement,
      options: Record<string, unknown>,
    ) => GoogleAutocompleteInstance;
  };
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
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&language=es&region=CO&libraries=places`;
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

/** Lo que Google reconoce en un punto; los nombres vienen tal como los escribe Google. */
export interface DetectedPlace {
  readonly department: string | undefined;
  readonly municipality: string | undefined;
  readonly vereda: string | undefined;
}

function componentName(
  components: readonly GoogleAddressComponent[],
  ...types: readonly string[]
): string | undefined {
  for (const type of types) {
    const found = components.find((component) => component.types.includes(type));
    if (found) return found.long_name;
  }
  return undefined;
}

/**
 * Geocodificación inversa con la llave de navegador (ADR-0056, enmienda de la Fase 3).
 * Solo viajan las coordenadas. Devuelve `null` sin llave, sin red o sin resultado.
 */
export async function reverseGeocode(point: LatLngLiteral): Promise<DetectedPlace | null> {
  const key = getGoogleMapsBrowserKey();
  if (!key) return null;
  try {
    const maps = await loadGoogleMaps(key);
    const { results } = await new maps.Geocoder().geocode({
      location: point,
      language: 'es',
      region: 'CO',
    });
    const withPlace = results.find((result) =>
      result.address_components.some(
        (component) =>
          component.types.includes('administrative_area_level_2') ||
          component.types.includes('locality'),
      ),
    );
    if (!withPlace) return null;
    const components = withPlace.address_components;
    return {
      department: componentName(components, 'administrative_area_level_1'),
      municipality: componentName(components, 'administrative_area_level_2', 'locality'),
      vereda: componentName(components, 'sublocality_level_1', 'sublocality', 'neighborhood'),
    };
  } catch {
    return null;
  }
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

/** Caja aproximada de Colombia (incluye San Andrés y la Guajira); fuera de ella no se acepta un punto. */
const COLOMBIA_BOX = { minLat: -4.3, maxLat: 13.5, minLng: -79.1, maxLng: -66.8 };

export type MapsLinkResult =
  | { readonly kind: 'point'; readonly point: LatLngLiteral }
  | { readonly kind: 'short' }
  | { readonly kind: 'invalid' };

const AT_PATTERN = /@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)/;
const BANG_PATTERN = /!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)/;
const SHORT_HOSTS = new Set(['maps.app.goo.gl', 'goo.gl']);

function toPoint(latText: string, lngText: string): LatLngLiteral | null {
  const lat = Number(latText);
  const lng = Number(lngText);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < COLOMBIA_BOX.minLat || lat > COLOMBIA_BOX.maxLat) return null;
  if (lng < COLOMBIA_BOX.minLng || lng > COLOMBIA_BOX.maxLng) return null;
  return { lat: roundCoordinate(lat), lng: roundCoordinate(lng) };
}

/**
 * Extrae coordenadas de un enlace de Google Maps pegado por el anfitrión (ADR-0056, Fase 2).
 * Los enlaces cortos (maps.app.goo.gl) no se resuelven en el navegador: se devuelve `short`.
 */
export function parseGoogleMapsLink(text: string): MapsLinkResult {
  const trimmed = text.trim();
  if (!/^https?:\/\//i.test(trimmed)) return { kind: 'invalid' };
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return { kind: 'invalid' };
  }
  const host = url.hostname.toLowerCase();
  if (SHORT_HOSTS.has(host)) return { kind: 'short' };
  const isGoogle = /(^|\.)google\.(com|co)$/.test(host);
  if (!isGoogle) return { kind: 'invalid' };

  let decoded: string;
  try {
    decoded = decodeURIComponent(url.pathname + url.search);
  } catch {
    return { kind: 'invalid' };
  }
  const at = AT_PATTERN.exec(decoded);
  const bang = BANG_PATTERN.exec(decoded);
  const query =
    url.searchParams.get('q') ?? url.searchParams.get('query') ?? url.searchParams.get('ll');
  const queryPair = query ? /^(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)$/.exec(query.trim()) : null;

  const match = at ?? bang ?? queryPair;
  if (!match) return { kind: 'invalid' };
  const point = toPoint(match[1] ?? '', match[2] ?? '');
  return point ? { kind: 'point', point } : { kind: 'invalid' };
}

/** Colombia, centrado para un primer uso sin punto previo. */
export const COLOMBIA_CENTER: LatLngLiteral = { lat: 4.570868, lng: -74.297333 };
