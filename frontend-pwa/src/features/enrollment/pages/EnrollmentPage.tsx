import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type ReactNode, type SubmitEvent } from 'react';
import { Link, useBlocker } from 'react-router';

import { DOC_TYPES, NUMERIC_DOC_TYPES, PolicyLink, useCurrentPolicy } from '@/features/auth';
import { ApiError, NetworkError, useHttpClient } from '@/shared/api';
import {
  BottomSheet,
  Button,
  buttonClassName,
  Icon,
  ProblemNotice,
  Skeleton,
  StickyActionBar,
  TextField,
} from '@/shared/ui';

import { registerAssisted, type AssistedRegistration } from '../api';
import { ActivationCodeIssuer } from '../components/ActivationCodeIssuer';
import { ConnectionBadge } from '../components/ConnectionBadge';
import { DocumentUpload } from '../components/DocumentUpload';
import { useOnlineStatus } from '../connectivity';
import {
  EMPTY_DOCUMENT,
  emptyDraft,
  ENROLLMENT_FIELDS,
  serverErrorsOf,
  validateEnrollment,
  VICTIMIZATION_OTHER,
  VICTIMIZATION_TYPES,
  withVictimAnswer,
  type EnrollmentDraft,
  type EnrollmentField,
} from '../forms';
import { enrollmentKeys } from '../queries';

type Errors = Partial<Record<EnrollmentField, string>>;

const VICTIM_FIELDS: readonly EnrollmentField[] = [
  'victim',
  'ruvRegistrationNumber',
  'victimizationType',
  'victimizationOther',
  'supportDoc',
];

function withoutErrors(errors: Errors, fields: readonly EnrollmentField[]): Errors {
  return Object.fromEntries(
    Object.entries(errors).filter(([field]) => !fields.includes(field as EnrollmentField)),
  );
}

const SELECT_CLASS =
  'mt-1.5 block min-h-12 w-full rounded-lg border-2 bg-canvas px-3 text-base text-ink';

/** Bloque de error bajo un control que no es TextField. */
function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={id} className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-danger-text">
      <Icon name="alert" size={18} className="mt-px" />
      {children}
    </p>
  );
}

/** Casilla de autorización en tercera persona: el gestor la marca tras leerla con el anfitrión. */
function AssistedConsentCheckbox({
  name,
  title,
  body,
  checked,
  onChange,
  error,
}: {
  name: string;
  title: string;
  body: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  error: string | undefined;
}) {
  const bodyId = useId();
  const errorId = useId();
  return (
    <div>
      <label
        className={`flex cursor-pointer items-start gap-3 rounded-xl border-2 px-4 py-3 has-checked:border-brand has-checked:bg-brand-soft ${
          error ? 'border-danger-border' : 'border-border'
        }`}
      >
        <input
          type="checkbox"
          name={name}
          checked={checked}
          onChange={(event) => {
            onChange(event.target.checked);
          }}
          aria-describedby={[bodyId, error ? errorId : null].filter(Boolean).join(' ')}
          aria-invalid={error ? true : undefined}
          className="mt-0.5 size-6 shrink-0 accent-brand"
        />
        <span className="font-semibold text-ink">{title} (obligatorio)</span>
      </label>
      <p id={bodyId} className="mt-1.5 pl-1 text-sm text-ink-muted">
        {body}
      </p>
      {error && <FieldError id={errorId}>{error}</FieldError>}
    </div>
  );
}

