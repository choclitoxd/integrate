import { pluralize } from '@/shared/lib';

export interface RankedItem {
  readonly id: string;
  readonly label: string;
  readonly value: number;
}

interface RankedBarsProps {
  readonly items: readonly RankedItem[];
  /** Total del período: el porcentaje de cada barra se calcula contra él. */
  readonly total: number;
  readonly unit: readonly [singular: string, plural: string];
}

function share(value: number, total: number) {
  return total > 0 ? Math.round((value / total) * 100) : 0;
}

/**
 * Barras horizontales ordenadas (una sola serie, un solo tono): la longitud compara la magnitud
 * sobre una base común y el valor va en la punta. Al pasar el cursor muestra su detalle; el texto completo lo leen los lectores de pantalla y la vista de tabla.
 */
export function RankedBars({ items, total, unit }: RankedBarsProps) {
  const max = Math.max(1, ...items.map((item) => item.value));
  return (
    <ol className="grid gap-3">
      {items.map((item) => {
        const text = `${pluralize(item.value, unit[0], unit[1])} · ${String(share(item.value, total))} % del mes`;
        return (
          <li key={item.id} className="group relative grid gap-1 rounded-lg">
            <span className="sr-only">{`${item.label}: ${text}`}</span>
            <span aria-hidden="true" className="font-semibold break-words text-ink">
              {item.label}
            </span>
            <span className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className="h-5 flex-1 overflow-hidden rounded-r-[4px] bg-surface"
              >
                <span
                  className="block h-full rounded-r-[4px] bg-brand"
                  style={{ width: `${String((item.value / max) * 100)}%` }}
                />
              </span>
              <span aria-hidden="true" className="w-24 shrink-0 text-right tabular-nums text-ink">
                <strong>{item.value}</strong>{' '}
                <span className="text-sm text-ink-muted">({share(item.value, total)} %)</span>
              </span>
            </span>
            <span
              role="presentation"
              className="pointer-events-none absolute -top-1 right-0 z-10 hidden -translate-y-full rounded-lg bg-surface-inverse px-3 py-1.5 text-sm text-ink-inverse shadow-lg group-hover:block"
            >
              {text}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

/** La misma información como tabla, para quien prefiere leer cifras o usa lector de pantalla. */
export function RankedTable({
  items,
  total,
  caption,
  valueLabel,
}: RankedBarsProps & { readonly caption: string; readonly valueLabel: string }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b-2 border-border-subtle">
            <th scope="col" className="py-2 pr-3 font-semibold">
              Experiencia
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-semibold">
              {valueLabel}
            </th>
            <th scope="col" className="py-2 text-right font-semibold">
              % del mes
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id} className="border-b border-border-subtle">
              <th scope="row" className="py-2 pr-3 font-normal break-words">
                {item.label}
              </th>
              <td className="py-2 pr-3 text-right tabular-nums">{item.value}</td>
              <td className="py-2 text-right tabular-nums">{share(item.value, total)} %</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
