import { useLayoutEffect, useRef, useState, type SubmitEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { ApiError, useHttpClient } from '@/shared/api';
import { useSession, useSessionStore } from '@/shared/session';
import { Button, buttonClassName, Icon, PasswordField, ProblemNotice } from '@/shared/ui';

import { fieldErrorsOf, type FieldErrors } from '../forms';
import { resetPassword, resetPasswordSchema, resetTokenFromHash } from '../passwordReset';

type Field = 'newPassword' | 'confirmation';
const FIELDS: readonly Field[] = ['newPassword', 'confirmation'];

/** Aviso que `/ingresar` muestra tras restablecer (estado de navegación, no URL). */
export const PASSWORD_RESET_NOTICE = 'Tu contraseña cambió. Ingresa con la nueva.';

function InvalidLink() {
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-3 font-semibold text-danger-text"
    >
      <Icon name="alert" className="mt-0.5" />
      <p>El enlace venció o ya se usó. Pide uno nuevo: dura 15 minutos.</p>
    </div>
  );
}

/** Crear la nueva contraseña con el enlace del correo (T-1.13b, ADR-0014 §3 y §5). */
export function ResetPasswordPage() {
  const http = useHttpClient();
  const navigate = useNavigate();
  const session = useSession();
  const store = useSessionStore();
  const location = useLocation();
  // Se lee una sola vez y se borra el fragmento antes de pintar (ADR-0014 §3).
  const [token] = useState(() => resetTokenFromHash(location.hash));
  useLayoutEffect(() => {
    if (location.hash) {
      void navigate({ pathname: location.pathname, search: location.search }, { replace: true });
    }
  }, [location.hash, location.pathname, location.search, navigate]);
  const formRef = useRef<HTMLFormElement>(null);
  const [errors, setErrors] = useState<FieldErrors<Field>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    const data = new FormData(event.currentTarget);
    const parsed = resetPasswordSchema.safeParse({
      newPassword: data.get('newPassword'),
      confirmation: data.get('confirmation'),
    });
    setServerError(null);
    if (!parsed.success) {
      const next = fieldErrorsOf<Field>(parsed.error.issues);
      setErrors(next);
      const first = FIELDS.find((field) => next[field]);
      if (first) (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await resetPassword(http, { token, newPassword: parsed.data.newPassword });
      // El backend revocó todas las sesiones (ADR-0014 §4): se cierra también la local.
      if (session.status === 'authenticated') await store.signOut().catch(() => undefined);
      void navigate('/ingresar', { replace: true, state: { notice: PASSWORD_RESET_NOTICE } });
    } catch (error) {
      setServerError(error);
      setSubmitting(false);
      window.scrollTo({ top: 0 });
    }
  }

  const invalid = token === null || (serverError instanceof ApiError && serverError.status === 401);

  return (
    <>
      <title>Nueva contraseña · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Crea tu nueva contraseña</h1>

      {invalid ? (
        <div className="mt-6 grid gap-5">
          <InvalidLink />
          <Link to="/recuperar-contrasena" className={buttonClassName('primary', 'w-full')}>
            Pedir otro enlace
          </Link>
        </div>
      ) : (
        <form
          ref={formRef}
          onSubmit={(event) => void handleSubmit(event)}
          noValidate
          className="mt-6 grid gap-5"
        >
          {serverError !== null && <ProblemNotice error={serverError} />}
          <PasswordField
            label="Nueva contraseña"
            hint="Mínimo 12 caracteres. Una frase que recuerdes con facilidad es más segura que símbolos raros."
            name="newPassword"
            autoComplete="new-password"
            error={errors.newPassword}
          />
          <PasswordField
            label="Repite la contraseña"
            name="confirmation"
            autoComplete="new-password"
            error={errors.confirmation}
          />
          {/* Acción justo bajo los campos (FE-DIR-001 Directiva 1.4, enmienda de ADR-0014 §5). */}
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? 'Guardando…' : 'Guardar contraseña'}
          </Button>
        </form>
      )}
    </>
  );
}
