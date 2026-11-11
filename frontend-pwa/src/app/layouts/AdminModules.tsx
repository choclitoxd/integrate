import { useState } from 'react';
import { NavLink, useMatch } from 'react-router';
import { BottomSheet, Icon, type IconName } from '@/shared/ui';
import type { NavItem } from './shell';

interface Group {
  readonly name: string | undefined;
  readonly items: NavItem[];
}

function groupItems(items: readonly NavItem[]): Group[] {
  const groups: Group[] = [];
  for (const item of items) {
    const group = groups.find((candidate) => candidate.name === item.group);
    if (group) group.items.push(item);
    else groups.push({ name: item.group, items: [item] });
  }
  return groups;
}

/**
 * Botón «Módulos» con su hoja inferior: ADR-0044 lo creó para el administrador y ADR-0067 lo
 * comparte con el anfitrión y el gestor. Sin `active`, conserva el criterio del administrador.
 */
export function AdminModules({
  items,
  mobile = false,
  title = 'Módulos de administración',
  icon = 'compass',
  active,
}: {
  items: readonly NavItem[];
  mobile?: boolean;
  title?: string;
  icon?: IconName;
  active?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const adminActive = !!useMatch('/admin/:module');
  const isActive = active ?? adminActive;
  const groups = groupItems(items);
  const headings = groups.length > 1;
  const style = mobile
    ? 'flex min-h-14 w-full min-w-11 flex-col items-center justify-center gap-0.5 rounded-lg px-2 text-xs font-semibold'
    : 'inline-flex min-h-11 min-w-11 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-lg px-3 text-sm font-semibold hover:bg-brand-hover';
  const list = (entries: readonly NavItem[]) => (
    <ul className="grid gap-2">
      {entries.map((item) => (
        <li key={item.to}>
          <NavLink
            to={item.to}
            end
            className="flex min-h-11 items-center gap-2 rounded-lg px-3 font-semibold text-brand hover:bg-brand-soft aria-[current=page]:bg-brand-soft"
            onClick={() => {
              setOpen(false);
            }}
          >
            <Icon name={item.icon} size={20} /> {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  );
  return (
    <>
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        className={`${style} ${isActive ? 'bg-brand-soft text-brand' : ''}`}
        onClick={() => {
          setOpen(true);
        }}
      >
        <Icon name={icon} size={22} /> Módulos
      </button>
      <BottomSheet
        open={open}
        title={title}
        onClose={() => {
          setOpen(false);
        }}
      >
        {headings ? (
          <div className="grid gap-4">
            {groups.map((group) => (
              <section key={group.name ?? 'modulos'}>
                {group.name && (
                  <h3 className="mb-2 px-3 text-sm font-bold text-ink-muted">{group.name}</h3>
                )}
                {list(group.items)}
              </section>
            ))}
          </div>
        ) : (
          list(items)
        )}
      </BottomSheet>
    </>
  );
}
