import { useCallback, useRef, useState } from 'react';

import { useDepartments, useMunicipalities, type Place } from '@/features/catalog';
import { reverseGeocode, type DetectedPlace, type LatLngLiteral } from '@/shared/maps/googleMaps';
import { findPlaceByName } from '@/shared/maps/places';

export type DetectionStatus = 'idle' | 'detecting' | 'found' | 'unresolved';

export interface LocationDetection {
  readonly status: DetectionStatus;
  /** Cambia con cada punto nuevo; sirve para aplicar la sugerencia una sola vez. */
  readonly seq: number;
  readonly department: Place | undefined;
  readonly municipality: Place | undefined;
  readonly vereda: string | undefined;
  /** Municipios del departamento detectado, para el aviso de coherencia con la dirección. */
  readonly municipalities: readonly Place[];
  readonly detect: (point: LatLngLiteral) => void;
}

interface Pending {
  readonly seq: number;
  readonly done: boolean;
  readonly place: DetectedPlace | null;
}

/**
 * Detecta el municipio de un punto: geocodificación inversa de Google y resolución contra DIVIPOLA
 * con los endpoints públicos del catálogo (ADR-0056, enmienda de la Fase 3).
 */
export function useLocationDetection(): LocationDetection {
  const seqRef = useRef(0);
  const [pending, setPending] = useState<Pending | null>(null);
  const departments = useDepartments();
  const department = findPlaceByName(departments.data ?? [], pending?.place?.department);
  const municipalities = useMunicipalities(department?.code);
  const municipality = findPlaceByName(municipalities.data ?? [], pending?.place?.municipality);

  const detect = useCallback((point: LatLngLiteral) => {
    seqRef.current += 1;
    const seq = seqRef.current;
    setPending({ seq, done: false, place: null });
    void reverseGeocode(point).then((place) => {
      if (seqRef.current === seq) setPending({ seq, done: true, place });
    });
  }, []);

  let status: DetectionStatus = 'idle';
  if (pending) {
    const resolving =
      !pending.done ||
      (pending.place !== null && departments.isPending) ||
      (department !== undefined && municipalities.isPending);
    if (resolving) status = 'detecting';
    else status = municipality ? 'found' : 'unresolved';
  }

  return {
    status,
    seq: pending?.seq ?? 0,
    department,
    municipality,
    vereda: pending?.place?.vereda,
    municipalities: municipalities.data ?? [],
    detect,
  };
}
