import { useId, type ReactNode, type SubmitEvent } from 'react';

import { Button, Icon, type IconName } from '@/shared/ui';

import type { Tone } from '../labels';
import {
  SEARCH_BUTTON_CLASS,
  SEARCH_FORM_CLASS,
  SEARCH_INPUT_CLASS,
  SEARCH_LABEL_CLASS,
} from './tableStyles';

const TONE_CLASS: Readonly<Record<Tone, string>> = {
  neutral: 'border-border-subtle bg-surface-subtle text-ink',
  success: 'border-success-border bg-success-bg text-success-text',
  warning: 'border-warning-border bg-warning-bg text-warning-text',
  danger: 'border-danger-border bg-danger-bg text-danger-text',
  info: 'border-info-border bg-info-bg text-info-text',
};

const TONE_ICON: Readonly<Record<Tone, IconName | null>> = {
  neutral: null,
  success: 'check',
  warning: 'clock',
  danger: 'alert',
  info: 'flag',
};

/** Etiqueta de estado: el texto y el icono dicen lo mismo que el color (WCAG 1.4.1). */
export function StatusBadge({ tone, children }: { tone: Tone; children: ReactNode }) {
  const icon = TONE_ICON[tone];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border-2 px-2.5 py-0.5 text-sm font-bold whitespace-nowrap ${TONE_CLASS[tone]}`}
    >
      {icon && <Icon name={icon} size={16} />}
      {children}
    </span>
  );
}

/** Aviso en línea (resultado de una acción, advertencia de la sección). */
export function Notice({
  tone,
  role = 'status',
  children,
}: {
  tone: Exclude<Tone, 'neutral'>;
  role?: 'status' | 'alert' | 'note';
  children: ReactNode;
}) {
  return (
    <div
      role={role}
      className={`flex items-start gap-2 rounded-xl border-2 px-4 py-3 font-semibold ${TONE_CLASS[tone]}`}
    >
      <Icon name={TONE_ICON[tone] ?? 'flag'} className="mt-0.5 shrink-0" />
      <div className="min-w-0">{children}</div>
    </div>
  );
}

/** Encabezado de sección de la consola: `h1` único con la tipografía de inicio, descripción y acciones a la derecha. */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description: string;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0 max-w-3xl">
        <h1 className="text-3xl leading-tight font-bold text-balance text-brand sm:text-4xl">
          {title}
        </h1>
        <p className="mt-2 text-lg text-ink-muted">{description}</p>
      </div>
      {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
    </div>
  );
}

/** Paginación del servidor: anterior / siguiente con la posición anunciada. */
export function Pager({
  label,
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  /** Qué se pagina, en plural: "experiencias". */
  label: string;
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  if (totalElements === 0) return null;
  return (
    <nav
      aria-label={`Páginas de ${label}`}
      className="mt-4 flex flex-wrap items-center justify-between gap-3"
    >
      <p className="text-ink-muted" aria-live="polite">
        Página {page + 1} de {Math.max(totalPages, 1)} · {totalElements} en total
      </p>
      <div className="flex gap-3">
        <Button
          variant="secondary"
          disabled={page <= 0}
          onClick={() => {
            onChange(page - 1);
          }}
        >
          <Icon name="chevronLeft" size={20} />
          Anterior
        </Button>
        <Button
          variant="secondary"
          disabled={page + 1 >= totalPages}
          onClick={() => {
            onChange(page + 1);
          }}
        >
          Siguiente
          <Icon name="chevronRight" size={20} />
        </Button>
      </div>
    </nav>
  );
}

/** Marco de tabla (FE-DIR-001 D1.7): forma rectangular con fondo transparente y título + conteo por fuera. */
export function TableFrame({
  title,
  count,
  children,
}: {
  title?: ReactNode | undefined;
  count?: number | undefined;
  children: ReactNode;
}) {
  return (
    <div className="grid min-w-0 gap-2">
      {title && (
        <p aria-hidden="true" className="text-base font-bold text-ink">
          {title}
          {count !== undefined && <span className="font-normal text-ink-muted"> · {count}</span>}
        </p>
      )}
      <div className="relative min-w-0 max-w-full overflow-x-auto rounded-lg border-2 border-border-subtle bg-transparent">
        {children}
      </div>
    </div>
  );
}

/** Buscador acoplado para tablas (FE-DIR-001 D1.7): mismo diseño y comportamiento que el buscador de experiencias. */
export function TableSearchBar({
  label,
  placeholder = 'Buscar',
  value,
  onChange,
  onSubmitSearch,
}: {
  label: string;
  placeholder?: string | undefined;
  value: string;
  onChange: (value: string) => void;
  onSubmitSearch?: ((value: string) => void) | undefined;
}) {
  const inputId = useId();

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = value.trim();
    onChange(trimmed);
    onSubmitSearch?.(trimmed);
  }

  return (
    <form role="search" aria-label={label} onSubmit={handleSubmit} className={SEARCH_FORM_CLASS}>
      <label htmlFor={inputId} className="sr-only">
        {label}
      </label>
      <input
        id={inputId}
        type="search"
        enterKeyHint="search"
        maxLength={100}
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        placeholder={placeholder}
        className={SEARCH_INPUT_CLASS}
      />
      <button type="submit" aria-label="Buscar" className={SEARCH_BUTTON_CLASS}>
        <Icon name="search" size={22} />
        <span aria-hidden="true" className={SEARCH_LABEL_CLASS}>
          Buscar
        </span>
      </button>
    </form>
  );
}

export function EmptyRow({ columns, children }: { columns: number; children: ReactNode }) {
  return (
    <tr>
      <td colSpan={columns} className="px-3 py-10 text-center text-ink-muted">
        {children}
      </td>
    </tr>
  );
}
