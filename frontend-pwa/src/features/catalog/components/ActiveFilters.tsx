import { toTitleCase } from '@/shared/lib';
import { Icon } from '@/shared/ui';

import { INTENSITY_LABELS, omitFilters, type CatalogFilters } from '../filters';
import { useCategories, useDepartments, useMunicipalities } from '../queries';

interface ActiveFiltersProps {
  readonly filters: CatalogFilters;
  readonly onChange: (filters: CatalogFilters) => void;
}

interface Chip {
  readonly kind: string;
  readonly label: string;
  readonly without: CatalogFilters;
}

/** Filtros aplicados como chips removibles (≥ 44 px de alto, separados 8 px). */
export function ActiveFilters({ filters, onChange }: ActiveFiltersProps) {
  const departments = useDepartments();
  const municipalities = useMunicipalities(filters.departmentCode);
  const categories = useCategories();

  const { q, departmentCode, municipalityCode, category, intensity } = filters;
  const nameOf = (list: readonly { code: string; name: string }[] | undefined, code: string) =>
    list?.find((item) => item.code === code)?.name;

  const chips: Chip[] = [];
  if (q) chips.push({ kind: 'Búsqueda', label: `“${q}”`, without: omitFilters(filters, 'q') });
  if (departmentCode) {
    const name = nameOf(departments.data, departmentCode);
    chips.push({
      kind: 'Departamento',
      label: name ? toTitleCase(name) : departmentCode,
      without: omitFilters(filters, 'departmentCode'),
    });
  }
  if (municipalityCode) {
    const name = nameOf(municipalities.data, municipalityCode);
    chips.push({
      kind: 'Municipio',
      label: name ? toTitleCase(name) : municipalityCode,
      without: omitFilters(filters, 'municipalityCode'),
    });
  }
  if (category) {
    chips.push({
      kind: 'Categoría',
      label: nameOf(categories.data, category) ?? category,
      without: omitFilters(filters, 'category'),
    });
  }
  if (intensity) {
    chips.push({
      kind: 'Intensidad',
      label: `Intensidad ${INTENSITY_LABELS[intensity].toLowerCase()}`,
      without: omitFilters(filters, 'intensity'),
    });
  }

  if (chips.length === 0) return null;

  return (
    <ul className="flex flex-wrap gap-2" aria-label="Filtros aplicados">
      {chips.map((chip) => (
        <li key={chip.kind}>
          <button
            type="button"
            aria-label={`Quitar filtro ${chip.kind}: ${chip.label}`}
            onClick={() => {
              onChange(chip.without);
            }}
            className="inline-flex min-h-11 items-center gap-1.5 rounded-full border-2 border-brand-border bg-brand-soft pr-2.5 pl-3.5 font-semibold text-brand hover:bg-canvas"
          >
            {chip.label}
            <Icon name="close" size={18} />
          </button>
        </li>
      ))}
      {chips.length > 1 && (
        <li>
          <button
            type="button"
            onClick={() => {
              onChange({});
            }}
            className="inline-flex min-h-11 items-center px-2 font-semibold text-brand underline underline-offset-4"
          >
            Quitar todos
          </button>
        </li>
      )}
    </ul>
  );
}
