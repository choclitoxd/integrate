import type { ReactNode } from 'react';
import { useOnlineStatus } from '@/shared/offline';
import { operationFieldClass } from './tableStyles';
import { Notice } from './parts';
import { CAPTION_CLASS, TABLE_CLASS, TH_CLASS } from './tableStyles';

export function OperationFilter({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: Readonly<Record<string, string>>;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid min-w-0 gap-2 font-semibold">
      {label}
      <select
        className={operationFieldClass}
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
        }}
      >
        <option value="">Todos</option>
        {Object.entries(options).map(([id, name]) => (
          <option key={id} value={id}>
            {name}
          </option>
        ))}
      </select>
    </label>
  );
}

/* eslint-disable jsx-a11y-x/no-noninteractive-tabindex -- Permite desplazar la tabla con el teclado. */
export function OperationTable({
  caption,
  headings,
  children,
}: {
  caption: string;
  headings: readonly string[];
  children: ReactNode;
}) {
  return (
    <div
      className="min-w-0 max-w-full overflow-x-auto rounded-xl border border-border bg-surface"
      role="region"
      aria-label={caption}
      tabIndex={0}
    >
      <table className={TABLE_CLASS}>
        <caption className={CAPTION_CLASS}>{caption}</caption>
        <thead>
          <tr>
            {headings.map((heading) => (
              <th className={TH_CLASS} key={heading} scope="col">
                {heading}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
/* eslint-enable jsx-a11y-x/no-noninteractive-tabindex */

export function OperationConnection() {
  const online = useOnlineStatus();
  return online ? null : (
    <Notice tone="warning">Sin conexión. Las acciones requieren acceso al servidor.</Notice>
  );
}
