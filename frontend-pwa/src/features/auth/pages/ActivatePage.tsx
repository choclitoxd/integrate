import { useEffect, useId, useRef, useState, type SubmitEvent } from 'react';
import { Link, useSearchParams } from 'react-router';

import { ApiError, useHttpClient } from '@/shared/api';
import {
  Button,
  buttonClassName,
  Icon,
  PasswordField,
  ProblemNotice,
  StickyActionBar,
  TextField,
} from '@/shared/ui';

import { activateAccount, activationSchema } from '../activation';
import { fieldErrorsOf, type FieldErrors } from '../forms';

type Field = 'email' | 'code' | 'newPassword' | 'confirmation';
const FIELDS: readonly Field[] = ['email', 'code', 'newPassword', 'confirmation'];

/** Correo inexistente, código erróneo, vencido o usado: un solo mensaje (no revela cuál). */
const INVALID_CODE = 'El código no es válido, venció o ya se usó. Pide uno nuevo a tu gestor.';

/** Activación del anfitrión con el código que le entregó su gestor (T-1.12, ADR-0018 §7). */
export function ActivatePage() {
  const [params] = useSearchParams();
  const http = useHttpClient();
  const formRef = useRef<HTMLFormElement>(null);
  const doneRef = useRef<HTMLHeadingElement>(null);
  const formId = useId();
  const [errors, setErrors] = useState<FieldErrors<Field>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [activatedEmail, setActivatedEmail] = useState<string | null>(null);

  useEffect(() => {
    if (activatedEmail) doneRef.current?.focus();
  }, [activatedEmail]);

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const parsed = activationSchema.safeParse({
      email: data.get('email'),
      code: data.get('code'),
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
    const { email, code, newPassword } = parsed.data;
    try {
      await activateAccount(http, { email, code, newPassword });
      setActivatedEmail(email);
    } catch (error) {
      setServerError(error);
      // El aviso queda sobre el formulario: a 360 px quedaría fuera de la vista.
      window.scrollTo({ top: 0 });
    } finally {
      setSubmitting(false);
    }
  }

  if (activatedEmail) {
    return (
      <>
        <title>Cuenta activada · Intégrate</title>
        <h1 ref={doneRef} tabIndex={-1} className="text-3xl font-bold text-brand">
          Cuenta activada
        </h1>
        <p className="mt-3 flex items-start gap-2 text-ink">
          <Icon name="check" className="mt-0.5 text-success" />
          <span>
            Ya puedes ingresar con <span className="font-semibold break-all">{activatedEmail}</span>{' '}
            y la contraseña que acabas de crear.
          </span>
        </p>
        <Link to="/ingresar" className={buttonClassName('primary', 'mt-6 w-full')}>
          Ingresar
        </Link>
      </>
    );
  }

  const invalidCode = serverError instanceof ApiError && serverError.status === 401;

  return (
    <>
      <title>Activar cuenta · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Activa tu cuenta</h1>
      <p className="mt-2 text-ink-muted">
        Usa el código que te entregó tu gestor de enlace y crea tu propia contraseña.
      </p>

      <form
        ref={formRef}
        id={formId}
        onSubmit={(event) => void handleSubmit(event)}
        noValidate
        className="mt-6 grid gap-5"
      >
        {invalidCode ? (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-3 font-semibold text-danger-text"
          >
            <Icon name="alert" className="mt-0.5" />
            <p>{INVALID_CODE}</p>
          </div>
        ) : (
          serverError !== null && <ProblemNotice error={serverError} />
        )}
        <TextField
          label="Correo"
          hint="El que te indicó tu gestor. Si no tienes correo propio, termina en @comunidad.integrate.co."
          name="email"
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          defaultValue={params.get('correo') ?? ''}
          error={errors.email}
        />
        <TextField
          label="Código de activación"
          hint="10 letras y números, por ejemplo 7K3QD-M9X2P."
          name="code"
          autoComplete="one-time-code"
          autoCapitalize="characters"
          spellCheck={false}
          className="[&_input]:font-mono [&_input]:tracking-widest"
          error={errors.code}
        />
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
      </form>

      <StickyActionBar>
        <Button type="submit" form={formId} disabled={submitting}>
          {submitting ? 'Activando…' : 'Activar cuenta'}
        </Button>
      </StickyActionBar>
    </>
  );
}
