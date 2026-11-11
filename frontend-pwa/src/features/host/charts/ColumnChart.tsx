export interface ColumnPoint {
  readonly key: string;
  /** Texto bajo la columna cuando toca (la mayoría va en blanco para no saturar el eje). */
  readonly tick: string | null;
  readonly value: number;
  /** Detalle que se muestra al pasar el cursor. */
  readonly detail: string;
  /** La columna que cuenta la historia: va en el tono de marca y las demás atenuadas. */
  readonly highlight?: boolean;
}

interface ColumnChartProps {
  readonly points: readonly ColumnPoint[];
  /** Resumen en una frase: es lo que lee un lector de pantalla en lugar de las columnas. */
  readonly summary: string;
}

const CHART_HEIGHT_PX = 128;

/**
 * Columnas de una sola serie sobre una base común (≤ 24 px de ancho, punta redondeada de 4 px, base
 * recta). Solo se rotula el valor máximo; el resto se lee al pasar el cursor o en la vista de tabla.
 * Si ninguna columna se resalta, todas van en el tono de marca.
 */
export function ColumnChart({ points, summary }: ColumnChartProps) {
  const max = Math.max(1, ...points.map((point) => point.value));
  const emphasis = points.some((point) => point.highlight === true);
  const peak = points.reduce<ColumnPoint | null>(
    (best, point) =>
      point.value > 0 && (best === null || point.value > best.value) ? point : best,
    null,
  );
  return (
    <div role="img" aria-label={summary} className="pt-6">
      <div
        className="flex items-end gap-0.5 border-b border-border-subtle"
        style={{ height: `${String(CHART_HEIGHT_PX)}px` }}
      >
        {points.map((point) => {
          const height = (point.value / max) * CHART_HEIGHT_PX;
          const tone = emphasis && point.highlight !== true ? 'bg-border-subtle' : 'bg-brand';
          return (
            <div
              key={point.key}
              className="group relative flex h-full min-w-0 flex-1 items-end justify-center"
            >
              {point.value > 0 && (
                <span
                  className={`${tone} block w-full max-w-6 rounded-t-[4px]`}
                  style={{ height: `${String(Math.max(height, 2))}px` }}
                />
              )}
              {point === peak && (
                <span
                  className="absolute -translate-y-1 text-xs font-bold text-ink tabular-nums"
                  style={{ bottom: `${String(height)}px` }}
                >
                  {point.value}
                </span>
              )}
              <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 rounded-lg bg-surface-inverse px-3 py-1.5 text-sm whitespace-nowrap text-ink-inverse shadow-lg group-hover:block">
                {point.detail}
              </span>
            </div>
          );
        })}
      </div>
      <div className="mt-1 flex gap-0.5" aria-hidden="true">
        {points.map((point) => (
          <span key={point.key} className="relative h-4 min-w-0 flex-1">
            {point.tick !== null && (
              <span className="absolute left-1/2 -translate-x-1/2 text-xs whitespace-nowrap text-ink-muted">
                {point.tick}
              </span>
            )}
          </span>
        ))}
      </div>
    </div>
  );
}

export interface TableRow {
  readonly key: string;
  readonly label: string;
  readonly values: readonly (string | number)[];
}

/** La misma serie como tabla: para leer cifras exactas o con lector de pantalla. */
export function DataTable({
  caption,
  firstColumn,
  columns,
  rows,
  empty,
}: {
  readonly caption: string;
  readonly firstColumn: string;
  readonly columns: readonly string[];
  readonly rows: readonly TableRow[];
  readonly empty: string;
}) {
  if (rows.length === 0) return <p className="text-ink-muted">{empty}</p>;
  return (
    <div className="max-h-72 overflow-auto">
      <table className="w-full text-left">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b-2 border-border-subtle">
            <th scope="col" className="py-2 pr-3 font-semibold">
              {firstColumn}
            </th>
            {columns.map((column) => (
              <th key={column} scope="col" className="py-2 pl-3 text-right font-semibold">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} className="border-b border-border-subtle">
              <th scope="row" className="py-2 pr-3 font-normal">
                {row.label}
              </th>
              {row.values.map((value, index) => (
                <td key={columns[index]} className="py-2 pl-3 text-right tabular-nums">
                  {value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
