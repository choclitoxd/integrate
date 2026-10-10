import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { availability, detail, EXPERIENCE_ID } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';

import { formatCountdown, milestoneOf, remainingSeconds } from './countdown';
import { externalNavigation, wompiCheckoutUrl } from './wompi';

type Status = 'PENDING' | 'CONFIRMED' | 'PAYMENT_FAILED' | 'EXPIRED';

function bookingWith(status: Status, ttl: number) {
  return {
    bookingId: 'bk-1',
    status,
    scheduleId: 'b',
    experienceId: EXPERIENCE_ID,
    lockedSlots: 2,
    unitPriceCop: 80000,
    totalAmountCop: 160000,
    currency: 'COP',
    createdAt: '2026-10-09T20:00:00Z',
    // Deliberadamente en el pasado: la cuenta regresiva no debe usar expiresAt.
    expiresAt: '2020-01-01T00:00:00Z',
    ttlSecondsRemaining: status === 'PENDING' ? ttl : 0,
  };
}

const mockCheckout = {
  paymentId: 'pay-1',
  bookingId: 'bk-1',
  provider: 'MOCK_GATEWAY',
  paymentMethod: 'NEQUI',
  status: 'INITIATED',
  amountCop: 160000,
  currency: 'COP',
  bookingExpiresAt: '2026-10-09T20:15:00Z',
  checkout: { type: 'MOCK', reference: 'pay-1' },
};

const wompiCheckout = {
  ...mockCheckout,
  provider: 'WOMPI',
  checkout: {
    type: 'WOMPI',
    publicKey: 'pub_test_abc',
    currency: 'COP',
    amountInCents: 16000000,
    reference: 'pay-1',
    integritySignature: 'f'.repeat(64),
  },
};

interface Scenario {
  ttl?: number;
  path?: string;
  payment?: (body: unknown) => unknown;
  /** Estado que adopta la reserva tras la simulación. */
  afterSimulate?: Status;
  /** Respuestas sucesivas de GET /bookings/bk-1 (la última se repite). */
  statuses?: Status[];
}

function renderCheckout({
  ttl = 900,
  path = '/reservas/bk-1',
  payment = () => mockCheckout,
  afterSimulate = 'CONFIRMED',
  statuses = ['PENDING'],
}: Scenario = {}) {
  let status: Status = statuses[0] ?? 'PENDING';
  let reads = 0;
  const simulated: unknown[] = [];
  const paid: unknown[] = [];
  const view = renderApp(
    path,
    {
      '/v1/bookings/bk-1': () => {
        status = statuses[Math.min(reads, statuses.length - 1)] ?? status;
        reads += 1;
        return bookingWith(status, ttl);
      },
      '/v1/bookings/bk-1/payment': (_url: URL, request: RequestOptions) => {
        paid.push(request.body);
        return payment(request.body);
      },
      '/v1/dev/payments/pay-1/simulate': (_url: URL, request: RequestOptions) => {
        simulated.push(request.body);
        statuses = [afterSimulate];
        reads = 0;
        return { received: true };
      },
      [`/v1/experiences/${EXPERIENCE_ID}`]: detail,
      [`/v1/experiences/${EXPERIENCE_ID}/availability`]: (url: URL) =>
        availability(
          url.searchParams.get('dateFrom') ?? '',
          url.searchParams.get('dateTo') ?? '',
          [],
        ),
    },
    { roles: ['ROLE_TOURIST'] },
  );
  return { ...view, paid, simulated };
}

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('cuenta regresiva (ADR-0016 §2)', () => {
  it('calcula segundos, formato e hitos', () => {
    expect(remainingSeconds(10_500, 10_000)).toBe(1);
    expect(remainingSeconds(10_000, 12_000)).toBe(0);
    expect(formatCountdown(900)).toBe('15:00');
    expect(formatCountdown(59)).toBe('00:59');
    expect(milestoneOf(900)).toBe('running');
    expect(milestoneOf(300)).toBe('five');
    expect(milestoneOf(60)).toBe('one');
    expect(milestoneOf(0)).toBe('over');
  });

  it('usa el TTL del servidor aunque el reloj del teléfono esté desfasado', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2031-05-05T05:05:05Z'));
    renderCheckout({ ttl: 600 });
    expect(await screen.findByRole('timer')).toHaveTextContent('10:00');
    expect(
      screen.getByText('Tienes 15 minutos o menos para pagar antes de que se liberen los cupos.'),
    ).toBeInTheDocument();
  });

  it('avisa el último minuto en texto y en un anuncio', async () => {
    renderCheckout({ ttl: 50 });
    expect(await screen.findByRole('timer')).toHaveTextContent('00:50');
    expect(screen.getByText('Último minuto: si no pagas, los cupos se liberan.')).toBeVisible();
    expect(screen.getByText('Queda 1 minuto para pagar.')).toBeInTheDocument();
  });

  it('al vencer reconsulta y muestra la reserva vencida', async () => {
    renderCheckout({ ttl: 1, statuses: ['PENDING', 'EXPIRED'] });
    expect(
      await screen.findByRole(
        'heading',
        { level: 1, name: 'El tiempo para pagar terminó' },
        { timeout: 4000 },
      ),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: 'Volver a reservar' })).toBeVisible();
  });
});

