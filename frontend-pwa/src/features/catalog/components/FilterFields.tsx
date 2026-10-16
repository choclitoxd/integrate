import { useId } from 'react';

import { toTitleCase } from '@/shared/lib';

import type { PhysicalIntensity } from '../api/types';
import { INTENSITY_LABELS, setFilter, type CatalogFilters } from '../filters';
import { useCategories, useDepartments, useMunicipalities } from '../queries';

const SELECT_CLASS =
  'mt-1.5 block min-h-11 w-full rounded-lg border-2 border-border bg-canvas px-3 text-base text-ink disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted';

const INTENSITY_OPTIONS: readonly (PhysicalIntensity | '')[] = ['', 'LOW', 'MEDIUM', 'HIGH'];

interface FilterFieldsProps {
  readonly draft: CatalogFilters;
  readonly onChange: (draft: CatalogFilters) => void;
  /** Rejilla de intensidad: 4 columnas en la hoja móvil, 2 en la barra lateral de escritorio. */
  readonly intensityColumns?: 2 | 4;
}

/**
 * Campos DIVIPOLA, categoría e intensidad (T-1.10a), compartidos por la hoja inferior móvil y la
 * barra lateral de escritorio (ADR-0020 §6). El formulario que los envuelve decide cuándo aplicar.
 */
export function FilterFields({ draft, onChange, intensityColumns = 4 }: FilterFieldsProps) {
  const hintId = useId();
  const groupName = useId();
  const departments = useDepartments();
  const municipalities = useMunicipalities(draft.departmentCode);
  const categories = useCategories();

  return (
    <>
      <label className="block font-semibold">
        Departamento
        <select
          className={SELECT_CLASS}
          value={draft.departmentCode ?? ''}
          disabled={!departments.data}
          onChange={(event) => {
            onChange(setFilter(draft, 'departmentCode', event.target.value));
          }}
        >
          <option value="">{departments.isError ? 'No disponible' : 'Todos'}</option>
          {departments.data?.map((place) => (
            <option key={place.code} value={place.code}>
              {toTitleCase(place.name)}
            </option>
          ))}
        </select>
      </label>

      <div>
        <label className="block font-semibold">
          Municipio
          <select
            className={SELECT_CLASS}
            value={draft.municipalityCode ?? ''}
            disabled={!draft.departmentCode || !municipalities.data}
            aria-describedby={draft.departmentCode ? undefined : hintId}
            onChange={(event) => {
              onChange(setFilter(draft, 'municipalityCode', event.target.value));
            }}
          >
            <option value="">Todos</option>
            {municipalities.data?.map((place) => (
              <option key={place.code} value={place.code}>
                {toTitleCase(place.name)}
              </option>
            ))}
          </select>
        </label>
        {!draft.departmentCode && (
          <p id={hintId} className="mt-1 text-sm text-ink-muted">
            Elige primero un departamento.
          </p>
        )}
      </div>

      <label className="block font-semibold">
        Categoría
        <select
          className={SELECT_CLASS}
          value={draft.category ?? ''}
          disabled={!categories.data}
          onChange={(event) => {
            onChange(setFilter(draft, 'category', event.target.value));
          }}
        >
          <option value="">Todas</option>
          {categories.data?.map((category) => (
            <option key={category.code} value={category.code}>
              {category.name}
            </option>
          ))}
        </select>
      </label>

      <fieldset>
        <legend className="font-semibold">Intensidad física</legend>
        <div
          className={`mt-1.5 grid gap-2 ${intensityColumns === 4 ? 'grid-cols-4' : 'grid-cols-2'}`}
        >
          {INTENSITY_OPTIONS.map((option) => (
            <label
              key={option || 'all'}
              className="flex min-h-11 cursor-pointer items-center justify-center rounded-lg border-2 border-border bg-canvas px-1 text-center font-semibold text-ink has-checked:border-brand has-checked:bg-brand has-checked:text-on-brand has-focus-visible:outline-3 has-focus-visible:outline-focus"
            >
              <input
                type="radio"
                name={groupName}
                value={option}
                checked={(draft.intensity ?? '') === option}
                onChange={() => {
                  onChange(setFilter(draft, 'intensity', option));
                }}
                className="sr-only"
              />
              {option ? INTENSITY_LABELS[option] : 'Todas'}
            </label>
          ))}
        </div>
      </fieldset>
    </>
  );
}
