import { useId, useState, type SubmitEvent } from 'react';

import { Icon } from '@/shared/ui';

interface SearchBarProps {
  readonly initialQuery: string;
  readonly onSearch: (query: string) => void;
}

/*
 * Efectos de interacción: al enfocar, la barra se eleva y gana una sombra suave; con texto escrito,
 * el botón muestra su etiqueta (desde 368 px: en 320 px el campo necesita el espacio). Todo desde tokens (color-mix sobre variables del @theme) y con
 * movimiento reducido sin elevación; las transiciones las apaga la regla global de index.css.
 */
const FORM_CLASS =
  'group relative flex min-w-0 flex-1 transition-transform duration-200 ease-in-out before:pointer-events-none before:absolute before:inset-0 before:rounded-lg before:opacity-0 before:shadow-[0_0.5rem_1.5rem_-0.75rem_color-mix(in_oklab,var(--color-brand)_55%,transparent)] before:transition-opacity before:duration-300 before:ease-in-out focus-within:-translate-y-1 focus-within:before:opacity-100 motion-reduce:focus-within:translate-y-0';

const LABEL_CLASS =
  'max-w-0 overflow-hidden font-semibold whitespace-nowrap opacity-0 transition-[max-width,opacity,margin] duration-200 ease-in-out min-[23rem]:group-has-[input:not(:placeholder-shown)]:ml-2 min-[23rem]:group-has-[input:not(:placeholder-shown)]:max-w-16 min-[23rem]:group-has-[input:not(:placeholder-shown)]:opacity-100 min-[23rem]:group-has-[input:not(:placeholder-shown)]:pr-1';

/** Búsqueda por título con envío explícito: una consulta por intención, no por tecla. */
export function SearchBar({ initialQuery, onSearch }: SearchBarProps) {
  const [value, setValue] = useState(initialQuery);
  const inputId = useId();

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSearch(value.trim());
  }

  return (
    <form role="search" onSubmit={handleSubmit} className={FORM_CLASS}>
      <label htmlFor={inputId} className="sr-only">
        Buscar experiencias por nombre
      </label>
      <input
        id={inputId}
        type="search"
        enterKeyHint="search"
        maxLength={100}
        value={value}
        onChange={(event) => {
          setValue(event.target.value);
        }}
        placeholder="Buscar"
        className="min-h-11 w-full min-w-0 rounded-l-lg border-2 border-r-0 border-border bg-canvas px-3 text-base text-ink placeholder:text-ink-subtle focus:outline-none focus-visible:outline-none focus-visible:shadow-none"
      />
      <button
        type="submit"
        aria-label="Buscar"
        className="inline-flex min-h-11 min-w-12 shrink-0 items-center justify-center rounded-r-lg bg-brand text-on-brand transition-shadow duration-300 ease-in-out hover:bg-brand-hover hover:shadow-[0_0.5rem_1rem_-0.5rem_color-mix(in_oklab,var(--color-brand)_60%,transparent)]"
      >
        <Icon name="search" size={22} />
        {/* Decorativo: el nombre accesible del botón es siempre "Buscar" (aria-label). */}
        <span aria-hidden="true" className={LABEL_CLASS}>
          Buscar
        </span>
      </button>
    </form>
  );
}
