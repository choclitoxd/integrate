import { toTitleCase } from '@/shared/lib';

import type { ExperienceSummary, RouteType } from './api/types';

export const ROUTE_TYPE_LABELS: Record<RouteType, string> = {
  LOOP: 'Circular',
  OUT_AND_BACK: 'Ida y vuelta por el mismo camino',
  ONE_WAY: 'Solo ida',
};

/** "San Carlos, Antioquia": municipio y departamento DIVIPOLA en formato título. */
export function placeLabel(experience: ExperienceSummary): string {
  return `${toTitleCase(experience.municipality.name)}, ${toTitleCase(experience.department.name)}`;
}
