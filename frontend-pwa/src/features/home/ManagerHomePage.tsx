import { Link } from 'react-router';

import { Icon, type IconName } from '@/shared/ui';

const SHORTCUTS: readonly { to: string; title: string; body: string; icon: IconName }[] = [
  {
    to: '/gestor/anfitriones/enrolamiento',
    title: 'Enrolar anfitrión',
    body: 'Registra a una persona de la comunidad con su autorización firmada.',
    icon: 'plus',
  },
  {
    to: '/gestor/anfitriones',
    title: 'Mis anfitriones',
    body: 'Consulta su estado y entrega el código para activar la cuenta.',
    icon: 'users',
  },
];

/** Panel del Gestor de Enlace; la operación offline de la jornada llega en la Fase 2. */
export function ManagerHomePage() {
  return (
    <>
      <title>Panel de jornada · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Panel de jornada</h1>
      <p className="mt-2 text-ink-muted">
        Pronto descargarás aquí el padrón del día para validar vouchers sin conexión.
      </p>
      <ul className="mt-6 grid gap-3">
        {SHORTCUTS.map((item) => (
          <li key={item.to}>
            <Link
              to={item.to}
              className="flex min-h-11 items-start gap-3 rounded-xl border-2 border-border-subtle bg-canvas px-4 py-4 hover:border-brand hover:bg-brand-soft"
            >
              <span className="rounded-lg bg-brand-soft p-2 text-brand">
                <Icon name={item.icon} size={24} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-lg font-bold text-ink">{item.title}</span>
                <span className="mt-0.5 block text-ink-muted">{item.body}</span>
              </span>
              <Icon name="chevronRight" className="mt-1 text-ink-muted" />
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
