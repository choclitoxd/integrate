import { useRef, useState, type SubmitEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';

import { ApiError } from '@/shared/api';
import { formatClockTime } from '@/shared/lib';
import { useSession, useSessionStore } from '@/shared/session';
import { Button, Icon, PasswordField, ProblemNotice, TextField } from '@/shared/ui';

import { fieldErrorsOf, loginSchema, type FieldErrors } from '../forms';
import { returnQuery, safeReturnPath } from '../returnPath';

type Field = 'email' | 'password';

/** 423: hora de desbloqueo desde `lockedUntil` (o 15 min si no llega). */
function unlockTime(error: ApiError): string {
  const raw = error.problem.lockedUntil;
  const until = typeof raw === 'string' ? Date.parse(raw) : Number.NaN;
  return formatClockTime(Number.isNaN(until) ? Date.now() + 15 * 60_000 : until);
}

function LoginError({ error }: { error: unknown }) {
  if (
    error instanceof ApiError &&
    (error.status === 401 || error.status === 423 || error.status === 403)
  ) {
    const message =
      error.status === 401
        ? 'El correo o la contraseña no coinciden. Revísalos e inténtalo de nuevo.'
        : error.status === 423
          ? `Por seguridad bloqueamos la cuenta tras varios intentos. A partir de las ${unlockTime(error)} podrás intentarlo de nuevo.`
          : 'Esta cuenta está suspendida. Comunícate con el equipo de Intégrate para revisarla.';
    return (
      <div
        role="alert"
        className="flex items-start gap-2 rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-3 font-semibold text-danger-text"
      >
        <Icon name="alert" className="mt-0.5" />
        <p>{message}</p>
      </div>
    );
  }
  return <ProblemNotice error={error} />;
}

/** Inicio de sesión móvil (T-1.11, ADR-0013 §5). */
export function LoginPage() {
  const [params] = useSearchParams();
  const destination = safeReturnPath(params.get('volver'));
  // Aviso de otra pantalla (p. ej. "Tu contraseña cambió"), por estado de navegación y no por URL.
  const { state } = useLocation() as { state: { notice?: unknown } | null };
  const notice = typeof state?.notice === 'string' ? state.notice : null;
  const navigate = useNavigate();
  const session = useSession();
  const store = useSessionStore();
  const formRef = useRef<HTMLFormElement>(null);
  const [errors, setErrors] = useState<FieldErrors<Field>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  if (session.status === 'authenticated' && !submitting) {
    return <Navigate to={destination} replace />;
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const parsed = loginSchema.safeParse({
      email: data.get('email'),
      password: data.get('password'),
    });
    setServerError(null);
    if (!parsed.success) {
      const next = fieldErrorsOf<Field>(parsed.error.issues);
      setErrors(next);
      const first = Object.keys(next)[0];
      if (first) (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await store.signIn(parsed.data);
      void navigate(destination, { replace: true });
    } catch (error) {
      setServerError(error);
      setSubmitting(false);
    }
  }

  return (
    <>
      <title>Ingresar · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Ingresa a tu cuenta</h1>
      <p className="mt-2 text-ink-muted">
        Con tu cuenta reservas experiencias y recibes tus vouchers.
      </p>

      <form
        ref={formRef}
        onSubmit={(event) => void handleSubmit(event)}
        noValidate
        className="mt-6 grid gap-5"
      >
        {notice !== null && serverError === null && (
          <p
            role="status"
            className="flex items-start gap-2 rounded-xl border-2 border-success-border bg-success-bg px-4 py-3 font-semibold text-success-text"
          >
            <Icon name="check" className="mt-0.5" />
            <span>{notice}</span>
          </p>
        )}
        {serverError !== null && <LoginError error={serverError} />}
        <TextField
          label="Correo"
          name="email"
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          error={errors.email}
        />
        <div className="grid gap-1">
          <PasswordField
            label="Contraseña"
            name="password"
            autoComplete="current-password"
            error={errors.password}
          />
          <Link
            to="/recuperar-contrasena"
            className="inline-flex min-h-11 items-center justify-self-start font-semibold text-brand underline underline-offset-4"
          >
            ¿Olvidaste tu contraseña?
          </Link>
        </div>
        {/* Acción justo bajo los campos: un formulario de dos campos no necesita barra fija. */}
        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Ingresando…' : 'Ingresar'}
        </Button>
      </form>

      <p className="mt-8 text-ink-muted">
        ¿Aún no tienes cuenta?{' '}
        <Link
          to={`/registro${returnQuery(destination)}`}
          className="inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4"
        >
          Crea una gratis
        </Link>
      </p>
    </>
  );
}
