import { useEffect, useState } from 'react';

import { ApiError, useHttpClient } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { BottomSheet, Button, ProblemNotice } from '@/shared/ui';

interface HostBooking {
  readonly bookingId: string;
  readonly experienceTitle: string;
  readonly startsAt: string;
  readonly holderName: string;
}
interface BookingPage {
  readonly items: readonly HostBooking[];
  readonly page: number;
  readonly totalPages: number;
}
type EpsState =
  | { status: 'loading' }
  | { status: 'success'; value: string | null }
  | { status: 'error'; error: unknown };

export function HostBookingsPage() {
  const http = useHttpClient();
  const [page, setPage] = useState(0);
  const [list, setList] = useState<BookingPage | null>(null);
  const [listError, setListError] = useState<unknown>(null);
  const [selected, setSelected] = useState<HostBooking | null>(null);
  const [eps, setEps] = useState<EpsState>({ status: 'loading' });
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void http
      .request<BookingPage>(`/v1/host/bookings?page=${String(page)}&size=20`, {
        signal: controller.signal,
      })
      .then((data) => {
        if (!controller.signal.aborted) {
          setList(data);
          setListError(null);
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setListError(error);
      });
    return () => {
      controller.abort();
    };
  }, [http, page, reload]);

  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    void http
      .request<{ medicalEps: string | null }>(
        `/v1/host/bookings/${encodeURIComponent(selected.bookingId)}/holder-eps`,
        { signal: controller.signal },
      )
      .then((data) => {
        if (!controller.signal.aborted) setEps({ status: 'success', value: data.medicalEps });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setEps({ status: 'error', error });
      });
    return () => {
      controller.abort();
    };
  }, [http, selected]);

  const close = () => {
    setSelected(null);
    setEps({ status: 'loading' });
  };
  return (
    <>
      <h1 className="text-3xl font-bold text-brand">Reservas confirmadas</h1>
      <p className="mt-3 text-ink-muted">
        Consulta las reservas de tus experiencias. La EPS del titular solo se consulta
        individualmente para atender una emergencia.
      </p>
      <div className="mt-6" aria-live="polite">
        {listError ? (
          <ProblemNotice
            error={listError}
            onRetry={() => {
              setReload((n) => n + 1);
            }}
          />
        ) : !list ? (
          <p>Cargando reservas…</p>
        ) : list.items.length === 0 ? (
          <p>No tienes reservas confirmadas para mostrar.</p>
        ) : (
          <ul className="grid gap-4">
            {list.items.map((booking) => (
              <li key={booking.bookingId} className="rounded-xl border-2 border-border-subtle p-4">
                <h2 className="text-lg font-bold text-ink">{booking.experienceTitle}</h2>
                <p className="mt-1 text-ink-muted">
                  {formatDateTime(Date.parse(booking.startsAt))}
                </p>
                <p className="mt-1 text-ink">Titular: {booking.holderName}</p>
                <Button
                  variant="secondary"
                  className="mt-4 w-full"
                  onClick={() => {
                    setEps({ status: 'loading' });
                    setSelected(booking);
                  }}
                >
                  Consultar EPS en emergencia
                  <span className="sr-only">
                    {' '}
                    de {booking.holderName}, {booking.experienceTitle}
                  </span>
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
      {list && list.totalPages > 1 && (
        <nav
          aria-label="Páginas de reservas"
          className="mt-6 flex items-center justify-between gap-2"
        >
          <Button
            variant="secondary"
            disabled={page === 0}
            onClick={() => {
              setList(null);
              setPage(page - 1);
            }}
          >
            Anterior
          </Button>
          <span>
            Página {page + 1} de {list.totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={page + 1 >= list.totalPages}
            onClick={() => {
              setList(null);
              setPage(page + 1);
            }}
          >
            Siguiente
          </Button>
        </nav>
      )}
      <BottomSheet open={selected !== null} onClose={close} title="EPS del titular">
        {selected && (
          <p className="mb-3 font-semibold">
            {selected.holderName} · {selected.experienceTitle}
          </p>
        )}
        <p className="mb-3 text-ink-muted">
          Consulta esta información solo para atender una emergencia.
        </p>
        {eps.status === 'loading' ? (
          <p role="status">Consultando…</p>
        ) : eps.status === 'success' ? (
          <p>{eps.value ?? 'El titular no proporcionó una EPS.'}</p>
        ) : eps.error instanceof ApiError && eps.error.status === 403 ? (
          <div role="alert">
            <p>No es posible consultar la EPS. La autorización requerida no está disponible.</p>
            {eps.error.traceId && <p>Código de seguimiento: {eps.error.traceId}</p>}
          </div>
        ) : (
          <ProblemNotice error={eps.error} />
        )}
      </BottomSheet>
    </>
  );
}
