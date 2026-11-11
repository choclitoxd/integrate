import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

import { Button } from '@/shared/ui';

import {
  COLOMBIA_CENTER,
  getGoogleMapsBrowserKey,
  loadGoogleMaps,
  parseGoogleMapsLink,
  roundCoordinate,
  type GoogleMapInstance,
  type GoogleMarkerInstance,
  type LatLngLiteral,
} from './googleMaps';

/** Punto elegido; `address` solo llega cuando se eligió un lugar de la búsqueda. */
export interface PickedPoint extends LatLngLiteral {
  readonly address?: string | undefined;
}

interface LocationPickerProps {
  /** Latitud y longitud actuales del punto; `null` si aún no hay punto. */
  readonly lat: number | null;
  readonly lng: number | null;
  /** Recibe el punto elegido con el pin, el mapa, la búsqueda, un enlace o "Usar mi ubicación". */
  readonly onChange: (point: PickedPoint) => void;
}

type MapStatus = 'loading' | 'ready' | 'unavailable';

interface MapHandles {
  readonly map: GoogleMapInstance;
  readonly marker: GoogleMarkerInstance;
}

const LINK_PREFIX = /^https?:\/\//i;

/**
 * Selector de punto de encuentro con Google Maps (ADR-0056, Fases 1 y 2).
 * Sin llave o si el mapa no carga, la búsqueda deja de sugerir lugares, pero el enlace pegado sigue funcionando;
 * y "Avanzado" siempre permite escribir las coordenadas.
 */
