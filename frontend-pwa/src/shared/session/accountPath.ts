import { hasRole, type SessionState } from './sessionStore';

/**
 * «Mi cuenta» de la zona del rol (ADR-0062, enmendado por ADR-0064): el rol de zona manda sobre el de turista,
 * porque todo anfitrión y gestor es también turista. Orden: administrador, anfitrión, gestor y, al final, turista.
 * Sin sesión no hay cuenta: lleva a ingresar.
 */
export function accountPathFor(session: SessionState): string {
  if (session.status !== 'authenticated') return '/ingresar';
  if (hasRole(session, 'ROLE_ADMIN')) return '/admin/cuenta';
  if (hasRole(session, 'ROLE_HOST')) return '/anfitrion/cuenta';
  if (hasRole(session, 'ROLE_LINK_MANAGER')) return '/gestor/cuenta';
  return '/turista/cuenta';
}
