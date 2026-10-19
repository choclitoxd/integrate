import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SubmitEvent } from 'react';
import { Link, useBlocker, useNavigate, useSearchParams } from 'react-router';

import { ConsentDialog, useMyConsents } from '@/features/auth';
import {
  catalogKeys,
  MAX_PARTICIPANTS,
  useAvailability,
  type ScheduleAvailability,
} from '@/features/catalog';
import { ApiError, NetworkError, useHttpClient } from '@/shared/api';
import { formatCop, formatLocalTime, formatLongDate, pluralize } from '@/shared/lib';
import { useSession } from '@/shared/session';
import {
  BottomSheet,
  Button,
  buttonClassName,
  Icon,
  ProblemNotice,
  Skeleton,
  TextField,
} from '@/shared/ui';

import { lockSlots, type BookingLockRequest } from '../api';
import { TravelerCard } from '../components/TravelerCard';
import { EPS_SUGGESTIONS } from '../eps';
import {
  bookingFormSchema,
  emptyTraveler,
  errorsByPath,
  serverErrorsByPath,
  toLockRequest,
  type TravelerDraft,
} from '../forms';
import { createIntentKeys } from '../intentKey';

const RETRY_AFTER_MS = 1000;
const ORDERED_FIELDS = ['fullName', 'docNumber', 'medicalEps'] as const;

const sleep = (ms: number) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

/** Conflicto del lock que se explica con un enlace de vuelta a las franjas. */
interface SlotConflict {
  readonly available: number | null;
}

function parseParams(params: URLSearchParams) {
  const experienceId = params.get('experiencia') ?? '';
  const scheduleId = params.get('franja') ?? '';
  const people = Number(params.get('personas'));
  const valid =
    experienceId !== '' &&
    scheduleId !== '' &&
    Number.isInteger(people) &&
    people >= 1 &&
    people <= MAX_PARTICIPANTS;
  return { experienceId, scheduleId, people, valid };
}

function SlotSummary({
  schedule,
  title,
  people,
  unitPrice,
}: {
  schedule: ScheduleAvailability;
  title: string;
  people: number;
  unitPrice: number;
}) {
  return (
    <section aria-label="Resumen de la reserva" className="mt-4 rounded-xl bg-surface px-4 py-4">
      <p className="text-lg font-bold text-ink">{title}</p>
      <p className="mt-1 flex items-center gap-2 text-ink-muted first-letter:uppercase">
        <Icon name="calendar" size={18} className="text-brand" />
        {formatLongDate(schedule.localDate)} · {formatLocalTime(schedule.localStartTime)} –{' '}
        {formatLocalTime(schedule.localEndTime)}
      </p>
      <p className="mt-1 flex items-center gap-2 text-ink-muted">
        <Icon name="users" size={18} className="text-brand" />
        {pluralize(people, 'persona', 'personas')} · {formatCop(unitPrice)} c/u
      </p>
    </section>
  );
}

