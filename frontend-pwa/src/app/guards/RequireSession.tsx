import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router';

import { returnQuery } from '@/features/auth';
import { useSession, type Role } from '@/shared/session';
import { buttonClassName } from '@/shared/ui';

interface RequireSessionProps {
  /** Basta con uno de estos roles; vacío = cualquier sesión. */
  readonly roles?: readonly Role[];
  readonly children: ReactNode;
}

/**
 * Protección de rutas en el cliente (ADR-0013 §6). Es solo experiencia de usuario: la autorización
 * real la hace el backend (deny-by-default).
 */
export function RequireSession({ roles = [], children }: RequireSessionProps) {
  const session = useSession();
  const location = useLocation();

  if (session.status === 'checking') {
    return (
      <p role="status" className="px-4 py-12 text-center font-semibold text-ink-muted">
        Verificando tu sesión…
      </p>
    );
  }
  if (session.status === 'anonymous') {
    return <Navigate to={`/ingresar${returnQuery(location.pathname + location.search)}`} replace />;
  }
  if (roles.length > 0 && !roles.some((role) => session.user.roles.includes(role))) {
    return (
      <main className="mx-auto max-w-md px-4 py-12">
        <title>Sin acceso · Intégrate</title>
        <h1 className="text-2xl font-bold text-brand">
          Esta sección no está disponible para tu cuenta
        </h1>
        <p className="mt-3 text-ink-muted">
          Si crees que deberías tener acceso, pide al equipo de Intégrate que revise tus permisos.
        </p>
        <Link to="/" className={buttonClassName('secondary', 'mt-6')}>
          Ir al inicio
        </Link>
      </main>
    );
  }
  return children;
}
