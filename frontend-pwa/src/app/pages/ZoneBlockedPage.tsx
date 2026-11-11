import { Link } from 'react-router';

import type { ZoneStatus } from '../layouts/zoneStatus';

type Zone = 'gestor' | 'host';
type Blocked = Exclude<ZoneStatus, 'ACTIVE'>;

const COPY: Readonly<Record<Zone, Readonly<Record<Blocked, { title: string; body: string }>>>> = {
  gestor: {
    SUSPENDED: {
      title: 'Tu acreditación está suspendida',
      body: 'Mientras esté suspendida no puedes enrolar anfitriones ni usar el padrón. Comunícate con la administradora de la plataforma para reactivarla.',
    },
    REVOKED: {
      title: 'Tu acreditación fue revocada',
      body: 'Dejaste de ser gestor de enlace. Si crees que es un error, comunícate con la administradora de la plataforma.',
    },
  },
  host: {
    SUSPENDED: {
      title: 'Tu perfil de anfitrión está suspendido',
      body: 'Mientras esté suspendido no puedes crear ni publicar experiencias ni gestionar franjas, y tus experiencias no aparecen en el catálogo. Comunícate con la administradora de la plataforma.',
    },
    REVOKED: {
      title: 'Tu perfil de anfitrión fue revocado',
      body: 'Dejaste de ser anfitrión. Si crees que es un error, comunícate con la administradora de la plataforma.',
    },
  },
};

/** Aviso en lugar del panel de quien está suspendido o revocado (ADR-0069 §2, ADR-0070 §4). */
export function ZoneBlockedPage({ zone, status }: { zone: Zone; status: Blocked }) {
  const copy = COPY[zone][status];
  return (
    <>
      <title>{`${copy.title} · Intégrate`}</title>
      <h1 className="text-3xl font-bold text-brand">{copy.title}</h1>
      <p className="mt-4 text-lg text-ink-muted">{copy.body}</p>
      <p className="mt-6">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4"
        >
          Volver a explorar
        </Link>
      </p>
    </>
  );
}
