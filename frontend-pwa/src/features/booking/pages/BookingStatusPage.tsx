import { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';

import { useAvailability, useExperience } from '@/features/catalog';
import { ApiError, useHttpClient } from '@/shared/api';
import { formatCop, formatLocalTime, formatLongDate, pluralize } from '@/shared/lib';
import { Button, buttonClassName, Icon, ProblemNotice, Skeleton } from '@/shared/ui';

import {
  simulatePayment,
  startPayment,
  type BookingStatus,
  type PaymentCheckout,
  type PaymentMethod,
} from '../api';
import { CountdownBanner } from '../components/CountdownBanner';
import { MockGatewayPanel } from '../components/MockGatewayPanel';
import { PaymentMethodPicker } from '../components/PaymentMethodPicker';
import { TripSheet } from '../components/TripSheet';
import { VoucherPanel } from '../components/VoucherPanel';
import { monotonicNow, useServerCountdown } from '../countdown';
import { useBooking, type BookingView } from '../queries';
import { externalNavigation, wompiCheckoutUrl } from '../wompi';

/** Sondeo del resultado del pago (ADR-0016 §4): cada 3 s, máximo 2 min. */
const VERIFY_POLL_MS = 3000;
const VERIFY_LIMIT_MS = 120_000;
/** Tras vencer el TTL, el worker de expiración corre cada 60 s: se reconsulta cada 5 s. */
const EXPIRED_POLL_MS = 5000;

const STATUS_LABEL: Readonly<Record<BookingStatus, string>> = {
  PENDING: 'Paga tu reserva',
  CONFIRMED: '¡Reserva confirmada!',
  PAYMENT_FAILED: 'El pago no se completó',
  EXPIRED: 'El tiempo para pagar terminó',
  CANCELLED_REFUNDED: 'Reserva cancelada con reembolso',
  CANCELLED_NO_REFUND: 'Reserva cancelada sin reembolso',
  CANCELLED_BY_PLATFORM: 'Reserva cancelada por Intégrate',
  EXECUTED: 'Experiencia realizada',
  NO_SHOW: 'No se registró asistencia',
  SYNC_CONFLICT: 'En revisión',
};

function ResultMessage({ booking }: { booking: BookingView }) {
  if (booking.status === 'EXPIRED' || booking.status === 'PAYMENT_FAILED') {
    return (
      <div className="mt-4 rounded-xl border-2 border-warning-border bg-warning-bg px-4 py-4 text-warning-text">
        <p className="font-bold">
          {booking.status === 'EXPIRED'
            ? 'El tiempo para pagar terminó y los cupos se liberaron.'
            : 'La pasarela no aprobó el pago y los cupos se liberaron. No se hizo ningún cobro.'}
        </p>
        <Link
          to={`/experiencias/${booking.experienceId}/franjas`}
          className={buttonClassName('secondary', 'mt-3')}
        >
          Volver a reservar
        </Link>
      </div>
    );
  }
  if (booking.status === 'CONFIRMED') {
    return (
      <p className="mt-4 flex items-start gap-2 rounded-xl border-2 border-success-border bg-success-bg px-4 py-4 font-bold text-success-text">
        <Icon name="shield" className="mt-0.5" />
        Recibimos tu pago. Tu voucher está abajo y también te lo enviamos al correo.
      </p>
    );
  }
  return null;
}

function BookingDetails({ booking }: { booking: BookingView }) {
  const experience = useExperience(booking.experienceId);
  const availability = useAvailability(booking.experienceId);
  const schedule = availability.data?.schedules.find(
    (item) => item.scheduleId === booking.scheduleId,
  );
  return (
    <section aria-labelledby="detalle" className="mt-6 rounded-xl bg-surface px-4 py-4">
      <h2 id="detalle" className="text-lg font-bold text-ink">
        {experience.data?.summary.title ?? 'Tu reserva'}
      </h2>
      {schedule && (
        <p className="mt-1 flex items-center gap-2 text-ink-muted first-letter:uppercase">
          <Icon name="calendar" size={18} className="text-brand" />
          {formatLongDate(schedule.localDate)} · {formatLocalTime(schedule.localStartTime)} –{' '}
          {formatLocalTime(schedule.localEndTime)}
        </p>
      )}
      <dl className="mt-3 grid grid-cols-[1fr_auto] gap-y-2 text-ink">
        <dt className="text-ink-muted">
          {pluralize(booking.lockedSlots, 'persona', 'personas')} ×{' '}
          {formatCop(booking.unitPriceCop)}
        </dt>
        <dd className="text-right font-semibold tabular-nums">
          {formatCop(booking.unitPriceCop * booking.lockedSlots)}
        </dd>
        <dt className="border-t border-border-subtle pt-2 font-bold">Total</dt>
        <dd className="border-t border-border-subtle pt-2 text-right text-xl font-bold tabular-nums">
          {formatCop(booking.totalAmountCop)}
        </dd>
      </dl>
      <p className="mt-3 flex items-start gap-2 text-sm text-ink-muted">
        <Icon name="leaf" size={18} className="mt-0.5 text-brand" />
        El 85 % del valor va a la asociación comunitaria que te recibe.
      </p>
    </section>
  );
}

interface VerifyState {
  readonly verifying: boolean;
  readonly timedOut: boolean;
  readonly start: () => void;
}

function Checkout({
  booking,
  refetch,
  verify,
}: {
  booking: BookingView;
  refetch: () => void;
  verify: VerifyState;
}) {
  const http = useHttpClient();
  const remaining = useServerCountdown(booking.receivedAt, booking.ttlSecondsRemaining);
  const [method, setMethod] = useState<PaymentMethod>('PSE');
  const [checkout, setCheckout] = useState<PaymentCheckout | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [simulatorMissing, setSimulatorMissing] = useState(false);
  const { verifying, timedOut } = verify;

  // Al vencer en el cliente se reconsulta; el servidor confirma el estado (EXPIRED).
  const expired = remaining === 0;
  useEffect(() => {
    if (expired) refetch();
  }, [expired, refetch]);

  async function pay() {
    setBusy(true);
    setError(null);
    try {
      const started = await startPayment(http, booking.bookingId, method);
      if (started.checkout.type === 'WOMPI') {
        const back = `${window.location.origin}/reservas/${booking.bookingId}?pago=retorno`;
        externalNavigation.assign(wompiCheckoutUrl(started.checkout, back));
        return;
      }
      setCheckout(started);
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === 'PAYMENT_BOOKING_NOT_PAYABLE') refetch();
      else setError(failure);
    } finally {
      setBusy(false);
    }
  }

  async function simulate(status: 'APPROVED' | 'DECLINED') {
    if (!checkout) return;
    setBusy(true);
    setError(null);
    try {
      await simulatePayment(http, checkout.paymentId, status);
      verify.start();
    } catch (failure) {
      // 404: el servidor no tiene encendido el simulador (ADR-0029 §3); no es "no encontrado".
      if (failure instanceof ApiError && failure.status === 404) setSimulatorMissing(true);
      else setError(failure);
    } finally {
      setBusy(false);
    }
  }

  const mock = checkout?.checkout.type === 'MOCK';

  return (
    <>
      <CountdownBanner remaining={remaining} />
      <BookingDetails booking={booking} />

      {verifying ? (
        <div role="status" className="mt-6 rounded-xl bg-surface px-4 py-4">
          {timedOut ? (
            <>
              <p className="font-semibold text-ink">
                Aún no recibimos la confirmación de la pasarela. Si el pago se aprobó, te llegará el
                voucher a tu correo.
              </p>
              <Button variant="secondary" className="mt-3" onClick={verify.start}>
                <Icon name="refresh" />
                Volver a consultar
              </Button>
            </>
          ) : (
            <p className="flex items-center gap-2 font-semibold text-ink">
              <Icon name="refresh" />
              Confirmando tu pago…
            </p>
          )}
        </div>
      ) : (
        <>
          <PaymentMethodPicker
            value={method}
            onChange={setMethod}
            disabled={busy || mock || expired}
          />
          {mock && <MockGatewayPanel busy={busy} onSimulate={(status) => void simulate(status)} />}
        </>
      )}

      {simulatorMissing && (
        <div
          role="alert"
          className="mt-4 flex items-start gap-2 rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-3 font-semibold text-danger-text"
        >
          <Icon name="alert" className="mt-0.5" />
          <p>
            La pasarela de pruebas no está activa en este servidor. Avisa al equipo de Intégrate;
            tus cupos se liberan solos si no se paga a tiempo.
          </p>
        </div>
      )}

      {error !== null && (
        <div className="mt-4">
          <ProblemNotice error={error} />
        </div>
      )}

      {/* Acción justo bajo el método de pago, con el total encima: no en una barra fija al fondo. */}
      {!verifying && !mock && (
        <div className="mt-6 grid gap-3">
          <p className="flex items-center justify-between gap-3 rounded-xl bg-surface px-4 py-3">
            <span className="text-ink-muted">Total en COP</span>
            <span className="text-xl font-bold text-ink tabular-nums">
              {formatCop(booking.totalAmountCop)}
            </span>
          </p>
          <Button disabled={busy || expired} onClick={() => void pay()} className="w-full">
            {busy ? 'Abriendo pasarela…' : 'Pagar'}
          </Button>
        </div>
      )}
    </>
  );
}

