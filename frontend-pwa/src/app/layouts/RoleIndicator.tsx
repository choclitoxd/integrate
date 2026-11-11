import { hasRole, useSession } from '@/shared/session';

/** Muestra los roles disponibles sin implicar que el usuario haya cambiado de rol. */
export function RoleIndicator() {
  const session = useSession();
  const roles = [
    ...(hasRole(session, 'ROLE_HOST') ? ['Anfitrión'] : []),
    ...(hasRole(session, 'ROLE_LINK_MANAGER') ? ['Gestor de enlace'] : []),
  ];
  if (roles.length === 0) return null;

  return (
    <p
      className="text-xs font-semibold text-on-brand"
      aria-label={`Tus roles: ${roles.join(', ')}`}
    >
      {roles.join(' · ')}
    </p>
  );
}
