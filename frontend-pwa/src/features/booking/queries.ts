import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { ApiError, useHttpClient } from '@/shared/api';

import { getBooking, getVoucher, listMyBookings, type Booking, type MyBookingsScope } from './api';
import { monotonicNow } from './countdown';

/** Reserva con el instante monotónico de su llegada, ancla de la cuenta regresiva (ADR-0016 §2). */
export interface BookingView extends Booking {
  readonly receivedAt: number;
}

/** Bajo `['me', …]`: se borra al cerrar sesión (ADR-0013 §2). */
export const bookingKeys = {
  booking: (id: string) => ['me', 'booking', id] as const,
  voucher: (id: string) => ['me', 'voucher', id] as const,
  mine: (scope: MyBookingsScope) => ['me', 'bookings', scope] as const,
};

/** Tarjetas por página en "Mis experiencias" (ADR-0023 §2). */
export const MY_BOOKINGS_PAGE_SIZE = 10;

/** "Mis experiencias": páginas de 10 con "Ver más" (sin scroll infinito, ADR-0023 §2). */
export function useMyBookings(scope: MyBookingsScope) {
  const http = useHttpClient();
  return useInfiniteQuery({
    queryKey: bookingKeys.mine(scope),
    queryFn: ({ pageParam, signal }) =>
      listMyBookings(http, scope, pageParam, MY_BOOKINGS_PAGE_SIZE, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

/** Emisión asíncrona (ADR-0017 §4-§5): 409 VOUCHER_NOT_READY cada 2 s y PDF en curso cada 3 s, máx. ~30 s. */
const VOUCHER_RETRY_MS = 2000;
const VOUCHER_MAX_RETRIES = 15;
const PDF_POLL_MS = 3000;
const PDF_MAX_POLLS = 10;

export function isVoucherNotReady(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'VOUCHER_NOT_READY';
}

export function useVoucher(bookingId: string, enabled: boolean) {
  const http = useHttpClient();
  return useQuery({
    queryKey: bookingKeys.voucher(bookingId),
    queryFn: ({ signal }) => getVoucher(http, bookingId, signal),
    enabled,
    staleTime: 0,
    refetchInterval: (query) => {
      const { data, error, errorUpdateCount, dataUpdateCount } = query.state;
      if (!data && isVoucherNotReady(error) && errorUpdateCount < VOUCHER_MAX_RETRIES) {
        return VOUCHER_RETRY_MS;
      }
      if (data?.pdfStatus === 'GENERATING' && dataUpdateCount < PDF_MAX_POLLS) return PDF_POLL_MS;
      return false;
    },
  });
}

/** Intervalo de sondeo según la última respuesta; `false` = sin sondeo. */
export type BookingPollPolicy = (booking: BookingView | undefined) => number | false;

/**
 * `poll`: sondeo mientras se espera un resultado (pago o vencimiento, ADR-0016 §4); TanStack lo
 * pausa con la pestaña en segundo plano.
 */
export function useBooking(id: string, poll: BookingPollPolicy = () => false) {
  const http = useHttpClient();
  return useQuery({
    queryKey: bookingKeys.booking(id),
    queryFn: async ({ signal }): Promise<BookingView> => ({
      ...(await getBooking(http, id, signal)),
      receivedAt: monotonicNow(),
    }),
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => poll(query.state.data),
  });
}
