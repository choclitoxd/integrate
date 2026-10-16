import { useRef, useState, type SubmitEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';

import { ApiError, useHttpClient } from '@/shared/api';
import { useSession, useSessionStore, type ConsentPurpose } from '@/shared/session';
import {
  Button,
  Icon,
  PasswordField,
  ProblemNotice,
  Select,
  Skeleton,
  TextField,
} from '@/shared/ui';

import { registerTourist, type DocType } from '../api';
import { ConsentCheckbox } from '../components/ConsentCheckbox';
import { PolicyLink } from '../components/PolicyLink';
import {
  DOC_TYPES,
  fieldErrorsOf,
  NUMERIC_DOC_TYPES,
  registerSchema,
  serverFieldErrors,
  type FieldErrors,
} from '../forms';
import { useCurrentPolicy } from '../queries';
import { returnQuery, safeReturnPath } from '../returnPath';

type Field =
  | 'fullName'
  | 'docType'
  | 'docNumber'
  | 'phone'
  | 'email'
  | 'password'
  | 'personalData'
  | 'healthEmergency';

const FIELDS: readonly Field[] = [
  'fullName',
  'docType',
  'docNumber',
  'phone',
  'email',
  'password',
  'personalData',
  'healthEmergency',
];

/** Registro de turistas con consentimiento informado y granular (T-1.11, ADR-0013 §5). */
export function RegisterPage() {
  const [params] = useSearchParams();
  const destination = safeReturnPath(params.get('volver'));
  const navigate = useNavigate();
  const session = useSession();
  const store = useSessionStore();
  const http = useHttpClient();
  const policy = useCurrentPolicy();
  const formRef = useRef<HTMLFormElement>(null);

  const [docType, setDocType] = useState<DocType>('CC');
  const [personalData, setPersonalData] = useState(false);
  const [healthEmergency, setHealthEmergency] = useState(false);
  const [errors, setErrors] = useState<FieldErrors<Field>>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  if (session.status === 'authenticated' && !submitting) {
    return <Navigate to={destination} replace />;
  }

  function focusFirst(next: FieldErrors<Field>) {
    const first = FIELDS.find((field) => next[field]);
    if (first) (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!policy.data) return;
    const data = new FormData(event.currentTarget);
    const parsed = registerSchema.safeParse({
      fullName: data.get('fullName'),
      docType,
      docNumber: data.get('docNumber'),
      phone: data.get('phone'),
      email: data.get('email'),
      password: data.get('password'),
      personalData,
      healthEmergency,
    });
    setServerError(null);
    if (!parsed.success) {
      const next = fieldErrorsOf<Field>(parsed.error.issues);
      setErrors(next);
      focusFirst(next);
      return;
    }
    setErrors({});
    setSubmitting(true);
    const form = parsed.data;
    const purposes: ConsentPurpose[] = form.healthEmergency
      ? ['PERSONAL_DATA', 'HEALTH_EMERGENCY']
      : ['PERSONAL_DATA'];
    try {
      await registerTourist(http, {
        email: form.email,
        password: form.password,
        fullName: form.fullName,
        docType: form.docType,
        docNumber: form.docNumber,
        phone: form.phone,
        consent: { policyVersion: policy.data.version, purposes },
      });
    } catch (error) {
      handleRegisterError(error);
      return;
    }
    // El registro no inicia sesión (ADR-0005): /login es el único emisor de tokens. Si este paso
    // falla, la cuenta ya existe: reintentar el registro daría 409, así que se va a "Ingresar".
    try {
      await store.signIn({ email: form.email, password: form.password });
      void navigate(destination, { replace: true });
    } catch {
      void navigate(`/ingresar${returnQuery(destination)}`, { replace: true });
    }
  }

  function handleRegisterError(error: unknown) {
    setSubmitting(false);
    if (error instanceof ApiError && error.status === 422 && error.invalidParams.length > 0) {
      const mapped = serverFieldErrors(error.invalidParams, FIELDS, {
        'consent.purposes': 'personalData',
      });
      setErrors(mapped.fields);
      focusFirst(mapped.fields);
      if (mapped.unmatched.length > 0) setServerError(error);
      return;
    }
    setServerError(error);
  }

  const duplicate = serverError instanceof ApiError && serverError.status === 409;

  return (
    <>
      <title>Crear cuenta · Intégrate</title>
      <h1 className="text-3xl font-bold text-brand">Crea tu cuenta</h1>
      <p className="mt-2 text-ink-muted">
        Te toma un minuto. Con ella reservas y recibes tus vouchers.
      </p>

      <form
        ref={formRef}
        onSubmit={(event) => void handleSubmit(event)}
        noValidate
        className="mt-6 grid gap-5"
      >
        {duplicate ? (
          <div
            role="alert"
            className="rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-3 text-danger-text"
          >
            <p className="flex items-start gap-2 font-semibold">
              <Icon name="alert" className="mt-0.5" />
              Ya existe una cuenta con ese correo o documento.
            </p>
            <Link
              to={`/ingresar${returnQuery(destination)}`}
              className="mt-1 inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4"
            >
              Ingresar con esa cuenta
            </Link>
          </div>
        ) : (
          serverError !== null && <ProblemNotice error={serverError} />
        )}

        <TextField
          label="Nombre completo"
          name="fullName"
          autoComplete="name"
          autoCapitalize="words"
          error={errors.fullName}
        />

        <div className="grid gap-3">
          <Select
            label="Tipo de documento"
            name="docType"
            size="lg"
            value={docType}
            options={DOC_TYPES}
            onChange={(next) => {
              setDocType(next as DocType);
            }}
          />
          <TextField
            label="Número de documento"
            name="docNumber"
            inputMode={NUMERIC_DOC_TYPES.includes(docType) ? 'numeric' : 'text'}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            error={errors.docNumber}
          />
        </div>

        <TextField
          label="Celular"
          hint="Con indicativo del país. Te escribiremos solo por tus reservas."
          name="phone"
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          defaultValue="+57 "
          error={errors.phone}
        />
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
        <PasswordField
          label="Contraseña"
          hint="Mínimo 12 caracteres. Una frase que recuerdes con facilidad es más segura que símbolos raros."
          name="password"
          autoComplete="new-password"
          error={errors.password}
        />

        <fieldset className="grid gap-4">
          <legend className="text-lg font-bold text-ink">Autorización de datos</legend>
          {policy.isPending && <Skeleton className="h-6 w-3/4" />}
          {policy.data && <PolicyLink policy={policy.data} />}
          {policy.isError && (
            <ProblemNotice
              error={policy.error}
              onRetry={() => {
                void policy.refetch();
              }}
            />
          )}
          <ConsentCheckbox
            purpose="PERSONAL_DATA"
            name="personalData"
            required
            checked={personalData}
            onChange={setPersonalData}
            error={errors.personalData}
          />
          <ConsentCheckbox
            purpose="HEALTH_EMERGENCY"
            name="healthEmergency"
            checked={healthEmergency}
            onChange={setHealthEmergency}
          />
          <p className="text-sm text-ink-muted">
            Si no autorizas los datos de salud ahora, te los pediremos al reservar.
          </p>
        </fieldset>

        {/* Acción justo bajo los campos y el consentimiento, igual que en "Ingresar". */}
        <div>
          <Button type="submit" disabled={submitting || !policy.data} className="w-full">
            {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
          </Button>
          {policy.isError && (
            <p className="mt-2 text-sm text-ink-muted">
              No podemos crear cuentas sin la política de datos vigente.
            </p>
          )}
        </div>
      </form>

      <p className="mt-8 text-ink-muted">
        ¿Ya tienes cuenta?{' '}
        <Link
          to={`/ingresar${returnQuery(destination)}`}
          className="inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4"
        >
          Ingresa
        </Link>
      </p>
    </>
  );
}
