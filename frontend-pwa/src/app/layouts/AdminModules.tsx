import { useState } from 'react';
import { NavLink, useMatch } from 'react-router';
import { BottomSheet, Icon } from '@/shared/ui';
import type { NavItem } from './shell';

export function AdminModules({
  items,
  mobile = false,
}: {
  items: readonly NavItem[];
  mobile?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const active = !!useMatch('/admin/:module');
  const style = mobile
    ? 'flex min-h-14 w-full min-w-11 flex-col items-center justify-center gap-0.5 rounded-lg px-2 text-xs font-semibold'
    : 'inline-flex min-h-11 min-w-11 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-lg px-3 text-sm font-semibold hover:bg-brand-hover';
  return (
    <>
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        className={`${style} ${active ? 'bg-brand-soft text-brand' : ''}`}
        onClick={() => {
          setOpen(true);
        }}
      >
        <Icon name="compass" size={22} /> Módulos
      </button>
      <BottomSheet
        open={open}
        title="Módulos de administración"
        onClose={() => {
          setOpen(false);
        }}
      >
        <ul className="grid gap-2">
          {items.map((item) => (
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
      </BottomSheet>
    </>
  );
}
