import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

import { Icon } from './Icon';

export interface SelectOption {
  readonly value: string;
  readonly label: string;
  /** Texto del botón cerrado cuando es más corto que el de la lista ("+57" frente a "Colombia (+57)"). */
  readonly shortLabel?: string;
}

interface SelectProps {
  /** Rótulo visible; también es el nombre accesible del control. */
  readonly label: string;
  readonly options: readonly SelectOption[];
  readonly value: string;
  readonly onChange: (value: string) => void;
  /** Se fija en el control: `form.elements.namedItem(name)` lo encuentra para enfocarlo al validar. */
  readonly name?: string;
  readonly disabled?: boolean;
  readonly invalid?: boolean;
  readonly describedBy?: string | undefined;
  /** `lg` (48 px) en formularios largos; `md` (44 px) en filtros. */
  readonly size?: 'md' | 'lg';
  /**
   * El propio campo se escribe para buscar: las opciones se filtran mientras se teclea (sin tildes
   * ni mayúsculas). Sirve para listas largas, como los municipios.
   */
  readonly searchable?: boolean;
  /** Texto de ayuda del campo cuando se borra lo escrito (solo con `searchable`). */
  readonly searchPlaceholder?: string;
  /** El rótulo se anuncia a lectores de pantalla pero no se dibuja (el campo ya va rotulado al lado). */
  readonly hideLabel?: boolean;
  /** `wide`: el menú es más ancho que un campo angosto (p. ej. el de indicativo) para leer las opciones. */
  readonly menuWidth?: 'field' | 'wide';
}

/** Altura máxima del menú: con menos espacio libre debajo se abre hacia arriba, y se acorta si hace falta. */
const MENU_HEIGHT_PX = 256;
const MENU_MIN_PX = 132;
const MENU_GAP_PX = 12;
const TYPEAHEAD_RESET_MS = 700;

/** Ancestro más cercano con scroll vertical propio; `undefined` si la que se desplaza es la página. */
function scrollParentOf(element: HTMLElement): HTMLElement | undefined {
  for (let parent = element.parentElement; parent; parent = parent.parentElement) {
    const { overflowY } = window.getComputedStyle(parent);
    if (overflowY === 'auto' || overflowY === 'scroll') return parent;
  }
  return undefined;
}