/** Estado y checkout de una reserva propia (T-1.10c, T-1.10d · ADR-0015 §8, ADR-0016). */
export function BookingStatusPage() {
  const { bookingId = '' } = useParams();
  const [params] = useSearchParams();
  // Al volver de la pasarela (?pago=retorno) se empieza a verificar sin esperar al turista.
  const [verifyRound, setVerifyRound] = useState(() => (params.get('pago') === 'retorno' ? 1 : 0));
  const [timedOutRound, setTimedOutRound] = useState(0);
  const verifying = verifyRound > 0;
  const timedOut = verifying && timedOutRound === verifyRound;

  const booking = useBooking(bookingId, (data) => {
    if (data?.status !== 'PENDING') return false;
    if (monotonicNow() >= data.receivedAt + data.ttlSecondsRemaining * 1000) return EXPIRED_POLL_MS;
    return verifying && !timedOut ? VERIFY_POLL_MS : false;
  });
  const { refetch: refetchBooking } = booking;
  const refetch = useCallback(() => {
    void refetchBooking();
  }, [refetchBooking]);

  useEffect(() => {
    if (verifyRound === 0) return;
    const id = setTimeout(() => {
      setTimedOutRound(verifyRound);
    }, VERIFY_LIMIT_MS);
    return () => {
      clearTimeout(id);
    };
  }, [verifyRound]);

  const verify: VerifyState = {
    verifying,
    timedOut,
    start: () => {
      setVerifyRound((round) => round + 1);
      refetch();
    },
  };

  if (booking.isPending) {
    return (
      <div aria-busy="true">
        <p className="sr-only" role="status">
          Consultando tu reserva…
        </p>
        <Skeleton className="h-9 w-3/4" />
        <Skeleton className="mt-4 h-24 w-full" />
        <Skeleton className="mt-6 h-32 w-full" />
      </div>
    );
  }
  if (booking.isError) {
    if (booking.error instanceof ApiError && booking.error.status === 404) {
      return (
        <>
          <title>Reserva no encontrada · Intégrate</title>
          <h1 className="text-2xl font-bold text-brand">No encontramos esta reserva</h1>
          <p className="mt-3 text-ink-muted">
            Revisa el enlace o busca la reserva desde tu cuenta.
          </p>
          <Link to="/" className={buttonClassName('secondary', 'mt-6')}>
            Ver experiencias
          </Link>
        </>
      );
    }
    return (
      <>
        <title>Error · Intégrate</title>
        <h1 className="text-2xl font-bold text-brand">No pudimos consultar tu reserva</h1>
        <div className="mt-4">
          <ProblemNotice
            error={booking.error}
            onRetry={() => {
              void booking.refetch();
            }}
          />
        </div>
      </>
    );
  }

  const data = booking.data;
  return (
    <>
      <title>{`${STATUS_LABEL[data.status]} · Intégrate`}</title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-brand">
        {STATUS_LABEL[data.status]}
      </h1>
      {data.status === 'PENDING' ? (
        <Checkout booking={data} refetch={refetch} verify={verify} />
      ) : (
        <>
          <ResultMessage booking={data} />
          {data.status === 'CONFIRMED' || data.status === 'EXECUTED' ? (
            <>
              <VoucherPanel bookingId={data.bookingId} used={data.status === 'EXECUTED'} />
              <TripSheet booking={data} />
            </>
          ) : (
            <BookingDetails booking={data} />
          )}
        </>
      )}
    </>
  );
}
