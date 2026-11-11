import { useEffect, useRef, useState } from 'react';

import { Button } from '@/shared/ui';

import {
  COLOMBIA_CENTER,
  getGoogleMapsBrowserKey,
  loadGoogleMaps,
  roundCoordinate,
  type GoogleMapInstance,
  type GoogleMarkerInstance,
  type LatLngLiteral,
} from './googleMaps';

interface LocationPickerProps {
  /** Latitud y longitud actuales del punto; `null` si aún no hay punto. */
  readonly lat: number | null;
  readonly lng: number | null;
  /** Recibe el punto elegido con el pin, el mapa o "Usar mi ubicación". */
  readonly onChange: (point: LatLngLiteral) => void;
}

type MapStatus = 'loading' | 'ready' | 'unavailable';

interface MapHandles {
  readonly map: GoogleMapInstance;
  readonly marker: GoogleMarkerInstance;
}

/**
 * Selector de punto de encuentro con Google Maps (ADR-0056, Fase 1).
 * Sin llave o si el mapa no carga, muestra un aviso y el formulario usa la captura manual de coordenadas.
 */
export function LocationPicker({ lat, lng, onChange }: LocationPickerProps) {
  const apiKey = getGoogleMapsBrowserKey();
  const containerRef = useRef<HTMLDivElement>(null);
  const handlesRef = useRef<MapHandles | null>(null);
  const onChangeRef = useRef(onChange);
  const startRef = useRef({ lat, lng });
  const [status, setStatus] = useState<MapStatus>(apiKey ? 'loading' : 'unavailable');
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

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
        const point = {
          lat: roundCoordinate(position.coords.latitude),
          lng: roundCoordinate(position.coords.longitude),
        };
        setLocating(false);
        onChangeRef.current(point);
        const handles = handlesRef.current;
        if (handles) {
          handles.map.setCenter(point);
          handles.map.setZoom(17);
          handles.marker.setPosition(point);
        }
        setMessage('Revisa el pin y muévelo si no coincide con el punto exacto.');
      },
      () => {
        setLocating(false);
        setMessage('No pudimos obtener tu ubicación. Mueve el pin o escribe las coordenadas.');
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }

  const hasPoint = lat !== null && lng !== null;

  return (
    <div className="grid gap-3">
      {status === 'unavailable' ? (
        <p
          className="rounded-lg border-2 border-border-subtle p-3 text-sm text-ink-muted"
          role="status"
        >
          El mapa no está disponible. Escribe las coordenadas en “Avanzado”.
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
