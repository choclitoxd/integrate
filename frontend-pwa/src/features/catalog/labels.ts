import { toTitleCase } from '@/shared/lib';

import type { ExperienceSummary } from './api/types';

/** "San Carlos, Antioquia": municipio y departamento DIVIPOLA en formato título. */
export function placeLabel(experience: ExperienceSummary): string {
  return `${toTitleCase(experience.municipality.name)}, ${toTitleCase(experience.department.name)}`;
}
