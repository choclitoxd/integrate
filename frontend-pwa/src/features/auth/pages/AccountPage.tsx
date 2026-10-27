import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { useSession, useSessionStore, type Role } from '@/shared/session';
import { Button, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import { ConsentDialog } from '../components/ConsentDialog';
import { CONSENT_STATE_LABEL, PURPOSE_COPY } from '../purposes';
import { useMyConsents } from '../queries';

const ROLE_LABEL: Readonly<Record<Role, string>> = {
  ROLE_TOURIST: 'Turista',
  ROLE_HOST: 'Anfitrión comunitario',
  ROLE_LINK_MANAGER: 'Gestor de Enlace',
  ROLE_ADMIN: 'Administrador',
};

/**
 * Mi cuenta: datos básicos, autorizaciones de datos y cierre de sesión (T-1.11). "Mis experiencias"
 * tiene su propia pantalla y su pestaña en la barra (`/mis-experiencias`, ADR-0023 §2 enmendado).
 */
export function AccountPage() {
  const session = useSession();
  const store = useSessionStore();
  const navigate = useNavigate();
  const consents = useMyConsents();
  const [granting, setGranting] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const consentsHeading = useRef<HTMLHeadingElement>(null);

  if (session.status !== 'authenticated') return null;
  const { user } = session;
  const health = consents.data?.consents.find((item) => item.purpose === 'HEALTH_EMERGENCY');

  return (
    <>
      <title>Mi cuenta · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Mi cuenta</h1>

      <section aria-labelledby="perfil" className="mt-6 rounded-xl bg-surface px-4 py-4">
        <h2 id="perfil" className="sr-only">
          Perfil
        </h2>
        <p className="text-xl font-bold text-ink">{user.fullName}</p>
        <p className="mt-0.5 break-all text-ink-muted">{user.email}</p>
        <p className="mt-2 text-sm font-semibold text-ink-muted">
          {user.roles.map((role) => ROLE_LABEL[role]).join(' · ')}
        </p>
      </section>

      <section aria-labelledby="autorizaciones" className="mt-8">
        <h2
          id="autorizaciones"
          ref={consentsHeading}
          tabIndex={-1}
          className="text-xl font-bold text-ink"
        >
          Autorizaciones de datos
        </h2>
        {consents.isPending && (
          <div className="mt-3 grid gap-2" aria-hidden="true">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        )}
        {consents.isError && (
          <div className="mt-3">
            <ProblemNotice
              error={consents.error}
              onRetry={() => {
                void consents.refetch();
              }}
            />
          </div>
        )}
        {consents.data && (
          <ul className="mt-3 grid gap-2">
            {consents.data.consents.map((item) => {
              const valid = item.state === 'VALID';
              return (
                <li
                  key={item.purpose}
                  className="flex items-center justify-between gap-3 rounded-xl border-2 border-border-subtle px-4 py-3"
                >
                  <span className="font-semibold text-ink">{PURPOSE_COPY[item.purpose].title}</span>
                  <span
                    className={`inline-flex shrink-0 items-center gap-1 text-sm font-semibold ${
                      valid ? 'text-success-text' : 'text-warning-text'
                    }`}
                  >
                    <Icon name={valid ? 'shield' : 'alert'} size={18} />
                    {CONSENT_STATE_LABEL[item.state]}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
        {health && health.state !== 'VALID' && (
          <Button
            variant="secondary"
            className="mt-3 w-full"
            onClick={() => {
              setGranting(true);
            }}
          >
            Autorizar datos de salud para reservar
          </Button>
        )}
      </section>

      <Button
        variant="secondary"
        className="mt-10 w-full"
        disabled={signingOut}
        onClick={() => {
          setSigningOut(true);
          void store.signOut().then(() => navigate('/', { replace: true }));
        }}
      >
        {signingOut ? 'Cerrando sesión…' : 'Cerrar sesión'}
      </Button>

      <ConsentDialog
        open={granting}
        mode="optional"
        purposes={['HEALTH_EMERGENCY']}
        onGranted={() => {
          setGranting(false);
          // El botón que abrió el diálogo desaparece: el foco va al resultado, no al inicio de la página.
          setTimeout(() => consentsHeading.current?.focus(), 0);
        }}
        onDecline={() => {
          setGranting(false);
        }}
      />
    </>
  );
}