/** Pantalla tras el 201: los datos de víctima ya no existen en el teléfono (ADR-0018 §5). */
function EnrollmentResult({
  fullName,
  registration,
  onAnother,
}: {
  fullName: string;
  registration: AssistedRegistration;
  onAnother: () => void;
}) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    headingRef.current?.focus();
  }, []);
  return (
    <>
      <title>Anfitrión registrado · Intégrate</title>
      <h1 ref={headingRef} tabIndex={-1} className="text-3xl font-bold text-brand">
        Anfitrión registrado
      </h1>
      <dl className="mt-4 grid gap-3 rounded-xl bg-surface px-4 py-4">
        <div>
          <dt className="text-sm text-ink-muted">Nombre</dt>
          <dd className="font-semibold text-ink">{fullName}</dd>
        </div>
        <div>
          <dt className="text-sm text-ink-muted">Correo de acceso</dt>
          <dd className="font-semibold break-all text-ink">{registration.email}</dd>
          {registration.emailIsSynthetic && (
            <dd className="mt-0.5 text-sm text-ink-muted">
              Correo interno asignado por Intégrate: no recibe mensajes. El anfitrión recupera el
              acceso con un código tuyo.
            </dd>
          )}
        </div>
        <div>
          <dt className="text-sm text-ink-muted">Estado</dt>
          <dd className="font-semibold text-ink">Pendiente de activación</dd>
        </div>
      </dl>

      <section aria-labelledby="activacion" className="mt-6">
        <h2 id="activacion" className="text-xl font-bold text-ink">
          Entrega el acceso
        </h2>
        <p className="mt-1 mb-3 text-ink-muted">
          Con el código, el anfitrión crea su contraseña. Vence en 72 horas.
        </p>
        <ActivationCodeIssuer
          hostProfileId={registration.hostProfileId}
          email={registration.email}
        />
      </section>

      <div className="mt-8 grid gap-3">
        <Button variant="secondary" onClick={onAnother}>
          Enrolar a otra persona
        </Button>
        <Link to="/gestor/anfitriones" className={buttonClassName('quiet')}>
          Ver mis anfitriones
        </Link>
      </div>
    </>
  );
}