/** Formulario de reserva (T-1.10c, ADR-0015). Los datos de los viajeros viven solo aquí, en memoria. */
export function BookingFormPage() {
  const [params] = useSearchParams();
  const { experienceId, scheduleId, people, valid } = parseParams(params);
  const availability = useAvailability(experienceId);
  const consents = useMyConsents();
  const session = useSession();
  const http = useHttpClient();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const epsListId = useId();
  const conflictId = useId();
  const failureId = useId();
  const formRef = useRef<HTMLFormElement>(null);

  const holderName = session.status === 'authenticated' ? session.user.fullName : '';
  const [travelers, setTravelers] = useState<TravelerDraft[]>(() =>
    Array.from({ length: valid ? people : 1 }, (_, index) =>
      emptyTraveler(index === 0 ? holderName : ''),
    ),
  );
  const [emergencyName, setEmergencyName] = useState('');
  const [emergencyPhone, setEmergencyPhone] = useState('+57 ');
  const [dirty, setDirty] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [failure, setFailure] = useState<unknown>(null);
  const [conflict, setConflict] = useState<SlotConflict | null>(null);
  const [consentFor, setConsentFor] = useState<{ policyVersion?: string } | null>(null);

  const intentKeys = useRef(createIntentKeys());
  const pendingBody = useRef<BookingLockRequest | null>(null);
  const leaving = useRef(false);

  // Aviso al salir con datos sin enviar (ADR-0015 §4): navegación interna y cierre de pestaña.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && !leaving.current && currentLocation.pathname !== nextLocation.pathname,
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

  // Si el servidor rechaza el envío, el aviso puede quedar fuera de pantalla (el de cupos va arriba del
  // formulario y el de errores abajo, tapado por la barra fija): se lleva a la vista para que quien
  // toca «Apartar cupos» siempre vea la respuesta.
  useEffect(() => {
    const target = conflict !== null ? conflictId : failure !== null ? failureId : null;
    if (target === null) return;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    document
      .getElementById(target)
      ?.scrollIntoView({ block: 'center', behavior: reduceMotion ? 'auto' : 'smooth' });
  }, [conflict, failure, conflictId, failureId]);

  const franjasPath = `/experiencias/${experienceId}/franjas`;
  // Cupos de la experiencia en caché: se refrescan tras un lock o un conflicto de cupos.
  const availabilityPrefix = catalogKeys.availabilityOf(experienceId);

  if (!valid) {
    return (
      <>
        <title>Reserva · Intégrate</title>
        <h1 className="text-2xl font-bold text-brand">Falta elegir la franja</h1>
        <p className="mt-3 text-ink-muted">
          Elige una experiencia, la fecha y el horario para reservar.
        </p>
        <Link to="/" className={buttonClassName('secondary', 'mt-6')}>
          Ver experiencias
        </Link>
      </>
    );
  }

  const schedule = availability.data?.schedules.find((item) => item.scheduleId === scheduleId);
  const slotProblem =
    availability.data && !schedule
      ? 'gone'
      : schedule && schedule.availableSlots < people
        ? 'insufficient'
        : null;
  const healthMissing =
    consents.data?.consents.find((item) => item.purpose === 'HEALTH_EMERGENCY')?.state !==
      'VALID' && consents.isSuccess;

  function touch() {
    setDirty(true);
    if (failure !== null) setFailure(null);
  }

  function focusFirstError(next: Record<string, string>) {
    const ordered = [
      ...travelers.flatMap((_, index) =>
        ORDERED_FIELDS.map((field) => `participants.${String(index)}.${field}`),
      ),
      'emergencyName',
      'emergencyPhone',
    ];
    const first = ordered.find((key) => next[key]);
    if (first) (formRef.current?.elements.namedItem(first) as HTMLElement | null)?.focus();
  }

  async function send(body: BookingLockRequest, attempt = 0): Promise<void> {
    const key = intentKeys.current.keyFor(body);
    setSending(true);
    setFailure(null);
    setConflict(null);
    try {
      const booking = await lockSlots(http, body, key);
      leaving.current = true;
      void queryClient.invalidateQueries({ queryKey: availabilityPrefix });
      void navigate(`/reservas/${booking.bookingId}`, { replace: true });
    } catch (error) {
      setSending(false);
      setNotice(null);
      if (!(error instanceof ApiError)) {
        setFailure(error);
        return;
      }
      if (error.status === 428) {
        pendingBody.current = body;
        const version = error.problem.policyVersion;
        setConsentFor(typeof version === 'string' ? { policyVersion: version } : {});
        return;
      }
      if (error.status === 503 && attempt === 0) {
        setNotice('Hay mucha demanda en esta franja, reintentando…');
        setSending(true);
        await sleep(RETRY_AFTER_MS);
        await send(body, 1);
        return;
      }
      if (error.code === 'BOOKING_INSUFFICIENT_SLOTS') {
        const left = error.problem.availableSlots;
        setConflict({ available: typeof left === 'number' ? left : null });
        void queryClient.invalidateQueries({ queryKey: availabilityPrefix });
        return;
      }
      if (error.code === 'BOOKING_SCHEDULE_NOT_BOOKABLE' || error.status === 404) {
        setConflict({ available: 0 });
        return;
      }
      if (error.code === 'IDEMPOTENCY_KEY_REUSED') intentKeys.current.reset();
      if (error.status === 422 && error.invalidParams.length > 0) {
        const mapped = serverErrorsByPath(error.invalidParams);
        setErrors(mapped.fields);
        focusFirstError(mapped.fields);
        if (mapped.unmatched.length === 0) return;
      }
      setFailure(error);
    }
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending) return;
    const parsed = bookingFormSchema.safeParse({
      participants: travelers,
      emergencyName,
      emergencyPhone,
    });
    if (!parsed.success) {
      const next = errorsByPath(parsed.error.issues);
      setErrors(next);
      focusFirstError(next);
      return;
    }
    setErrors({});
    const body = toLockRequest(scheduleId, parsed.data);
    // Precondición HEALTH_EMERGENCY (ADR-0015 §5): se pide antes de enviar; el 428 queda de respaldo.
    if (healthMissing) {
      pendingBody.current = body;
      setConsentFor({});
      return;
    }
    void send(body);
  }

  const failureMessage =
    failure instanceof NetworkError
      ? 'Sin conexión. Tus datos siguen aquí: revisa la señal e inténtalo de nuevo.'
      : null;

  return (
    <>
      <title>Datos de los viajeros · Intégrate</title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-brand">
        Datos de los viajeros
      </h1>

      {availability.isPending && <Skeleton className="mt-4 h-24 w-full" />}
      {schedule && availability.data && (
        <SlotSummary
          schedule={schedule}
          title={availability.data.title}
          people={people}
          unitPrice={availability.data.unitPriceCop}
        />
      )}

      {(slotProblem !== null || conflict !== null) && (
        <div
          id={conflictId}
          role="alert"
          className="mt-4 rounded-xl border-2 border-warning-border bg-warning-bg px-4 py-3 text-warning-text"
        >
          <p className="font-semibold">
            {slotProblem === 'gone' || conflict?.available === 0
              ? 'Esta franja ya no está disponible.'
              : conflict?.available != null
                ? `Solo quedan ${pluralize(conflict.available, 'cupo', 'cupos')} en esta franja.`
                : `Ya no hay cupo para ${pluralize(people, 'persona', 'personas')} en esta franja.`}
          </p>
          <Link
            to={franjasPath}
            className="mt-1 inline-flex min-h-11 items-center font-semibold text-brand underline underline-offset-4"
          >
            Elegir otra franja o ajustar el grupo
          </Link>
        </div>
      )}

      {healthMissing && (
        <div className="mt-4 flex items-start gap-2 rounded-xl border-2 border-info-border bg-info-bg px-4 py-3 text-info-text">
          <Icon name="shield" className="mt-0.5" />
          <p>
            Para reservar necesitamos tu autorización para tratar la EPS y el contacto de
            emergencia. Te la pediremos al apartar los cupos.
          </p>
        </div>
      )}

      <p className="mt-4 text-sm text-ink-muted">
        Estos datos no se guardan en tu teléfono. Si sales de esta página antes de apartar los
        cupos, se borrarán.
      </p>

      <form ref={formRef} onSubmit={handleSubmit} noValidate className="mt-5 grid gap-6">
        <datalist id={epsListId}>
          {EPS_SUGGESTIONS.map((eps) => (
            <option key={eps} value={eps} />
          ))}
        </datalist>

        {travelers.map((traveler, index) => (
          <TravelerCard
            key={index}
            index={index}
            total={travelers.length}
            value={traveler}
            epsListId={epsListId}
            errors={errors}
            onChange={(next) => {
              touch();
              setTravelers((current) => current.map((item, i) => (i === index ? next : item)));
            }}
          />
        ))}

        <fieldset className="grid gap-4 rounded-xl border-2 border-border-subtle px-4 pt-2 pb-4">
          <legend className="px-1 text-lg font-bold text-ink">Contacto de emergencia</legend>
          <p className="text-sm text-ink-muted">
            Alguien que no viaje con el grupo y pueda responder.
          </p>
          <TextField
            label="Nombre completo"
            name="emergencyName"
            autoComplete="off"
            autoCapitalize="words"
            value={emergencyName}
            onChange={(event) => {
              touch();
              setEmergencyName(event.target.value);
            }}
            error={errors.emergencyName}
          />
          <TextField
            label="Celular"
            name="emergencyPhone"
            type="tel"
            inputMode="tel"
            autoComplete="off"
            value={emergencyPhone}
            onChange={(event) => {
              touch();
              setEmergencyPhone(event.target.value);
            }}
            error={errors.emergencyPhone}
          />
        </fieldset>

        {notice && (
          <p role="status" className="font-semibold text-ink-muted">
            {notice}
          </p>
        )}
        {failure !== null && (
          <div id={failureId}>
            {failureMessage ? (
              <div
                role="alert"
                className="flex items-start gap-2 rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-3 font-semibold text-danger-text"
              >
                <Icon name="wifiOff" className="mt-0.5" />
                <p>{failureMessage}</p>
              </div>
            ) : (
              <ProblemNotice error={failure} />
            )}
          </div>
        )}

        {/* Acción justo bajo los datos, con el total encima: igual que en "Ingresar" y "Crear cuenta". */}
        <div className="grid gap-3">
          {availability.data && (
            <p className="flex items-center justify-between gap-3 rounded-xl bg-surface px-4 py-3">
              <span className="text-ink-muted">
                Total · {pluralize(people, 'persona', 'personas')}
              </span>
              <span className="text-xl font-bold text-ink tabular-nums">
                {formatCop(availability.data.unitPriceCop * people)}
              </span>
            </p>
          )}
          <Button type="submit" disabled={sending} className="w-full">
            {sending ? 'Apartando…' : 'Apartar cupos'}
          </Button>
        </div>
      </form>

      <ConsentDialog
        open={consentFor !== null}
        mode="optional"
        purposes={['HEALTH_EMERGENCY']}
        policyVersion={consentFor?.policyVersion}
        onGranted={() => {
          setConsentFor(null);
          void consents.refetch();
          const body = pendingBody.current;
          pendingBody.current = null;
          if (body) void send(body);
        }}
        onDecline={() => {
          setConsentFor(null);
          pendingBody.current = null;
        }}
      />

      <BottomSheet
        open={blocker.state === 'blocked'}
        onClose={() => {
          blocker.reset?.();
        }}
        title="¿Salir sin apartar los cupos?"
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
          Los datos de los viajeros no se guardan: si sales ahora, se borrarán.
        </p>
      </BottomSheet>
    </>
  );
}