export function LocationPicker({ lat, lng, onChange }: LocationPickerProps) {
  const apiKey = getGoogleMapsBrowserKey();
  const searchId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const handlesRef = useRef<MapHandles | null>(null);
  const onChangeRef = useRef(onChange);
  const startRef = useRef({ lat, lng });
  const [status, setStatus] = useState<MapStatus>(apiKey ? 'loading' : 'unavailable');
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [searchValue, setSearchValue] = useState('');
  const [searchError, setSearchError] = useState<string | null>(null);

  useEffect(() => {
    onChangeRef.current = onChange;
  });

  useEffect(() => {
    if (!apiKey || !containerRef.current) return undefined;
    let cancelled = false;
    loadGoogleMaps(apiKey)
      .then((maps) => {
        const container = containerRef.current;
        if (cancelled || !container) return;
        const { lat: startLat, lng: startLng } = startRef.current;
        const hasPoint = startLat !== null && startLng !== null;
        const start: LatLngLiteral = hasPoint ? { lat: startLat, lng: startLng } : COLOMBIA_CENTER;
        const map = new maps.Map(container, {
          center: start,
          zoom: hasPoint ? 16 : 6,
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: false,
        });
        const marker = new maps.Marker({
          position: start,
          map,
          draggable: true,
          title: 'Punto de encuentro',
        });
        marker.addListener('dragend', (event) => {
          const point = event.latLng;
          if (point)
            onChangeRef.current({
              lat: roundCoordinate(point.lat()),
              lng: roundCoordinate(point.lng()),
            });
        });
        map.addListener('click', (event) => {
          const point = event.latLng;
          if (!point) return;
          const next = { lat: roundCoordinate(point.lat()), lng: roundCoordinate(point.lng()) };
          marker.setPosition(next);
          onChangeRef.current(next);
        });
        handlesRef.current = { map, marker };

        const input = searchRef.current;
        if (input) {
          const autocomplete = new maps.places.Autocomplete(input, {
            componentRestrictions: { country: 'co' },
            fields: ['geometry', 'formatted_address', 'name'],
          });
          autocomplete.addListener('place_changed', () => {
            const place = autocomplete.getPlace();
            const location = place.geometry?.location;
            if (!location) {
              setSearchError('Elige un lugar de la lista de sugerencias.');
              return;
            }
            const picked: PickedPoint = {
              lat: roundCoordinate(location.lat()),
              lng: roundCoordinate(location.lng()),
              address: place.formatted_address ?? place.name,
            };
            setSearchError(null);
            setSearchValue('');
            applyPoint(picked);
          });
        }
        setStatus('ready');
      })
      .catch(() => {
        if (!cancelled) setStatus('unavailable');
      });
    return () => {
      cancelled = true;
      handlesRef.current = null;
    };
  }, [apiKey]);

  // Un valor escrito a mano (Avanzado) o recibido de GPS mueve el pin sin volver a crear el mapa.
  useEffect(() => {
    const handles = handlesRef.current;
    if (!handles || lat === null || lng === null) return;
    handles.marker.setPosition({ lat, lng });
  }, [lat, lng]);

  /** Lleva el pin y el mapa a un punto y lo reporta al formulario. */
  function applyPoint(point: PickedPoint) {
    onChangeRef.current(point);
    const handles = handlesRef.current;
    if (handles) {
      handles.map.setCenter(point);
      handles.map.setZoom(17);
      handles.marker.setPosition(point);
    }
    setMessage('Revisa el pin y muévelo si no coincide con el punto exacto.');
  }

  function applyTypedLink() {
    const result = parseGoogleMapsLink(searchValue);
    if (result.kind === 'point') {
      setSearchError(null);
      setSearchValue('');
      applyPoint(result.point);
      return;
    }
    setSearchError(
      result.kind === 'short'
        ? 'Este enlace corto no se puede leer aquí. Abre el lugar en Google Maps, copia el enlace largo o escribe el nombre del lugar.'
        : 'No encontramos un punto dentro de Colombia en ese enlace. Revisa que sea un enlace de Google Maps.',
    );
  }

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    // Enter no debe enviar el formulario de la experiencia; con sugerencias abiertas, Google elige el lugar.
    if (event.key !== 'Enter') return;
    event.preventDefault();
    if (LINK_PREFIX.test(searchValue.trim())) applyTypedLink();
  }

  function locateMe() {
    if (!('geolocation' in navigator)) {
      setMessage(
        'Tu navegador no permite obtener la ubicación. Mueve el pin o escribe las coordenadas.',
      );
      return;
    }
    setLocating(true);
    setMessage(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        applyPoint({
          lat: roundCoordinate(position.coords.latitude),
          lng: roundCoordinate(position.coords.longitude),
        });
      },
      () => {
        setLocating(false);
        setMessage('No pudimos obtener tu ubicación. Mueve el pin o escribe las coordenadas.');
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }

  const hasPoint = lat !== null && lng !== null;
  const looksLikeLink = LINK_PREFIX.test(searchValue.trim());

  return (
    <div className="grid gap-3">
      <div className="grid gap-2">
        <label htmlFor={searchId} className="block font-semibold text-ink">
          Buscar lugar en Colombia o pegar enlace de Google Maps
        </label>
        <input
          ref={searchRef}
          id={searchId}
          type="search"
          enterKeyHint="search"
          autoComplete="off"
          value={searchValue}
          onChange={(event) => {
            setSearchValue(event.target.value);
            setSearchError(null);
          }}
          onKeyDown={handleSearchKeyDown}
          className="min-h-11 w-full rounded-lg border-2 border-border bg-canvas px-3 text-base text-ink placeholder:text-ink-subtle focus:border-brand focus:outline-none"
          placeholder="Ej.: vereda o sendero, o pega un enlace largo"
        />
        {looksLikeLink && (
          <Button
            type="button"
            variant="secondary"
            onClick={applyTypedLink}
            className="min-h-11 w-full"
          >
            Usar este enlace
          </Button>
        )}
        {searchError && (
          <p role="alert" className="text-sm font-semibold text-danger-text">
            {searchError}
          </p>
        )}
      </div>

      {status === 'unavailable' ? (
        <p
          className="rounded-lg border-2 border-border-subtle p-3 text-sm text-ink-muted"
          role="status"
        >
          El mapa no está disponible. Puedes pegar un enlace de Google Maps o escribir las
          coordenadas en “Avanzado”.
        </p>
      ) : (
        <>
          <div
            ref={containerRef}
            role="region"
            aria-label="Mapa para ubicar el punto de encuentro"
            className="h-64 w-full overflow-hidden rounded-lg border-2 border-border bg-canvas"
          >
            {status === 'loading' && <p className="p-4 text-sm text-ink-muted">Cargando mapa…</p>}
          </div>
          <p className="text-sm text-ink-muted">Mueve el pin al punto exacto antes de guardar.</p>
          <div>
            <Button
              type="button"
              variant="secondary"
              onClick={locateMe}
              disabled={locating}
              className="w-full min-h-11"
            >
              {locating ? 'Buscando tu ubicación…' : 'Usar mi ubicación'}
            </Button>
          </div>
        </>
      )}
      {hasPoint && (
        <p className="text-sm text-ink-muted">
          Punto elegido: {lat.toFixed(6)}, {lng.toFixed(6)}
        </p>
      )}
      {message && (
        <p role="status" className="text-sm font-semibold text-ink">
          {message}
        </p>
      )}
    </div>
  );
}