/** Enrolamiento asistido de anfitriones desde el teléfono del gestor (T-1.12, ADR-0018 §2). */
export function EnrollmentPage() {
  const http = useHttpClient();
  const queryClient = useQueryClient();
  const policy = useCurrentPolicy();
  const online = useOnlineStatus();
  const formId = useId();
  const docTypeId = useId();
  const victimErrorId = useId();
  const victimizationId = useId();
  const victimizationErrorId = useId();
  const formRef = useRef<HTMLFormElement>(null);

  const [draft, setDraft] = useState<EnrollmentDraft>(emptyDraft);
  const [dirty, setDirty] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [failure, setFailure] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{
    fullName: string;
    registration: AssistedRegistration;
  } | null>(null);

  // Aviso al salir con datos sin enviar (ADR-0015 §4); nada se guarda en el teléfono.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && currentLocation.pathname !== nextLocation.pathname,
  );
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => {
      window.removeEventListener('beforeunload', warn);
    };
  }, [dirty]);

  if (result) {
    return (
      <EnrollmentResult
        {...result}
        onAnother={() => {
          setResult(null);
          window.scrollTo({ top: 0 });
        }}
      />
    );
  }

  function update(patch: Partial<EnrollmentDraft>) {
    setDraft((current) => ({ ...current, ...patch }));
    setDirty(true);
    const touched = Object.keys(patch) as EnrollmentField[];
    if (touched.some((field) => errors[field])) {
      setErrors((current) => withoutErrors(current, touched));
    }
  }

  function focusFirst(next: Errors) {
    const first = ENROLLMENT_FIELDS.find((field) => next[field]);
    if (!first) return;
    const element = formRef.current?.elements.namedItem(first);
    const target = element instanceof RadioNodeList ? element[0] : element;
    (target as HTMLElement | null | undefined)?.focus();
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!policy.data || !online || submitting) return;
    const validation = validateEnrollment(draft, policy.data.version);
    setFailure(null);
    if (!validation.ok) {
      setErrors(validation.errors);
      focusFirst(validation.errors);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      const registration = await registerAssisted(http, validation.body);
      void queryClient.invalidateQueries({ queryKey: enrollmentKeys.hosts });
      setResult({ fullName: validation.body.fullName, registration });
      setDraft(emptyDraft());
      setDirty(false);
    } catch (error) {
      handleFailure(error);
    } finally {
      setSubmitting(false);
    }
  }

  function handleFailure(error: unknown) {
    if (error instanceof ApiError && error.status === 422 && error.invalidParams.length > 0) {
      const mapped = serverErrorsOf(error.invalidParams);
      // Un PDF rechazado ya no sirve: se vacía para subirlo otra vez.
      setDraft((current) => ({
        ...current,
        ...(mapped.errors.supportDoc && { supportDoc: EMPTY_DOCUMENT }),
        ...(mapped.errors.consentForm && { consentForm: EMPTY_DOCUMENT }),
      }));
      setErrors(mapped.errors);
      focusFirst(mapped.errors);
      if (mapped.unmatched > 0) setFailure(error);
      return;
    }
    setFailure(error);
    window.scrollTo({ top: 0 });
  }

  const docNumeric = NUMERIC_DOC_TYPES.includes(draft.docType);
  const victim = draft.victim === 'yes';

  return (
    <>
      <title>Enrolar anfitrión · Intégrate</title>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-3xl font-bold text-brand">Enrolar anfitrión</h1>
        <ConnectionBadge online={online} />
      </div>
      <p className="mt-2 text-ink-muted">
        Registra a la persona con su autorización. Nada de lo que escribas se guarda en este
        teléfono.
      </p>

      <nav aria-label="Secciones del formulario" className="mt-4">
        <ol className="flex flex-wrap gap-2">
          {[
            ['#datos', '1. Datos'],
            ['#victima', '2. Condición'],
            ['#consentimiento', '3. Autorización'],
          ].map(([href, label]) => (
            <li key={href}>
              <a
                href={href}
                className="inline-flex min-h-11 items-center rounded-full border-2 border-border px-3 text-sm font-semibold text-ink hover:bg-surface-subtle"
              >
                {label}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <form
        ref={formRef}
        id={formId}
        onSubmit={(event) => void handleSubmit(event)}
        noValidate
        className="mt-6 grid gap-8"
      >
        {failure !== null && <EnrollmentFailure error={failure} />}

        <section aria-labelledby="datos" className="grid scroll-mt-20 gap-5">
          <h2 id="datos" className="scroll-mt-20 text-xl font-bold text-ink">
            1. Datos del anfitrión
          </h2>
          <TextField
            label="Nombre completo"
            name="fullName"
            autoComplete="off"
            autoCapitalize="words"
            value={draft.fullName}
            onChange={(event) => {
              update({ fullName: event.target.value });
            }}
            error={errors.fullName}
          />
          <div className="grid gap-3">
            <div>
              <label htmlFor={docTypeId} className="block font-semibold text-ink">
                Tipo de documento
              </label>
              <select
                id={docTypeId}
                name="docType"
                value={draft.docType}
                onChange={(event) => {
                  update({ docType: event.target.value });
                }}
                className={`${SELECT_CLASS} border-border`}
              >
                {DOC_TYPES.map((type) => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
            </div>
            <TextField
              label="Número de documento"
              name="docNumber"
              inputMode={docNumeric ? 'numeric' : 'text'}
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              value={draft.docNumber}
              onChange={(event) => {
                update({ docNumber: event.target.value });
              }}
              error={errors.docNumber}
            />
          </div>
          <TextField
            label="Celular"
            hint="Con indicativo del país, por ejemplo +57 300 123 4567."
            name="phone"
            type="tel"
            inputMode="tel"
            autoComplete="off"
            value={draft.phone}
            onChange={(event) => {
              update({ phone: event.target.value });
            }}
            error={errors.phone}
          />
          <TextField
            label="Correo (opcional)"
            hint="Si no tiene correo, se le asigna uno interno y recupera el acceso con un código tuyo."
            name="email"
            type="email"
            inputMode="email"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            value={draft.email}
            onChange={(event) => {
              update({ email: event.target.value });
            }}
            error={errors.email}
          />
        </section>

        <section aria-labelledby="victima" className="grid gap-5">
          <h2 id="victima" className="scroll-mt-20 text-xl font-bold text-ink">
            2. Condición de víctima
          </h2>
          <fieldset aria-describedby={errors.victim ? victimErrorId : undefined}>
            <legend className="font-semibold text-ink">
              ¿La persona es víctima del conflicto armado (Ley 1448)?
            </legend>
            <p className="mt-0.5 text-sm text-ink-muted">
              Es un dato sensible: se cifra y nadie lo ve en la aplicación.
            </p>
            <div className="mt-2 grid grid-cols-2 gap-3">
              {(
                [
                  ['yes', 'Sí'],
                  ['no', 'No'],
                ] as const
              ).map(([value, label]) => (
                <label
                  key={value}
                  className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-xl border-2 px-4 font-semibold text-ink has-checked:border-brand has-checked:bg-brand-soft ${
                    errors.victim ? 'border-danger-border' : 'border-border'
                  }`}
                >
                  <input
                    type="radio"
                    name="victim"
                    value={value}
                    checked={draft.victim === value}
                    onChange={() => {
                      setDraft((current) => withVictimAnswer(current, value));
                      setDirty(true);
                      setErrors((current) => withoutErrors(current, VICTIM_FIELDS));
                    }}
                    className="size-6 shrink-0 accent-brand"
                  />
                  {label}
                </label>
              ))}
            </div>
            {errors.victim && <FieldError id={victimErrorId}>{errors.victim}</FieldError>}
          </fieldset>

          {victim && (
            <div className="grid gap-5 border-l-4 border-brand-border pl-4">
              <TextField
                label="Número de registro RUV (opcional)"
                hint="Como aparece en la certificación de la Unidad para las Víctimas."
                name="ruvRegistrationNumber"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={40}
                value={draft.ruvRegistrationNumber}
                onChange={(event) => {
                  update({ ruvRegistrationNumber: event.target.value });
                }}
                error={errors.ruvRegistrationNumber}
              />
              <div>
                <label htmlFor={victimizationId} className="block font-semibold text-ink">
                  Hecho victimizante (opcional)
                </label>
                <select
                  id={victimizationId}
                  name="victimizationType"
                  value={draft.victimizationType}
                  onChange={(event) => {
                    update({ victimizationType: event.target.value, victimizationOther: '' });
                  }}
                  aria-invalid={errors.victimizationType ? true : undefined}
                  aria-describedby={errors.victimizationType ? victimizationErrorId : undefined}
                  className={`${SELECT_CLASS} ${
                    errors.victimizationType ? 'border-danger-border' : 'border-border'
                  }`}
                >
                  <option value="">Sin especificar</option>
                  {VICTIMIZATION_TYPES.map((type) => (
                    <option key={type.value} value={type.value}>
                      {type.label}
                    </option>
                  ))}
                  <option value={VICTIMIZATION_OTHER}>Otro (especificar)</option>
                </select>
                {errors.victimizationType && (
                  <FieldError id={victimizationErrorId}>{errors.victimizationType}</FieldError>
                )}
              </div>
              {draft.victimizationType === VICTIMIZATION_OTHER && (
                <TextField
                  label="¿Cuál hecho?"
                  name="victimizationOther"
                  autoComplete="off"
                  maxLength={80}
                  value={draft.victimizationOther}
                  onChange={(event) => {
                    update({ victimizationOther: event.target.value });
                  }}
                  error={errors.victimizationOther}
                />
              )}
              <DocumentUpload
                kind="VICTIM_SUPPORT_DOC"
                name="supportDoc"
                title="Documento soporte"
                hint="Certificación RUV u otro soporte, en PDF de hasta 5 MB."
                value={draft.supportDoc}
                onChange={(supportDoc) => {
                  update({ supportDoc });
                }}
                error={errors.supportDoc}
              />
            </div>
          )}
        </section>

        <section aria-labelledby="consentimiento" className="grid gap-5">
          <h2 id="consentimiento" className="scroll-mt-20 text-xl font-bold text-ink">
            3. Autorización de datos
          </h2>
          <div className="rounded-xl bg-surface px-4 py-3">
            <p className="text-ink">
              Lee la política con la persona, o entrégale el texto impreso, y confirma que entiende
              cada autorización antes de marcarla.
            </p>
            {policy.isPending && <Skeleton className="mt-2 h-6 w-3/4" />}
            {policy.data && <PolicyLink policy={policy.data} />}
          </div>
          {policy.isError && (
            <ProblemNotice
              error={policy.error}
              onRetry={() => {
                void policy.refetch();
              }}
            />
          )}
          <AssistedConsentCheckbox
            name="personalData"
            title="La persona autoriza el tratamiento de sus datos personales"
            body="Nombre, documento, celular y correo, para crear su cuenta de anfitrión y coordinar las experiencias."
            checked={draft.personalData}
            onChange={(personalData) => {
              update({ personalData });
            }}
            error={errors.personalData}
          />
          <AssistedConsentCheckbox
            name="sensitiveVictim"
            title="La persona autoriza el tratamiento de su condición de víctima"
            body="Dato sensible (Ley 1448): se cifra y solo lo usa su asociación para el registro. Se pide en todo enrolamiento, sea o no víctima."
            checked={draft.sensitiveVictim}
            onChange={(sensitiveVictim) => {
              update({ sensitiveVictim });
            }}
            error={errors.sensitiveVictim}
          />
          <DocumentUpload
            kind="PHYSICAL_CONSENT_FORM"
            name="consentForm"
            required
            title="Formato de consentimiento firmado"
            hint="Escanéalo como PDF (máximo 5 MB) con la opción «Escanear» de Google Drive, Archivos o Notas."
            value={draft.consentForm}
            onChange={(consentForm) => {
              update({ consentForm });
            }}
            error={errors.consentForm}
          />
        </section>
      </form>

      <StickyActionBar
        note={
          !online
            ? 'El enrolamiento necesita conexión. Tus datos siguen aquí.'
            : policy.isError
              ? 'No se puede enrolar sin la política de datos vigente.'
              : undefined
        }
      >
        <Button type="submit" form={formId} disabled={submitting || !online || !policy.data}>
          {submitting ? 'Registrando…' : 'Registrar anfitrión'}
        </Button>
      </StickyActionBar>

      <BottomSheet
        open={blocker.state === 'blocked'}
        onClose={() => {
          blocker.reset?.();
        }}
        title="¿Salir sin registrar?"
        footer={
          <>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => {
                blocker.proceed?.();
              }}
            >
              Salir
            </Button>
            <Button
              className="flex-[2]"
              onClick={() => {
                blocker.reset?.();
              }}
            >
              Seguir llenando
            </Button>
          </>
        }
      >
        <p className="text-ink">
          Los datos del anfitrión no se guardan en el teléfono: si sales ahora, se borrarán.
        </p>
      </BottomSheet>
    </>
  );
}

/** Errores del envío con el mensaje de la acción que corresponde (ADR-0018 §5). */
function EnrollmentFailure({ error }: { error: unknown }) {
  if (error instanceof ApiError && (error.status === 403 || error.status === 409)) {
    const message =
      error.status === 409
        ? 'Ya existe una cuenta con ese documento o correo. Búscala en "Mis anfitriones" o revisa los datos.'
        : 'Tu acreditación como gestor no está activa. Pide a la administración de Intégrate que la revise.';
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
  return (
    <div className="grid gap-2">
      <ProblemNotice error={error} />
      {error instanceof NetworkError && (
        <p className="text-sm font-semibold text-ink">
          Tus datos siguen aquí: envíalos cuando vuelva la señal.
        </p>
      )}
    </div>
  );
}