/** Texto comparable: sin mayúsculas ni tildes ("Boyacá" y "boyaca" coinciden). */
function normalizeText(text: string): string {
  return text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/**
 * Lista desplegable accesible que reemplaza al `<select>` nativo (patrón combobox de WAI-ARIA APG).
 * El foco se queda en el control y la opción activa se anuncia con `aria-activedescendant`.
 *
 * - **Botón** (por defecto, "select-only"): ↑ ↓ Inicio Fin, Enter/Espacio, Esc, Tab y salto por la
 *   inicial escrita.
 * - **`searchable`**: el campo es un `<input>`; se escribe ahí mismo para filtrar las opciones, y
 *   ↑ ↓ Enter Esc Tab manejan el menú. Al salir sin elegir, el campo vuelve al valor vigente.
 *
 * Colores solo de tokens (FE-DIR-001).
 */
export function Select({
  label,
  options,
  value,
  onChange,
  name,
  disabled = false,
  invalid = false,
  describedBy,
  size = 'md',
  searchable = false,
  searchPlaceholder,
  hideLabel = false,
  menuWidth = 'field',
}: SelectProps) {
  const baseId = useId();
  const labelId = `${baseId}-label`;
  const listId = `${baseId}-list`;
  const optionId = (index: number) => `${baseId}-option-${String(index)}`;

  const rootRef = useRef<HTMLDivElement>(null);
  const fieldRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const typeahead = useRef({ text: '', timer: 0 });
  const swallowClick = useRef(false);

  const [open, setOpen] = useState(false);
  /** Texto que el turista está escribiendo; `null` = no está buscando (el campo muestra la elección). */
  const [query, setQuery] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const [openUp, setOpenUp] = useState(false);
  const [menuMaxHeight, setMenuMaxHeight] = useState(MENU_HEIGHT_PX);

  const selectedIndex = options.findIndex((option) => option.value === value);
  const selectedLabel = options[selectedIndex]?.label ?? '';

  const needle = searchable && query !== null ? normalizeText(query.trim()) : '';
  const visibleOptions = needle
    ? options.filter((option) => normalizeText(option.label).includes(needle))
    : options;
  const last = Math.max(visibleOptions.length - 1, 0);
  const hasOptions = visibleOptions.length > 0;

  const focusControl = () => {
    (searchable ? inputRef.current : buttonRef.current)?.focus();
  };

  function closeMenu() {
    setOpen(false);
    setQuery(null);
  }

  /**
   * Decide si el menú se abre arriba o abajo y cuánto puede medir. Un menú absoluto se recorta en el
   * contenedor con scroll que lo contiene (la hoja de filtros tiene el suyo), no en la pantalla; y el
   * teclado en pantalla de un campo de búsqueda reduce el visualViewport, así que se vuelve a medir
   * cuando este cambia.
   */
  const measure = useCallback(() => {
    const field = fieldRef.current;
    if (!field) return;
    const rect = field.getBoundingClientRect();
    const scroller = scrollParentOf(field);
    const viewport = window.visualViewport;
    const screenBottom = viewport ? viewport.offsetTop + viewport.height : window.innerHeight;
    const bounds = scroller?.getBoundingClientRect() ?? { top: 0, bottom: screenBottom };
    // Lo que aún se puede desplazar hacia abajo también cuenta como espacio.
    const scrollable = scroller
      ? Math.max(scroller.scrollHeight - scroller.clientHeight - scroller.scrollTop, 0)
      : 0;
    const below = Math.min(bounds.bottom, screenBottom) - rect.bottom + scrollable;
    const above = rect.top - bounds.top;
    const up = below < MENU_HEIGHT_PX && above > below;
    setOpenUp(up);
    setMenuMaxHeight(
      Math.max(Math.min((up ? above : below) - MENU_GAP_PX, MENU_HEIGHT_PX), MENU_MIN_PX),
    );
  }, []);

  useEffect(() => {
    if (!open) return;
    // Un toque o clic fuera cierra el menú (en iOS un botón no recibe foco, así que `blur` no basta).
    function closeOnOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
        setQuery(null);
      }
    }
    document.addEventListener('pointerdown', closeOnOutside);
    window.visualViewport?.addEventListener('resize', measure);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside);
      window.visualViewport?.removeEventListener('resize', measure);
    };
  }, [open, measure]);

  function openMenu(index = Math.max(selectedIndex, 0)) {
    measure();
    setActiveIndex(index);
    setOpen(true);
  }

  function moveTo(index: number) {
    const next = Math.min(Math.max(index, 0), last);
    setActiveIndex(next);
    document.getElementById(optionId(next))?.scrollIntoView({ block: 'nearest' });
  }

  function commitOption(option: SelectOption | undefined) {
    closeMenu();
    focusControl();
    if (option && option.value !== value) onChange(option.value);
  }

  /** Salto por la inicial escrita sobre el botón (sin `searchable`). */
  function jumpTo(character: string) {
    window.clearTimeout(typeahead.current.timer);
    typeahead.current.text += character.toLowerCase();
    typeahead.current.timer = window.setTimeout(() => {
      typeahead.current.text = '';
    }, TYPEAHEAD_RESET_MS);
    // Una misma letra repetida recorre las opciones que empiezan por ella.
    const repeated = typeahead.current.text.split('').every((c) => c === typeahead.current.text[0]);
    const prefix = repeated ? (typeahead.current.text[0] ?? '') : typeahead.current.text;
    const start = repeated ? activeIndex + 1 : activeIndex;
    const ordered = [...options.keys()].map((_, i) => (start + i) % options.length);
    const match = ordered.find((i) => options[i]?.label.toLowerCase().startsWith(prefix));
    if (match === undefined) return;
    if (open) moveTo(match);
    else openMenu(match);
  }

  function handleButtonKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const { key } = event;

    if (!open) {
      // Enter y Espacio abren por el clic nativo del botón; aquí solo flechas y búsqueda por inicial.
      if (key === 'ArrowDown' || key === 'ArrowUp') {
        event.preventDefault();
        openMenu();
      } else if (key.length === 1 && key !== ' ') {
        jumpTo(key);
      }
      return;
    }

    switch (key) {
      case 'ArrowDown':
        event.preventDefault();
        moveTo(activeIndex + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        moveTo(activeIndex - 1);
        break;
      case 'Home':
        event.preventDefault();
        moveTo(0);
        break;
      case 'End':
        event.preventDefault();
        moveTo(last);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        swallowClick.current = true;
        commitOption(visibleOptions[activeIndex]);
        break;
      case 'Escape':
        // Solo cierra el menú: no debe cerrar también la hoja de filtros que lo contiene.
        event.preventDefault();
        event.stopPropagation();
        closeMenu();
        break;
      case 'Tab':
        closeMenu();
        break;
      default:
        if (key.length === 1) jumpTo(key);
    }
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const { key } = event;

    switch (key) {
      case 'ArrowDown':
        event.preventDefault();
        if (open) moveTo(activeIndex + 1);
        else openMenu();
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (open) moveTo(activeIndex - 1);
        else openMenu();
        break;
      case 'Enter':
        // Con el menú abierto elige la opción activa; cerrado, deja que el formulario se envíe.
        if (open) {
          event.preventDefault();
          commitOption(visibleOptions[activeIndex] ?? visibleOptions[0]);
        }
        break;
      case 'Escape':
        if (open) {
          event.preventDefault();
          event.stopPropagation();
          closeMenu();
        }
        break;
      case 'Tab':
        closeMenu();
        break;
      default:
    }
  }

  const sizeClass = size === 'lg' ? 'min-h-12' : 'min-h-11';
  const borderClass = invalid ? 'border-danger-border' : 'border-border';
  const listVisible = open && hasOptions;

  return (
    <div ref={rootRef}>
      <span id={labelId} className={hideLabel ? 'sr-only' : 'block font-semibold text-ink'}>
        {label}
      </span>
      {/* El menú se posiciona respecto de este contenedor (solo el campo), no de la etiqueta. */}
      <div ref={fieldRef} className="relative mt-1.5">
        {searchable ? (
          <>
            <Icon
              name="search"
              size={20}
              className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-muted"
            />
            <input
              ref={inputRef}
              type="text"
              name={name}
              role="combobox"
              aria-autocomplete="list"
              aria-expanded={open}
              aria-controls={listVisible ? listId : undefined}
              aria-labelledby={labelId}
              aria-activedescendant={listVisible ? optionId(activeIndex) : undefined}
              aria-invalid={invalid ? true : undefined}
              aria-describedby={describedBy}
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              enterKeyHint="search"
              disabled={disabled}
              value={query ?? selectedLabel}
              placeholder={searchPlaceholder ?? 'Escribe para buscar…'}
              onFocus={(event) => {
                // Lo escrito reemplaza a la elección actual en vez de añadirse a ella.
                event.currentTarget.select();
              }}
              onClick={(event) => {
                // Un clic sobre el campo ya enfocado coloca el cursor y deshace la selección: se
                // vuelve a seleccionar para que lo escrito reemplace la elección y no se le añada.
                if (query === null) event.currentTarget.select();
                if (!open) openMenu();
              }}
              onChange={(event) => {
                setQuery(event.target.value);
                setActiveIndex(0);
                if (!open) openMenu(0);
              }}
              onKeyDown={handleInputKeyDown}
              onBlur={closeMenu}
              className={`w-full rounded-lg border-2 bg-canvas pr-10 pl-10 text-base text-ink placeholder:text-ink-subtle hover:bg-surface-subtle aria-expanded:border-brand disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted ${sizeClass} ${borderClass}`}
            />
            {/* La flecha abre y cierra el menú; no se enfoca con Tab (el campo ya hace todo con teclado). */}
            <button
              type="button"
              tabIndex={-1}
              disabled={disabled}
              aria-label={`${open ? 'Cerrar' : 'Abrir'} la lista de ${label.toLowerCase()}`}
              // Que el campo no pierda el foco: perderlo cerraría el menú antes del clic.
              onMouseDown={(event) => {
                event.preventDefault();
              }}
              onClick={() => {
                if (open) {
                  closeMenu();
                } else {
                  inputRef.current?.focus();
                  openMenu();
                }
              }}
              className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-ink-muted disabled:text-ink-muted"
            >
              <Icon
                name="chevronDown"
                size={20}
                className={`transition-transform duration-150 ${open ? 'rotate-180' : ''}`}
              />
            </button>
          </>
        ) : (
          <button
            ref={buttonRef}
            type="button"
            name={name}
            role="combobox"
            aria-haspopup="listbox"
            aria-expanded={open}
            aria-controls={listVisible ? listId : undefined}
            aria-labelledby={labelId}
            aria-activedescendant={listVisible ? optionId(activeIndex) : undefined}
            aria-invalid={invalid ? true : undefined}
            aria-describedby={describedBy}
            disabled={disabled}
            onClick={() => {
              // Toque, ratón, Enter/Espacio con el menú cerrado y la activación de lectores de pantalla o
              // de voz llegan todos como clic. Solo se descarta el clic que sigue a un Enter/Espacio que
              // ya eligió una opción (en Espacio el navegador lo dispara al soltar la tecla).
              if (swallowClick.current) {
                swallowClick.current = false;
                return;
              }
              if (open) closeMenu();
              else openMenu();
            }}
            onKeyDown={handleButtonKeyDown}
            onKeyUp={() => {
              window.setTimeout(() => {
                swallowClick.current = false;
              }, 0);
            }}
            className={`flex w-full items-center justify-between gap-2 rounded-lg border-2 bg-canvas px-3 text-left text-base text-ink hover:bg-surface-subtle aria-expanded:border-brand disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted ${sizeClass} ${borderClass}`}
          >
            <span className="min-w-0 truncate">
              {options[selectedIndex]?.shortLabel ?? selectedLabel}
            </span>
            <Icon
              name="chevronDown"
              size={20}
              className={`text-ink-muted transition-transform duration-150 ${open ? 'rotate-180' : ''}`}
            />
          </button>
        )}

        {open && (
          <div
            role="presentation"
            // El foco no sale del campo: tocar una opción no debe quitárselo.
            onMouseDown={(event) => {
              event.preventDefault();
            }}
            style={{ maxHeight: menuMaxHeight }}
            className={`absolute z-30 overflow-y-auto ${
              menuWidth === 'wide' ? 'left-0 w-[min(18rem,calc(100vw-3rem))]' : 'inset-x-0'
            } overscroll-contain rounded-lg border-2 border-border bg-surface-elevated p-1.5 shadow-lg ${
              openUp ? 'bottom-full mb-1' : 'top-full mt-1'
            }`}
          >
            {hasOptions ? (
              <div id={listId} role="listbox" tabIndex={-1} aria-labelledby={labelId}>
                {visibleOptions.map((option, index) => {
                  const selected = option.value === value;
                  return (
                    <div
                      key={option.value}
                      id={optionId(index)}
                      role="option"
                      aria-selected={selected}
                      tabIndex={-1}
                      onClick={() => {
                        commitOption(option);
                      }}
                      // Normalmente el foco no sale del campo; un lector de pantalla táctil sí puede enfocar la opción.
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          commitOption(option);
                        }
                      }}
                      onMouseEnter={() => {
                        setActiveIndex(index);
                      }}
                      className={`flex min-h-11 cursor-pointer items-center justify-between gap-2 rounded-md px-3 py-2 text-base text-ink ${
                        index === activeIndex ? 'bg-brand-soft' : ''
                      } ${selected ? 'font-semibold' : ''}`}
                    >
                      <span className="min-w-0">{option.label}</span>
                      {selected && <Icon name="check" size={20} className="text-brand" />}
                    </div>
                  );
                })}
              </div>
            ) : (
              <p role="status" className="px-3 py-4 text-center text-base text-ink-muted">
                No hay resultados para “{query?.trim()}”.
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
