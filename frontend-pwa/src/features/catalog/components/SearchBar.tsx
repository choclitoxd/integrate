import { useId, useState, type SubmitEvent } from 'react';

import { Icon } from '@/shared/ui';

interface SearchBarProps {
  readonly initialQuery: string;
  readonly onSearch: (query: string) => void;
}

/** Búsqueda por título con envío explícito: una consulta por intención, no por tecla. */
export function SearchBar({ initialQuery, onSearch }: SearchBarProps) {
  const [value, setValue] = useState(initialQuery);
  const inputId = useId();

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSearch(value.trim());
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="flex min-w-0 flex-1">
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
        className="min-h-11 w-full min-w-0 rounded-l-lg border-2 border-r-0 border-border bg-canvas px-3 text-base text-ink placeholder:text-ink-subtle"
      />
      <button
        type="submit"
        aria-label="Buscar"
        className="inline-flex min-h-11 min-w-12 items-center justify-center rounded-r-lg bg-brand text-on-brand hover:bg-brand-hover"
      >
        <Icon name="search" size={22} />
      </button>
    </form>
  );
}
