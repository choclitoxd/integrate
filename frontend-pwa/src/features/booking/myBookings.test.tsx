import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api';
import { availability, detail, EXPERIENCE_ID, VOUCHER_TOKEN_SAMPLE } from '@/test/fixtures';
import { renderApp, type FakeRoutes } from '@/test/renderApp';

import type { MyBookingSummary, Page } from './api';

const consents = {
  currentPolicyVersion: 'v0.1',
  consents: [
    {
      purpose: 'PERSONAL_DATA',
      state: 'VALID',
      policyVersion: 'v0.1',
      occurredAt: '2026-10-09T10:00:00Z',
    },
    {
      purpose: 'HEALTH_EMERGENCY',
      state: 'VALID',
      policyVersion: 'v0.1',
      occurredAt: '2026-10-09T10:00:00Z',
    },
  ],
  requiresConsentUpdate: false,
  pendingPurposes: [],
};

const summary = (overrides: Partial<MyBookingSummary> = {}): MyBookingSummary => ({
  bookingId: 'bk-1',
  status: 'CONFIRMED',
  experienceId: EXPERIENCE_ID,
  experienceTitle: 'Sendero de la memoria',
  municipality: 'SAN CARLOS',
  startTime: '2026-10-29T12:00:00Z',
  endTime: '2026-10-29T17:00:00Z',
  timezone: 'America/Bogota',
  slots: 2,
  totalAmountCop: 160000,
  hasVoucher: true,
  ...overrides,
});

function pageOf(
  items: readonly MyBookingSummary[],
  page = 0,
  totalPages = 1,
): Page<MyBookingSummary> {
  return { items, page, size: 10, totalElements: items.length, totalPages };
}

/** `/v1/me/bookings` responde según `scope` y `page` de la URL. */
function myBookings(byScope: Record<string, (page: number) => unknown>) {
  return (url: URL) =>
    byScope[url.searchParams.get('scope') ?? 'upcoming']?.(
      Number(url.searchParams.get('page') ?? '0'),
    );
}

function renderAccount(routes: FakeRoutes, path = '/cuenta') {
  return renderApp(path, { '/v1/me/consents': consents, ...routes }, { roles: ['ROLE_TOURIST'] });
}

