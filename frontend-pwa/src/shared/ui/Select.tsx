import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

import { Icon } from './Icon';

export interface SelectOption {
  readonly value: string;
  readonly label: string;
}

interface SelectProps {
  /** Rótulo visible; también es el nombre accesible del control. */
  readonly label: string;
  readonly options: readonly SelectOption[];
  readonly value: string;
  readonly onChange: (value: string) => void;
  /** Se fija en el botón: `form.elements.namedItem(name)` lo encuentra para enfocarlo al validar. */
  readonly name?: string;
  readonly disabled?: boolean;
  readonly invalid?: boolean;
  readonly describedBy?: string | undefined;
  /** `lg` (48 px) en formularios largos; `md` (44 px) en filtros. */
  readonly size?: 'md' | 'lg';
}

/** Altura máxima del menú: con menos espacio libre debajo se abre hacia arriba, y se acorta si hace falta. */
const MENU_HEIGHT_PX = 256;
const MENU_MIN_PX = 132;
const MENU_GAP_PX = 12;

/** Ancestro más cercano con scroll vertical propio; `undefined` si la que se desplaza es la página. */
function scrollParentOf(element: HTMLElement): HTMLElement | undefined {
  for (let parent = element.parentElement; parent; parent = parent.parentElement) {
    const { overflowY } = window.getComputedStyle(parent);
    if (overflowY === 'auto' || overflowY === 'scroll') return parent;
  }
  return undefined;
}
const TYPEAHEAD_RESET_MS = 700;

