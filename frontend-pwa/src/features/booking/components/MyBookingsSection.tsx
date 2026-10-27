import { useId, useRef, type KeyboardEvent } from 'react';
import { Link, useSearchParams } from 'react-router';

import { formatCop, formatDateTime, pluralize } from '@/shared/lib';
import { Button, buttonClassName, Icon, ProblemNotice, Skeleton, type IconName } from '@/shared/ui';

import type { BookingStatus, MyBookingSummary, MyBookingsScope } from '../api';
import { useMyBookings } from '../queries';

/** Pestañas en la URL (`?tab=realizadas`): al volver del detalle se conserva la que estaba abierta. */
interface Tab {
  readonly key: string;
  readonly scope: MyBookingsScope;
  readonly label: string;
}

const UPCOMING_TAB: Tab = { key: 'proximas', scope: 'upcoming', label: 'Próximas' };
const TABS: readonly Tab[] = [
  UPCOMING_TAB,
  { key: 'realizadas', scope: 'past', label: 'Realizadas' },
];

type Tone = 'success' | 'warning' | 'muted';

/** Cada estado lleva texto e icono: nunca solo color (Directiva 2). */
const STATUS_BADGE: Readonly<Record<BookingStatus, { label: string; icon: IconName; tone: Tone }>> =
  {
    PENDING: { label: 'Pendiente de pago', icon: 'clock', tone: 'warning' },
    CONFIRMED: { label: 'Confirmada', icon: 'shield', tone: 'success' },
    EXECUTED: { label: 'Realizada', icon: 'check', tone: 'success' },
    NO_SHOW: { label: 'Sin asistencia', icon: 'alert', tone: 'muted' },
    PAYMENT_FAILED: { label: 'Pago no completado', icon: 'alert', tone: 'warning' },
    EXPIRED: { label: 'Tiempo para pagar vencido', icon: 'clock', tone: 'muted' },
    CANCELLED_REFUNDED: { label: 'Cancelada con reembolso', icon: 'alert', tone: 'muted' },
    CANCELLED_NO_REFUND: { label: 'Cancelada sin reembolso', icon: 'alert', tone: 'muted' },
    CANCELLED_BY_PLATFORM: { label: 'Cancelada por Intégrate', icon: 'alert', tone: 'muted' },
    SYNC_CONFLICT: { label: 'En revisión', icon: 'alert', tone: 'warning' },
  };

const TONE_CLASS: Readonly<Record<Tone, string>> = {
  success: 'text-success-text',
  warning: 'text-warning-text',
  muted: 'text-ink-muted',
};

function actionLabel(booking: MyBookingSummary): string {
  if (booking.hasVoucher) {
    return booking.status === 'EXECUTED' ? 'Ver voucher usado' : 'Ver voucher y QR';
  }
  return booking.status === 'PENDING' ? 'Completar el pago' : 'Ver detalle';
}

function BookingCard({ booking }: { booking: MyBookingSummary }) {
  const badge = STATUS_BADGE[booking.status];
  return (
    <li>
      {/* Un solo enlace por tarjeta: el detalle (ADR-0017) es el único lugar del QR. */}
      <Link
        to={`/reservas/${encodeURIComponent(booking.bookingId)}`}
        className="block min-h-11 rounded-xl border-2 border-border-subtle bg-canvas px-4 py-4 hover:border-brand"
      >
        <span
          className={`inline-flex items-center gap-1 text-sm font-semibold ${TONE_CLASS[badge.tone]}`}
        >
          <Icon name={badge.icon} size={18} />
          {badge.label}
        </span>
        <span className="mt-1 block text-lg leading-snug font-bold text-ink">
          {booking.experienceTitle}
        </span>
        <span className="mt-1 flex items-start gap-2 text-ink-muted first-letter:uppercase">
          <Icon name="calendar" size={18} className="mt-0.5 shrink-0" />
          <span className="first-letter:uppercase">
            {formatDateTime(Date.parse(booking.startTime))}
          </span>
        </span>
        {booking.municipality && (
          <span className="mt-0.5 flex items-start gap-2 text-ink-muted">
            <Icon name="map" size={18} className="mt-0.5 shrink-0" />
            {booking.municipality}
          </span>
        )}
        <span className="mt-0.5 flex items-start gap-2 text-ink-muted">
          <Icon name="users" size={18} className="mt-0.5 shrink-0" />
          {pluralize(booking.slots, 'persona', 'personas')} · {formatCop(booking.totalAmountCop)}
        </span>
        <span className="mt-3 inline-flex items-center gap-1 font-semibold text-brand">
          {actionLabel(booking)}
          <Icon name="chevronRight" size={20} />
        </span>
      </Link>
    </li>
  );
}