describe('checkout (T-1.10d)', () => {
  it('muestra el desglose y paga con el método elegido por la pasarela de pruebas', async () => {
    const { paid, simulated } = renderCheckout();
    expect(await screen.findByRole('heading', { level: 1, name: 'Paga tu reserva' })).toBeVisible();
    expect(screen.getByText(/2 personas ×/)).toBeVisible();
    expect(
      screen.getByText('El 85 % del valor va a la asociación comunitaria que te recibe.'),
    ).toBeVisible();

    await userEvent.click(screen.getByRole('radio', { name: /Nequi/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Pagar' }));
    expect(paid).toEqual([{ paymentMethod: 'NEQUI' }]);

    const panel = await screen.findByRole('region', { name: /Pasarela de pruebas/ });
    await userEvent.click(within(panel).getByRole('button', { name: 'Simular pago aprobado' }));
    expect(simulated).toEqual([{ status: 'APPROVED' }]);
    expect(
      await screen.findByRole('heading', { level: 1, name: '¡Reserva confirmada!' }),
    ).toBeVisible();
    expect(
      screen.getByText(
        'Recibimos tu pago. Tu voucher está abajo y también te lo enviamos al correo.',
      ),
    ).toBeVisible();
  });

  it('informa el pago rechazado y ofrece volver a reservar', async () => {
    renderCheckout({ afterSimulate: 'PAYMENT_FAILED' });
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Simular pago rechazado' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'El pago no se completó' }),
    ).toBeVisible();
    expect(screen.getByText(/No se hizo ningún cobro/)).toBeVisible();
  });

  it('si la reserva ya no se puede pagar, muestra su estado actual', async () => {
    renderCheckout({
      statuses: ['PENDING', 'EXPIRED'],
      payment: () =>
        new ApiError(
          {
            type: 'about:blank',
            title: 'x',
            status: 409,
            code: 'PAYMENT_BOOKING_NOT_PAYABLE',
            bookingStatus: 'EXPIRED',
          },
          null,
        ),
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'El tiempo para pagar terminó' }),
    ).toBeVisible();
  });

  it('redirige al Web Checkout de Wompi sin cargar scripts de terceros', async () => {
    const assign = vi.spyOn(externalNavigation, 'assign').mockImplementation(() => undefined);
    renderCheckout({ payment: () => wompiCheckout });
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar' }));
    await waitFor(() => {
      expect(assign).toHaveBeenCalledTimes(1);
    });
    const url = new URL(String(assign.mock.calls[0]?.[0]));
    expect(url.origin + url.pathname).toBe('https://checkout.wompi.co/p/');
    expect(url.searchParams.get('redirect-url')).toBe(
      `${window.location.origin}/reservas/bk-1?pago=retorno`,
    );
    expect(document.querySelector('script[src*="wompi"]')).toBeNull();
  });

  it('al volver de la pasarela verifica el resultado consultando la reserva', async () => {
    renderCheckout({ path: '/reservas/bk-1?pago=retorno', statuses: ['PENDING', 'CONFIRMED'] });
    expect(await screen.findByText('Confirmando tu pago…')).toBeVisible();
    expect(
      await screen.findByRole(
        'heading',
        { level: 1, name: '¡Reserva confirmada!' },
        { timeout: 5000 },
      ),
    ).toBeVisible();
  });
});

describe('wompiCheckoutUrl', () => {
  it('arma los parámetros del Web Checkout con la firma de integridad', () => {
    const url = new URL(
      wompiCheckoutUrl(
        {
          type: 'WOMPI',
          publicKey: 'pub_test_abc',
          currency: 'COP',
          amountInCents: 16000000,
          reference: 'ref 1',
          integritySignature: 'sig',
        },
        'https://app.integrate.co/reservas/bk-1?pago=retorno',
      ),
    );
    expect(Object.fromEntries(url.searchParams)).toEqual({
      'public-key': 'pub_test_abc',
      currency: 'COP',
      'amount-in-cents': '16000000',
      reference: 'ref 1',
      'signature:integrity': 'sig',
      'redirect-url': 'https://app.integrate.co/reservas/bk-1?pago=retorno',
    });
  });
});
