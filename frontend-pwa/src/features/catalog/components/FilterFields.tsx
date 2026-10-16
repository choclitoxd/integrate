import { useId } from 'react';

import { toTitleCase } from '@/shared/lib';
import { Select, type SelectOption } from '@/shared/ui';

import type { PhysicalIntensity } from '../api/types';
import { INTENSITY_LABELS, setFilter, type CatalogFilters } from '../filters';
import { useCategories, useDepartments, useMunicipalities } from '../queries';

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

  const departmentOptions: readonly SelectOption[] = [
    { value: '', label: departments.isError ? 'No disponible' : 'Todos' },
    ...(departments.data ?? []).map((place) => ({
      value: place.code,
      label: toTitleCase(place.name),
    })),
  ];
  const municipalityOptions: readonly SelectOption[] = [
    { value: '', label: 'Todos' },
    ...(municipalities.data ?? []).map((place) => ({
      value: place.code,
      label: toTitleCase(place.name),
    })),
  ];
  const categoryOptions: readonly SelectOption[] = [
    { value: '', label: 'Todas' },
    ...(categories.data ?? []).map((category) => ({ value: category.code, label: category.name })),
  ];

  return (
    <>
      <Select
        label="Departamento"
        value={draft.departmentCode ?? ''}
        options={departmentOptions}
        disabled={!departments.data}
        onChange={(code) => {
          onChange(setFilter(draft, 'departmentCode', code));
        }}
      />

      <div>
        <Select
          label="Municipio"
          value={draft.municipalityCode ?? ''}
          options={municipalityOptions}
          disabled={!draft.departmentCode || !municipalities.data}
          describedBy={draft.departmentCode ? undefined : hintId}
          onChange={(code) => {
            onChange(setFilter(draft, 'municipalityCode', code));
          }}
        />
        {!draft.departmentCode && (
          <p id={hintId} className="mt-1 text-sm text-ink-muted">
            Elige primero un departamento.
          </p>
        )}
      </div>

      <Select
        label="Categoría"
        value={draft.category ?? ''}
        options={categoryOptions}
        disabled={!categories.data}
        onChange={(code) => {
          onChange(setFilter(draft, 'category', code));
        }}
      />

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
