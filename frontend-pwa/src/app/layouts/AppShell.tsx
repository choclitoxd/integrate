import { lazy, Suspense } from 'react';
import { ScrollRestoration } from 'react-router';

import { useFormFactor } from '@/shared/lib';

import { RouteLoading } from '../pages/RouteLoading';
import { MobileShell } from './MobileShell';
import type { ShellProps } from './shell';

export type { NavItem, ShellHandle } from './shell';

// El shell de escritorio viaja en su propio chunk: un teléfono nunca lo descarga (ADR-0020 §5).
const DesktopShell = lazy(() =>
  import('./DesktopShell').then((module) => ({ default: module.DesktopShell })),
);

/** Elige el shell por factor de forma (ADR-0020 §3); las URLs son las mismas en ambos. */
export function AppShell(props: ShellProps) {
  const formFactor = useFormFactor();
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
