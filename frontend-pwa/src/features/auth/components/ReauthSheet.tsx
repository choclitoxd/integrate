import { useRef, useState, type SubmitEvent } from 'react';
import { Link } from 'react-router';
import { ApiError, useHttpClient } from '@/shared/api';
import { BottomSheet, Button, PasswordField, ProblemNotice } from '@/shared/ui';
import { reauthenticate, type ReauthGrant } from '../api';

interface ReauthSheetProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onConfirmed: (grant: ReauthGrant) => void;
}

/**
 * Pide la contraseña antes de editar la cuenta propia (ADR-0058). El token que devuelve el servidor
 * habilita la edición por unos minutos; aquí no se guarda nada fuera de la memoria del componente padre.
 */
export function ReauthSheet({ open, onClose, onConfirmed }: ReauthSheetProps) {
  return (
    <BottomSheet open={open} onClose={onClose} title="Confirma que eres tú">
      <ReauthForm onConfirmed={onConfirmed} />
    </BottomSheet>
  );
}

function ReauthForm({ onConfirmed }: { readonly onConfirmed: (grant: ReauthGrant) => void }) {
  const http = useHttpClient();
  const ref = useRef<HTMLFormElement>(null);
  const [error, setError] = useState<string | undefined>(undefined);
  const [serverError, setServerError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = new FormData(event.currentTarget).get('reauthPassword');
    const password = typeof value === 'string' ? value : '';
    setServerError(null);
    if (!password) {
      setError('Escribe tu contraseña.');
      (ref.current?.elements.namedItem('reauthPassword') as HTMLElement | null)?.focus();
      return;
    }
    setError(undefined);
    setBusy(true);
    try {
      onConfirmed(await reauthenticate(http, password));
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 422) {
        setError('La contraseña no es correcta.');
        (ref.current?.elements.namedItem('reauthPassword') as HTMLElement | null)?.focus();
      } else {
        setServerError(failure);
      }
      setBusy(false);
    }
  }

  return (
    <form
      ref={ref}
      noValidate
      className="grid gap-4"
      onSubmit={(event) => {
        void submit(event);
      }}
    >
      <p className="text-ink-muted">
        Por tu seguridad, escribe tu contraseña para editar los datos de tu cuenta. La confirmación
        dura unos minutos.
      </p>
      <PasswordField
        name="reauthPassword"
        label="Contraseña"
        autoComplete="current-password"
        error={error}
      />
      {serverError !== null && <ProblemNotice error={serverError} />}
      <Button type="submit" disabled={busy}>
        {busy ? 'Confirmando…' : 'Confirmar'}
      </Button>
      <Link
        to="/recuperar-contrasena"
        className="inline-flex min-h-11 items-center justify-center font-semibold text-brand underline underline-offset-4"
      >
        ¿Olvidaste tu contraseña?
      </Link>
    </form>
  );
}
