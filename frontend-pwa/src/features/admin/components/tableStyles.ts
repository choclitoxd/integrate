/**
 * Clases compartidas de las tablas y buscadores de la consola (ADR-0042 §5, ADR-0044 §1, FE-DIR-001 D1.7):
 * - Tabla y contenedor con fondo transparente (`bg-transparent`).
 * - El `caption` no se muestra dentro de la tabla (`sr-only` para lectores de pantalla); solo se ven
 *   los índices en verde (`bg-brand-soft text-brand`) y las filas de datos.
 * - Buscador acoplado unificado (`rounded-l-lg border-2 border-r-0` + botón `rounded-r-lg bg-brand`).
 */
export const TABLE_CLASS =
  'w-full border-collapse bg-transparent text-left text-ink [&_tbody_tr:last-child>*]:border-b-0';
export const CAPTION_CLASS = 'sr-only';
export const TH_CLASS =
  'border-b-2 border-border-strong bg-brand-soft px-3 py-3 text-sm font-bold text-brand';
export const TD_CLASS =
  'border-b border-border-subtle bg-transparent px-3 py-3 align-top break-words';
export const operationFieldClass =
  'min-h-11 w-full rounded-lg border-2 border-border bg-canvas px-3 py-2 text-ink focus:border-brand focus:outline-none focus-visible:outline-none focus-visible:shadow-none';

export const SEARCH_FORM_CLASS =
  'group relative flex min-w-0 flex-1 transition-transform duration-200 ease-in-out before:pointer-events-none before:absolute before:inset-0 before:rounded-lg before:opacity-0 before:shadow-[0_0.5rem_1.5rem_-0.75rem_color-mix(in_oklab,var(--color-brand)_55%,transparent)] before:transition-opacity before:duration-300 before:ease-in-out focus-within:-translate-y-1 focus-within:before:opacity-100 motion-reduce:focus-within:translate-y-0';

export const SEARCH_INPUT_CLASS =
  'min-h-11 w-full min-w-0 rounded-l-lg border-2 border-r-0 border-border bg-canvas px-3 text-base text-ink placeholder:text-ink-subtle transition-colors focus:border-brand focus:outline-none focus-visible:outline-none focus-visible:shadow-none';

export const SEARCH_BUTTON_CLASS =
  'inline-flex min-h-11 min-w-12 shrink-0 items-center justify-center rounded-r-lg bg-brand px-3 text-on-brand transition-shadow duration-300 ease-in-out hover:bg-brand-hover hover:shadow-[0_0.5rem_1rem_-0.5rem_color-mix(in_oklab,var(--color-brand)_60%,transparent)] disabled:opacity-60';

export const SEARCH_LABEL_CLASS =
  'max-w-0 overflow-hidden font-semibold whitespace-nowrap opacity-0 transition-[max-width,opacity,margin] duration-200 ease-in-out min-[23rem]:group-has-[input:not(:placeholder-shown)]:ml-2 min-[23rem]:group-has-[input:not(:placeholder-shown)]:max-w-16 min-[23rem]:group-has-[input:not(:placeholder-shown)]:opacity-100 min-[23rem]:group-has-[input:not(:placeholder-shown)]:pr-1';

/** Normaliza texto sin tildes y en minúsculas para filtrado instantáneo de filas en tablas. */
export function matchesSearchQuery(
  query: string,
  fields: readonly (string | number | null | undefined)[],
): boolean {
  const normalizedQuery = query
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
  if (!normalizedQuery) return true;
  return fields.some((field) => {
    if (field === null || field === undefined) return false;
    const normalizedField = String(field)
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
    return normalizedField.includes(normalizedQuery);
  });
}
