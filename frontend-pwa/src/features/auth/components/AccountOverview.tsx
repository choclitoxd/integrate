import { useState } from 'react';

import { formatPhoneDisplay } from '@/shared/lib';
import { Button, ProblemNotice } from '@/shared/ui';

import { useAccountProfile } from '../queries';
import { PasswordSheet, ProfileSheet } from './AccountSettings';

/**
 * Datos de la cuenta (ADR-0062 enmendado): se ven en «Mi cuenta» y se editan en ventanas emergentes.
 * Editar nombre y celular no pide contraseña; cambiar la contraseña sí (la pide su propia ventana).
 */
export function AccountOverview() {
  const profile = useAccountProfile();
  const [editing, setEditing] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [saved, setSaved] = useState(false);

  return (
    <section aria-labelledby="account-settings" className="mt-8">
      <h2 id="account-settings" className="text-xl font-bold text-ink">
        Datos de mi cuenta
      </h2>
      {profile.isPending && <p className="mt-3">Cargando datos…</p>}
      {profile.isError && (
        <div className="mt-3">
          <ProblemNotice
            error={profile.error}
            onRetry={() => {
              void profile.refetch();
            }}
          />
        </div>
      )}
      {profile.data && (
        <dl className="mt-3 grid gap-3 rounded-xl bg-surface px-4 py-4">
          <div>
            <dt className="text-sm text-ink-muted">Nombre completo</dt>
            <dd className="font-semibold text-ink">{profile.data.fullName}</dd>
          </div>
          <div>
            <dt className="text-sm text-ink-muted">Celular</dt>
            <dd className="font-semibold text-ink tabular-nums">
              {profile.data.phone ? formatPhoneDisplay(profile.data.phone) : 'Sin registrar'}
            </dd>
          </div>
        </dl>
      )}
      {saved && (
        <p role="status" className="mt-3 font-semibold text-success-text">
          Tus datos se guardaron.
        </p>
      )}
      <div className="mt-3 grid gap-3">
        <Button
          variant="secondary"
          disabled={!profile.data}
          onClick={() => {
            setSaved(false);
            setEditing(true);
          }}
        >
          Editar mis datos
        </Button>
        <Button
          variant="secondary"
          onClick={() => {
            setSaved(false);
            setChangingPassword(true);
          }}
        >
          Cambiar contraseña
        </Button>
      </div>
      {profile.data && (
        <ProfileSheet
          open={editing}
          profile={profile.data}
          onClose={() => {
            setEditing(false);
          }}
          onSaved={() => {
            setEditing(false);
            setSaved(true);
          }}
        />
      )}
      <PasswordSheet
        open={changingPassword}
        onClose={() => {
          setChangingPassword(false);
        }}
      />
    </section>
  );
}