describe('Mis experiencias en Mi cuenta (T-1.14b, ADR-0023)', () => {
  it('lista las próximas con acceso al voucher y abre el detalle con el QR', async () => {
    const { router } = renderAccount({
      '/v1/me/bookings': myBookings({
        upcoming: () =>
          pageOf([
            summary(),
            summary({
              bookingId: 'bk-2',
              status: 'PENDING',
              hasVoucher: false,
              experienceTitle: 'Ruta del café',
            }),
          ]),
      }),
      '/v1/bookings/bk-1': {
        bookingId: 'bk-1',
        status: 'CONFIRMED',
        scheduleId: 's-1',
        experienceId: EXPERIENCE_ID,
        lockedSlots: 2,
        unitPriceCop: 80000,
        totalAmountCop: 160000,
        currency: 'COP',
        createdAt: '2026-10-09T20:00:00Z',
        expiresAt: '2026-10-09T20:15:00Z',
        ttlSecondsRemaining: 0,
      },
      '/v1/bookings/bk-1/voucher': {
        voucherId: 'v-1',
        jti: 'j-1',
        bookingId: 'bk-1',
        shortCode: 'AB23CD45',
        status: 'ISSUED',
        issuedAt: '2026-10-09T20:05:00Z',
        expiresAt: '2026-10-30T17:00:00Z',
        validDate: '2026-10-29',
        token: VOUCHER_TOKEN_SAMPLE,
        pdfStatus: 'READY',
        pdfUrl: 'https://s3.local/v-1.pdf',
      },
      [`/v1/experiences/${EXPERIENCE_ID}`]: detail,
      [`/v1/experiences/${EXPERIENCE_ID}/availability`]: (url: URL) =>
        availability(
          url.searchParams.get('dateFrom') ?? '',
          url.searchParams.get('dateTo') ?? '',
          [],
        ),
    });

    const section = await screen.findByRole('region', { name: 'Mis experiencias' });
    expect(within(section).getByRole('tab', { name: 'Próximas' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    const cards = await within(section).findAllByRole('link', {
      name: /Sendero de la memoria|Ruta del café/,
    });
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveTextContent('Confirmada');
    expect(cards[0]).toHaveTextContent('Ver voucher y QR');
    expect(cards[0]).toHaveTextContent('2 personas');
    expect(cards[0]).toHaveTextContent('$ 160.000');
    expect(cards[0]).toHaveAttribute('href', '/reservas/bk-1');
    expect(cards[1]).toHaveTextContent('Pendiente de pago');
    expect(cards[1]).toHaveTextContent('Completar el pago');
    expect(cards[1]).not.toHaveTextContent('Ver voucher');

    await userEvent.click(within(section).getByRole('link', { name: /Sendero de la memoria/ }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/reservas/bk-1');
    });
    expect(await screen.findByRole('img', { name: /Código QR del voucher/ })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Mostrar QR a pantalla completa' })).toBeVisible();
  });

  it('las pestañas se operan con el teclado y la elegida queda en la URL', async () => {
    const { router } = renderAccount({
      '/v1/me/bookings': myBookings({
        upcoming: () => pageOf([summary()]),
        past: () =>
          pageOf([
            summary({
              bookingId: 'bk-9',
              status: 'EXECUTED',
              experienceTitle: 'Taller de memoria',
            }),
            summary({
              bookingId: 'bk-8',
              status: 'PAYMENT_FAILED',
              hasVoucher: false,
              experienceTitle: 'Agroturismo',
            }),
          ]),
      }),
    });
    const upcomingTab = await screen.findByRole('tab', { name: 'Próximas' });
    upcomingTab.focus();
    await userEvent.keyboard('{ArrowRight}');

    const pastTab = screen.getByRole('tab', { name: 'Realizadas' });
    expect(pastTab).toHaveFocus();
    expect(pastTab).toHaveAttribute('aria-selected', 'true');
    expect(router.state.location.search).toBe('?tab=realizadas');
    const panel = screen.getByRole('tabpanel');
    const executed = await within(panel).findByRole('link', { name: /Taller de memoria/ });
    expect(executed).toHaveTextContent('Realizada');
    expect(executed).toHaveTextContent('Ver voucher usado');
    expect(within(panel).getByRole('link', { name: /Agroturismo/ })).toHaveTextContent(
      'Ver detalle',
    );
  });

  it('muestra los estados vacíos y pagina con "Ver más"', async () => {
    renderAccount(
      {
        '/v1/me/bookings': myBookings({
          upcoming: () => pageOf([]),
          past: (page) =>
            page === 0
              ? pageOf(
                  [summary({ bookingId: 'bk-a', status: 'EXECUTED', experienceTitle: 'Primera' })],
                  0,
                  2,
                )
              : pageOf(
                  [summary({ bookingId: 'bk-b', status: 'EXECUTED', experienceTitle: 'Segunda' })],
                  1,
                  2,
                ),
        }),
      },
      '/cuenta?tab=realizadas',
    );
    const panel = await screen.findByRole('tabpanel');
    expect(await within(panel).findByRole('link', { name: /Primera/ })).toBeVisible();
    await userEvent.click(within(panel).getByRole('button', { name: 'Ver más' }));
    expect(await within(panel).findByRole('link', { name: /Segunda/ })).toBeVisible();
    expect(within(panel).queryByRole('button', { name: 'Ver más' })).toBeNull();

    await userEvent.click(screen.getByRole('tab', { name: 'Próximas' }));
    expect(await screen.findByText('Aún no tienes experiencias reservadas.')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Explorar experiencias' })).toHaveAttribute(
      'href',
      '/',
    );
  });

  it('un error del servidor se muestra con opción de reintentar', async () => {
    let calls = 0;
    renderAccount({
      '/v1/me/bookings': () => {
        calls += 1;
        return calls === 1
          ? new ApiError({ type: 'about:blank', title: 'Error', status: 500 }, 'trace-1')
          : pageOf([summary()]);
      },
    });
    await userEvent.click(await screen.findByRole('button', { name: /Reintentar/ }));
    expect(await screen.findByRole('link', { name: /Sendero de la memoria/ })).toBeVisible();
  });
});

describe('voucher de una experiencia realizada (ADR-0023 §2)', () => {
  it('se muestra como comprobante, sin QR a pantalla completa', async () => {
    renderApp(
      '/reservas/bk-9',
      {
        '/v1/bookings/bk-9': {
          bookingId: 'bk-9',
          status: 'EXECUTED',
          scheduleId: 's-1',
          experienceId: EXPERIENCE_ID,
          lockedSlots: 1,
          unitPriceCop: 80000,
          totalAmountCop: 80000,
          currency: 'COP',
          createdAt: '2026-10-09T20:00:00Z',
          expiresAt: '2026-10-09T20:15:00Z',
          ttlSecondsRemaining: 0,
        },
        '/v1/bookings/bk-9/voucher': {
          voucherId: 'v-9',
          jti: 'j-9',
          bookingId: 'bk-9',
          shortCode: 'ZX23CD45',
          status: 'CONSUMED',
          issuedAt: '2026-10-09T20:05:00Z',
          expiresAt: '2026-10-30T17:00:00Z',
          validDate: '2026-10-29',
          token: VOUCHER_TOKEN_SAMPLE,
          pdfStatus: 'READY',
          pdfUrl: 'https://s3.local/v-9.pdf',
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
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Experiencia realizada' }),
    ).toBeVisible();
    expect(await screen.findByText('Este voucher ya se usó.')).toBeVisible();
    expect(screen.getByText('ZX23 CD45')).toBeVisible();
    expect(screen.getByRole('button', { name: /Descargar voucher/ })).toBeEnabled();
    expect(screen.queryByRole('img', { name: /Código QR del voucher/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Mostrar QR a pantalla completa' })).toBeNull();
  });
});
