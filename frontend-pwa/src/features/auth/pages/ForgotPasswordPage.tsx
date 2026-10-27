import { useEffect, useRef, useState, type SubmitEvent } from 'react';
import { Link } from 'react-router';

import { useHttpClient } from '@/shared/api';
import { Button, buttonClassName, Icon, ProblemNotice, TextField } from '@/shared/ui';

import { fieldErrorsOf, type FieldErrors } from '../forms';
import { forgotPasswordSchema, requestPasswordReset } from '../passwordReset';

/** Mismo mensaje exista o no la cuenta (ADR-0014 §1). */
const SENT = 'Si el correo está registrado, te enviamos un enlace válido por 15 minutos.';

/** Pedir el enlace para restablecer la contraseña (T-1.13b, ADR-0014 §5). */
export function ForgotPasswordPage() {
  const http = useHttpClient();
  const formRef = useRef<HTMLFormElement>(null);
  const doneRef = useRef<HTMLHeadingElement>(null);
  const [errors, setErrors] = useState<FieldErrors<'email'>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  useEffect(() => {
    if (sent) doneRef.current?.focus();
  }, [sent]);

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = forgotPasswordSchema.safeParse({
      email: new FormData(event.currentTarget).get('email'),
    });
    setServerError(null);
    if (!parsed.success) {
      setErrors(fieldErrorsOf<'email'>(parsed.error.issues));
      (formRef.current?.elements.namedItem('email') as HTMLElement | null)?.focus();
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await requestPasswordReset(http, parsed.data.email);
      setSent(true);
    } catch (error) {
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <>
        <title>Revisa tu correo · Intégrate</title>
        <h1 ref={doneRef} tabIndex={-1} className="text-3xl font-bold text-brand">
          Revisa tu correo
        </h1>
        <p role="status" className="mt-3 flex items-start gap-2 text-ink">
          <Icon name="check" className="mt-0.5 text-success" />
          <span>{SENT}</span>
        </p>
        <p className="mt-3 text-ink-muted">
          Si no llega en unos minutos, revisa la carpeta de spam o pide otro enlace.
        </p>
        <Link to="/ingresar" className={buttonClassName('primary', 'mt-6 w-full')}>
          Volver a ingresar
        </Link>
      </>
    );
  }

  return (
    <>
      <title>Recuperar contraseña · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Recupera tu contraseña</h1>
      <p className="mt-2 text-ink-muted">
        Escribe el correo de tu cuenta y te enviaremos un enlace para crear una nueva.
      </p>

      <form
        ref={formRef}
        onSubmit={(event) => void handleSubmit(event)}
        noValidate
        className="mt-6 grid gap-5"
      >
        {serverError !== null && <ProblemNotice error={serverError} />}
        <TextField
          label="Correo"
          name="email"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
          error={errors.email}
        />
        {/* Acción justo bajo el campo (FE-DIR-001 Directiva 1.4, enmienda de ADR-0014 §5). */}
        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Enviando…' : 'Enviar enlace'}
        </Button>
      </form>

      <p className="mt-8 text-ink-muted">
        ¿La recordaste?{' '}
        <Link
          to="/ingresar"
          className="inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4"
        >
          Ingresar
        </Link>
      </p>
    </>
  );
}
