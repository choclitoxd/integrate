import { useId, useState, type SubmitEvent } from 'react';

import { toTitleCase } from '@/shared/lib';
import { BottomSheet, Button } from '@/shared/ui';

import type { PhysicalIntensity } from '../api/types';
import { INTENSITY_LABELS, omitFilters, setFilter, type CatalogFilters } from '../filters';
import { useCategories, useDepartments, useMunicipalities } from '../queries';

interface FilterSheetProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly filters: CatalogFilters;
  readonly onApply: (filters: CatalogFilters) => void;
}

const SELECT_CLASS =
  'mt-1.5 block min-h-11 w-full rounded-lg border-2 border-border bg-canvas px-3 text-base text-ink disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted';

const INTENSITY_OPTIONS: readonly (PhysicalIntensity | '')[] = ['', 'LOW', 'MEDIUM', 'HIGH'];

/**
 * Filtros DIVIPOLA, categoría e intensidad en una hoja inferior (T-1.10a). El borrador se aplica
 * solo con "Ver resultados" y se reinicia desde la URL en cada apertura. El <dialog> no se remonta:
 * así el navegador devuelve el foco al botón "Filtros" al cerrar.
 */
export function FilterSheet({ open, onClose, filters, onApply }: FilterSheetProps) {
  const [draft, setDraft] = useState<CatalogFilters>(filters);
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    // Ajuste de estado al cambiar una prop, durante el render (sin efecto ni render intermedio).
    setWasOpen(open);
    if (open) setDraft(filters);
  }
  const formId = useId();
  const departments = useDepartments();
  const municipalities = useMunicipalities(draft.departmentCode);
  const categories = useCategories();

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(draft);
  }

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="Filtrar experiencias"
      footer={
        <>
          <Button
            variant="secondary"
            className="flex-1"
            onClick={() => {
              setDraft(omitFilters(draft, 'departmentCode', 'category', 'intensity'));
            }}
          >
            Limpiar
          </Button>
          <Button type="submit" form={formId} className="flex-[2]">
            Ver resultados
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} className="grid gap-5">
        <label className="block font-semibold">
          Departamento
          <select
            className={SELECT_CLASS}
            value={draft.departmentCode ?? ''}
            disabled={!departments.data}
            onChange={(event) => {
              setDraft(setFilter(draft, 'departmentCode', event.target.value));
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
              aria-describedby={draft.departmentCode ? undefined : `${formId}-municipio`}
              onChange={(event) => {
                setDraft(setFilter(draft, 'municipalityCode', event.target.value));
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
            <p id={`${formId}-municipio`} className="mt-1 text-sm text-ink-muted">
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
              setDraft(setFilter(draft, 'category', event.target.value));
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
          <div className="mt-1.5 grid grid-cols-4 gap-2">
            {INTENSITY_OPTIONS.map((option) => (
              <label
                key={option || 'all'}
                className="flex min-h-11 cursor-pointer items-center justify-center rounded-lg border-2 border-border bg-canvas px-1 text-center font-semibold text-ink has-checked:border-brand has-checked:bg-brand has-checked:text-on-brand has-focus-visible:outline-3 has-focus-visible:outline-focus"
              >
                <input
                  type="radio"
                  name="intensidad"
                  value={option}
                  checked={(draft.intensity ?? '') === option}
                  onChange={() => {
                    setDraft(setFilter(draft, 'intensity', option));
                  }}
                  className="sr-only"
                />
                {option ? INTENSITY_LABELS[option] : 'Todas'}
              </label>
            ))}
          </div>
        </fieldset>
      </form>
    </BottomSheet>
  );
}