function BookingList({
  scope,
  panelId,
  tabId,
}: {
  scope: MyBookingsScope;
  panelId: string;
  tabId: string;
}) {
  const query = useMyBookings(scope);
  const items = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div id={panelId} role="tabpanel" aria-labelledby={tabId} className="mt-3">
      {query.isPending && (
        <div className="grid gap-2" aria-hidden="true">
          <Skeleton className="h-36 w-full" />
          <Skeleton className="h-36 w-full" />
        </div>
      )}
      {query.isError && (
        <ProblemNotice
          error={query.error}
          onRetry={() => {
            void query.refetch();
          }}
        />
      )}
      {query.isSuccess && items.length === 0 && (
        <div className="rounded-xl bg-surface px-4 py-4">
          <p className="text-ink">
            {scope === 'upcoming'
              ? 'Aún no tienes experiencias reservadas.'
              : 'Aquí verás las experiencias que ya viviste.'}
          </p>
          {scope === 'upcoming' && (
            <Link to="/" className={buttonClassName('secondary', 'mt-3 w-full')}>
              <Icon name="search" />
              Explorar experiencias
            </Link>
          )}
        </div>
      )}
      {items.length > 0 && (
        <ul className="grid gap-2">
          {items.map((booking) => (
            <BookingCard key={booking.bookingId} booking={booking} />
          ))}
        </ul>
      )}
      {query.hasNextPage && (
        <Button
          variant="secondary"
          className="mt-3 w-full"
          disabled={query.isFetchingNextPage}
          onClick={() => {
            void query.fetchNextPage();
          }}
        >
          {query.isFetchingNextPage ? 'Cargando…' : 'Ver más'}
        </Button>
      )}
    </div>
  );
}

/** Contenido de "Mis experiencias" (T-1.14b, ADR-0023 §2): próximas y realizadas, con acceso al voucher. */
export function MyBookingsSection() {
  const [params, setParams] = useSearchParams();
  const active = TABS.find((tab) => tab.key === params.get('tab')) ?? UPCOMING_TAB;
  const baseId = useId();
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  function select(index: number, focus: boolean) {
    const tab = TABS[index];
    if (!tab) return;
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (index === 0) next.delete('tab');
        else next.set('tab', tab.key);
        return next;
      },
      { replace: true, preventScrollReset: true },
    );
    if (focus) tabRefs.current[index]?.focus();
  }

  // Patrón de pestañas WAI-ARIA: flechas, Inicio y Fin mueven y activan la pestaña.
  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const last = TABS.length - 1;
    const target =
      event.key === 'ArrowRight'
        ? (index + 1) % TABS.length
        : event.key === 'ArrowLeft'
          ? (index - 1 + TABS.length) % TABS.length
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? last
              : null;
    if (target === null) return;
    event.preventDefault();
    select(target, true);
  }

  return (
    // El título visible (h1) lo pone la página; la región conserva su nombre para lectores de pantalla.
    <section aria-label="Mis experiencias" className="mt-5">
      <div role="tablist" aria-label="Próximas y realizadas" className="grid grid-cols-2 gap-2">
        {TABS.map((tab, index) => {
          const selected = tab.key === active.key;
          return (
            <button
              key={tab.key}
              ref={(node) => {
                tabRefs.current[index] = node;
              }}
              id={`${baseId}-tab-${tab.key}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${baseId}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => {
                select(index, false);
              }}
              onKeyDown={(event) => {
                onKeyDown(event, index);
              }}
              className={`min-h-11 rounded-lg border-2 px-3 font-semibold ${
                selected
                  ? 'border-brand bg-brand text-on-brand'
                  : 'border-border-subtle bg-canvas text-ink hover:border-brand'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <BookingList
        key={active.scope}
        scope={active.scope}
        panelId={`${baseId}-panel`}
        tabId={`${baseId}-tab-${active.key}`}
      />
    </section>
  );
}