/**
 * Lista desplegable accesible (patrón "select-only combobox" de WAI-ARIA APG) que reemplaza al
 * `<select>` nativo: botón con flecha y menú flotante. El foco se queda en el botón y la opción
 * activa se anuncia con `aria-activedescendant`. Teclado: ↑ ↓ Inicio Fin, Enter/Espacio, Esc,
 * Tab y búsqueda escribiendo la inicial. Colores solo de tokens (FE-DIR-001).
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
}: SelectProps) {
  const baseId = useId();
  const labelId = `${baseId}-label`;
  const listId = `${baseId}-list`;
  const optionId = (index: number) => `${baseId}-option-${String(index)}`;

  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const typeahead = useRef({ text: '', timer: 0 });
  const swallowClick = useRef(false);

  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [openUp, setOpenUp] = useState(false);
  const [menuMaxHeight, setMenuMaxHeight] = useState(MENU_HEIGHT_PX);

  const selectedIndex = options.findIndex((option) => option.value === value);
  const selectedLabel = options[selectedIndex]?.label ?? '';
  const last = options.length - 1;

  // Un toque o clic fuera cierra el menú (en iOS un botón no recibe foco, así que `blur` no basta).
  useEffect(() => {
    if (!open) return;
    function closeOnOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('pointerdown', closeOnOutside);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside);
    };
  }, [open]);

  function openMenu(index = Math.max(selectedIndex, 0)) {
    const trigger = triggerRef.current;
    if (trigger) {
      const rect = trigger.getBoundingClientRect();
      // Un menú absoluto se recorta en el contenedor con scroll que lo contiene (la hoja de filtros
      // tiene el suyo), no en la pantalla: el espacio se mide contra ese contenedor.
      const scroller = scrollParentOf(trigger);
      const bounds = scroller?.getBoundingClientRect() ?? {
        top: 0,
        bottom: window.visualViewport?.height ?? window.innerHeight,
      };
      // Lo que aún se puede desplazar hacia abajo también cuenta como espacio.
      const scrollable = scroller
        ? Math.max(scroller.scrollHeight - scroller.clientHeight - scroller.scrollTop, 0)
        : 0;
      const below = bounds.bottom - rect.bottom + scrollable;
      const above = rect.top - bounds.top;
      const up = below < MENU_HEIGHT_PX && above > below;
      setOpenUp(up);
      setMenuMaxHeight(
        Math.max(Math.min((up ? above : below) - MENU_GAP_PX, MENU_HEIGHT_PX), MENU_MIN_PX),
      );
    }
    setActiveIndex(index);
    setOpen(true);
  }

  function moveTo(index: number) {
    const next = Math.min(Math.max(index, 0), last);
    setActiveIndex(next);
    document.getElementById(optionId(next))?.scrollIntoView({ block: 'nearest' });
  }

  function commit(index: number) {
    const option = options[index];
    setOpen(false);
    triggerRef.current?.focus();
    if (option && option.value !== value) onChange(option.value);
  }

  function search(character: string) {
    window.clearTimeout(typeahead.current.timer);
    typeahead.current.text += character.toLowerCase();
    typeahead.current.timer = window.setTimeout(() => {
      typeahead.current.text = '';
    }, TYPEAHEAD_RESET_MS);
    // Una misma letra repetida recorre las opciones que empiezan por ella.
    const repeated = typeahead.current.text.split('').every((c) => c === typeahead.current.text[0]);
    const needle = repeated ? (typeahead.current.text[0] ?? '') : typeahead.current.text;
    const start = repeated ? activeIndex + 1 : activeIndex;
    const ordered = [...options.keys()].map((_, i) => (start + i) % options.length);
    const match = ordered.find((i) => options[i]?.label.toLowerCase().startsWith(needle));
    if (match === undefined) return;
    if (open) moveTo(match);
    else openMenu(match);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const { key } = event;

    if (!open) {
      // Enter y Espacio abren por el clic nativo del botón; aquí solo flechas y búsqueda.
      if (key === 'ArrowDown' || key === 'ArrowUp') {
        event.preventDefault();
        openMenu();
      } else if (key.length === 1 && key !== ' ') {
        search(key);
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
        commit(activeIndex);
        break;
      case 'Escape':
        // Solo cierra el menú: no debe cerrar también la hoja de filtros que lo contiene.
        event.preventDefault();
        event.stopPropagation();
        setOpen(false);
        break;
      case 'Tab':
        setOpen(false);
        break;
      default:
        if (key.length === 1) search(key);
    }
  }

  return (
    <div ref={rootRef}>
      <span id={labelId} className="block font-semibold text-ink">
        {label}
      </span>
      {/* El menú se posiciona respecto de este contenedor (solo el botón), no de la etiqueta. */}
      <div className="relative mt-1.5">
        <button
          ref={triggerRef}
          type="button"
          name={name}
          role="combobox"
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={open ? listId : undefined}
          aria-labelledby={labelId}
          aria-activedescendant={open ? optionId(activeIndex) : undefined}
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
            if (open) setOpen(false);
            else openMenu();
          }}
          onKeyDown={handleKeyDown}
          onKeyUp={() => {
            window.setTimeout(() => {
              swallowClick.current = false;
            }, 0);
          }}
          className={`flex w-full items-center justify-between gap-2 rounded-lg border-2 bg-canvas px-3 text-left text-base text-ink hover:bg-surface-subtle aria-expanded:border-brand disabled:border-border-subtle disabled:bg-surface-subtle disabled:text-ink-muted ${
            size === 'lg' ? 'min-h-12' : 'min-h-11'
          } ${invalid ? 'border-danger-border' : 'border-border'}`}
        >
          <span className="min-w-0 truncate">{selectedLabel}</span>
          <Icon
            name="chevronDown"
            size={20}
            className={`text-ink-muted transition-transform duration-150 ${open ? 'rotate-180' : ''}`}
          />
        </button>

        {open && (
          <div
            id={listId}
            role="listbox"
            tabIndex={-1}
            aria-labelledby={labelId}
            // El foco no sale del botón: tocar una opción no debe quitárselo.
            onMouseDown={(event) => {
              event.preventDefault();
            }}
            style={{ maxHeight: menuMaxHeight }}
            className={`absolute inset-x-0 z-30 overflow-y-auto overscroll-contain rounded-lg border-2 border-border bg-surface-elevated p-1.5 shadow-lg ${
              openUp ? 'bottom-full mb-1' : 'top-full mt-1'
            }`}
          >
            {options.map((option, index) => {
              const selected = index === selectedIndex;
              return (
                <div
                  key={option.value}
                  id={optionId(index)}
                  role="option"
                  aria-selected={selected}
                  tabIndex={-1}
                  onClick={() => {
                    commit(index);
                  }}
                  // Normalmente el foco no sale del botón; un lector de pantalla táctil sí puede enfocar la opción.
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      commit(index);
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
        )}
      </div>
    </div>
  );
}
