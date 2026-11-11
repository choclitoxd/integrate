import { lazy, Suspense } from 'react';
import { ScrollRestoration } from 'react-router';

import { useFormFactor } from '@/shared/lib';

import { RouteLoading } from '../pages/RouteLoading';
import { MobileShell } from './MobileShell';
import { accountLast } from './navOrder';
import type { ShellProps } from './shell';

export type { NavItem, ShellHandle } from './shell';

// El shell de escritorio viaja en su propio chunk: un teléfono nunca lo descarga (ADR-0020 §5).
const DesktopShell = lazy(() =>
  import('./DesktopShell').then((module) => ({ default: module.DesktopShell })),
);

/** Elige el shell por factor de forma (ADR-0020 §3); las URLs son las mismas en ambos. */
export function AppShell(rawProps: ShellProps) {
  const formFactor = useFormFactor();
  // «Mi cuenta» siempre a la derecha de todo, lo agregue quien lo agregue (ver navOrder.ts).
  const props = { ...rawProps, navItems: accountLast(rawProps.navItems) };
  return (
    <>
      {formFactor === 'desktop' ? (
        <Suspense fallback={<RouteLoading />}>
          <DesktopShell {...props} />
        </Suspense>
      ) : (
        <MobileShell {...props} />
      )}
      <ScrollRestoration />
    </>
  );
}
