import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import jsQR from 'jsqr';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/shared/api';
import { availability, detail, VOUCHER_TOKEN_SAMPLE, EXPERIENCE_ID } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';

import type { Voucher } from './api';
import { groupShortCode, QR_BORDER } from './qr/matrix';
import { QrCode } from './qr/QrCode';
import { externalNavigation } from './wompi';

/** Reconstruye la imagen del SVG renderizado (módulos de 4 px) y la decodifica con jsQR. */
function decodeRenderedQr(svg: SVGSVGElement): string | null {
  const size = Number(svg.getAttribute('viewBox')?.split(' ')[2]);
  const d = svg.querySelector('path')?.getAttribute('d') ?? '';
  const dark = new Set(
    [...d.matchAll(/M(\d+) (\d+)h1v1h-1z/g)].map((m) => `${m[1] ?? ''},${m[2] ?? ''}`),
  );
  const scale = 4;
  const width = size * scale;
  const pixels = new Uint8ClampedArray(width * width * 4);
  for (let y = 0; y < width; y++) {
    for (let x = 0; x < width; x++) {
      const on = dark.has(`${String(Math.floor(x / scale))},${String(Math.floor(y / scale))}`);
      const i = (y * width + x) * 4;
      pixels[i] = pixels[i + 1] = pixels[i + 2] = on ? 0 : 255;
      pixels[i + 3] = 255;
    }
  }
  return jsQR(pixels, width, width)?.data ?? null;
}

const voucher = (overrides: Partial<Voucher> = {}): Voucher => ({
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
  pdfUrl: 'https://s3.local/vouchers/v-1.pdf?firma=1',
  pdfUrlExpiresAt: '2026-10-09T20:10:00Z',
  ...overrides,
});

const confirmed = {
  bookingId: 'bk-1',
  status: 'CONFIRMED',
  scheduleId: 'b',
  experienceId: EXPERIENCE_ID,
  lockedSlots: 2,
  unitPriceCop: 80000,
  totalAmountCop: 160000,
  currency: 'COP',
  createdAt: '2026-10-09T20:00:00Z',
  expiresAt: '2026-10-09T20:15:00Z',
  ttlSecondsRemaining: 0,
};

function renderConfirmed(voucherRoute: unknown) {
  return renderApp(
    '/reservas/bk-1',
    {
      '/v1/bookings/bk-1': confirmed,
      '/v1/bookings/bk-1/voucher': voucherRoute,
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
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('QR del voucher (ADR-0017 §2)', () => {
  it('el SVG dibujado se decodifica exactamente al token, con margen de 4 módulos', () => {
    render(<QrCode value={VOUCHER_TOKEN_SAMPLE} label="QR de prueba" />);
    const svg = screen.getByRole('img', { name: 'QR de prueba' }) as unknown as SVGSVGElement;
    expect(decodeRenderedQr(svg)).toBe(VOUCHER_TOKEN_SAMPLE);
    // Ningún módulo oscuro dentro del margen.
    const d = svg.querySelector('path')?.getAttribute('d') ?? '';
    const coords = [...d.matchAll(/M(\d+) (\d+)/g)].flatMap((m) => [Number(m[1]), Number(m[2])]);
    expect(Math.min(...coords)).toBe(QR_BORDER);
  });

  it('agrupa el código corto para dictarlo', () => {
    expect(groupShortCode('AB23CD45')).toBe('AB23 CD45');
  });
});

describe('confirmación (T-1.10e)', () => {
  it('muestra el QR, el código corto y la ficha de viaje', async () => {
    renderConfirmed(voucher());
    expect(
      await screen.findByRole('heading', { level: 1, name: '¡Reserva confirmada!' }),
    ).toBeVisible();
    const qr = await screen.findByRole('img', {
      name: /Código QR del voucher, válido el jueves, 29 de octubre/,
    });
    expect(decodeRenderedQr(qr as unknown as SVGSVGElement)).toBe(VOUCHER_TOKEN_SAMPLE);
    expect(screen.getByText('AB23 CD45')).toBeVisible();
    const sheet = screen.getByRole('region', { name: 'Ficha de viaje' });
    expect(await within(sheet).findByText('Parque principal de San Carlos')).toBeVisible();
    expect(within(sheet).queryByText(/EPS/)).toBeNull();
  });

  it('reintenta mientras el voucher se emite', async () => {
    let calls = 0;
    renderConfirmed(() => {
      calls += 1;
      return calls === 1
        ? new ApiError(
            { type: 'about:blank', title: 'x', status: 409, code: 'VOUCHER_NOT_READY' },
            null,
          )
        : voucher();
    });
    expect(await screen.findByText('Estamos emitiendo tu voucher…')).toBeVisible();
    expect(await screen.findByText('AB23 CD45', {}, { timeout: 4000 })).toBeVisible();
  });

  it('sin token avisa que el QR está en el PDF', async () => {
    renderConfirmed(voucher({ token: null }));
    expect(await screen.findByText(/El QR está en el PDF del voucher/)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Mostrar QR a pantalla completa' })).toBeNull();
  });

  it('descarga el PDF con una URL prefirmada pedida en el momento del clic', async () => {
    const assign = vi.spyOn(externalNavigation, 'assign').mockImplementation(() => undefined);
    let n = 0;
    renderConfirmed(() => {
      n += 1;
      return voucher({ pdfUrl: `https://s3.local/vouchers/v-1.pdf?firma=${String(n)}` });
    });
    await userEvent.click(
      await screen.findByRole('button', { name: /Descargar voucher \(PDF\/A\)/ }),
    );
    await waitFor(() => {
      expect(assign).toHaveBeenCalledTimes(1);
    });
    expect(assign.mock.calls[0]?.[0]).toBe(`https://s3.local/vouchers/v-1.pdf?firma=${String(n)}`);
    expect(n).toBeGreaterThanOrEqual(2);
  });

  it('inhabilita la descarga mientras el PDF se genera', async () => {
    renderConfirmed(voucher({ pdfStatus: 'GENERATING', pdfUrl: null }));
    expect(await screen.findByRole('button', { name: /Preparando el PDF/ })).toBeDisabled();
  });

  it('abre el QR a pantalla completa y mantiene la pantalla encendida', async () => {
    const release = vi.fn(() => Promise.resolve());
    const request = vi.fn(() => Promise.resolve({ release }));
    vi.stubGlobal(
      'navigator',
      Object.assign(Object.create(navigator) as Navigator, { wakeLock: { request } }),
    );
    renderConfirmed(voucher());
    await userEvent.click(
      await screen.findByRole('button', { name: 'Mostrar QR a pantalla completa' }),
    );
    const dialog = screen.getByRole('dialog', { name: 'Voucher de tu reserva' });
    expect(within(dialog).getByRole('img', { name: /Código QR del voucher/ })).toBeVisible();
    expect(within(dialog).getByText(/Sube el brillo de la pantalla/)).toBeVisible();
    await waitFor(() => {
      expect(request).toHaveBeenCalledWith('screen');
    });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cerrar' }));
    await waitFor(() => {
      expect(release).toHaveBeenCalled();
    });
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('informa un voucher ya usado sin ofrecer el QR', async () => {
    renderConfirmed(voucher({ status: 'CONSUMED' }));
    expect(await screen.findByText('Este voucher ya se usó.')).toBeVisible();
    expect(screen.queryByRole('img', { name: /Código QR/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Mostrar QR a pantalla completa' })).toBeNull();
  });
});
