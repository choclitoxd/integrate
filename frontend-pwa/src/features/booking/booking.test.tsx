import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError, type RequestOptions } from '@/shared/api';
import { addDays, todayInBogota } from '@/shared/lib';
import { availability, detail, EXPERIENCE_ID, schedule } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';

const day = addDays(todayInBogota(), 3);
const FORM_PATH = `/reservas/nueva?experiencia=${EXPERIENCE_ID}&franja=b&personas=2`;

const booking = {
  bookingId: 'bk-1',
  status: 'PENDING',
  scheduleId: 'b',
  experienceId: EXPERIENCE_ID,
  lockedSlots: 2,
  unitPriceCop: 80000,
  totalAmountCop: 160000,
  currency: 'COP',
  createdAt: '2026-10-09T20:00:00Z',
  expiresAt: '2026-10-09T20:15:00Z',
  ttlSecondsRemaining: 900,
};

const policy = {
  version: 'v0.1',
  title: 'Política de datos',
  documentUrl: 'https://integrate.co/politica',
  effectiveFrom: '2026-10-01T00:00:00Z',
  contentSha256: 'a'.repeat(64),
};

function consents(health: 'VALID' | 'MISSING') {
  return {
    currentPolicyVersion: 'v0.1',
    consents: [
      { purpose: 'PERSONAL_DATA', state: 'VALID', policyVersion: 'v0.1', occurredAt: null },
      { purpose: 'HEALTH_EMERGENCY', state: health, policyVersion: null, occurredAt: null },
    ],
    requiresConsentUpdate: false,
    pendingPurposes: [],
  };
}

interface LockCall {
  readonly body: unknown;
  readonly key: string | undefined;
}

function renderForm(
  lock: (call: LockCall, n: number) => unknown,
  options: { health?: 'VALID' | 'MISSING'; available?: number } = {},
) {
  const calls: LockCall[] = [];
  let health = options.health ?? 'VALID';
  const view = renderApp(
    FORM_PATH,
    {
      [`/v1/experiences/${EXPERIENCE_ID}/availability`]: (url: URL) =>
        availability(url.searchParams.get('dateFrom') ?? '', url.searchParams.get('dateTo') ?? '', [
          schedule('b', day, '07', options.available ?? 5),
        ]),
      [`/v1/experiences/${EXPERIENCE_ID}`]: detail,
      '/v1/privacy-policies/current': policy,
      '/v1/me/consents': (_url: URL, request: RequestOptions) => {
        if (request.method === 'POST') health = 'VALID';
        return consents(health);
      },
      '/v1/bookings/lock': (_url: URL, request: RequestOptions) => {
        const call = { body: request.body, key: request.headers?.['Idempotency-Key'] };
        calls.push(call);
        return lock(call, calls.length);
      },
      '/v1/bookings/bk-1': booking,
    },
    { roles: ['ROLE_TOURIST'] },
  );
  return { ...view, calls };
}

async function fillTraveler(index: number, doc: string, eps: string, name?: string) {
  const card = screen.getByRole('group', { name: new RegExp(`Viajero ${String(index)} de 2`) });
  if (name) {
    const field = within(card).getByLabelText('Nombre completo');
    await userEvent.clear(field);
    await userEvent.type(field, name);
  }
  await userEvent.type(within(card).getByLabelText('Número de documento'), doc);
  await userEvent.type(within(card).getByLabelText(/^EPS/), eps);
}

async function fillAll() {
  await screen.findByText(/Sendero de la Memoria/);
  await fillTraveler(1, '1094000111', 'Sura');
  await fillTraveler(2, '1094000222', 'Sanitas', 'Luis Restrepo');
  const contact = screen.getByRole('group', { name: 'Contacto de emergencia' });
  await userEvent.type(within(contact).getByLabelText('Nombre completo'), 'Carmen Ruiz');
  const phone = within(contact).getByLabelText('Celular');
  await userEvent.clear(phone);
  await userEvent.type(phone, '300 765 4321');
}

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Apartar cupos' }));

describe('formulario de reserva (T-1.10c)', () => {
  it('exige sesión de turista y conserva el destino', async () => {
    const { router } = renderApp(FORM_PATH);
    // La ruta se carga en diferido (ADR-0018 §1): la guarda actúa al resolverse.
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/ingresar');
    });
    expect(new URLSearchParams(router.state.location.search).get('volver')).toBe(FORM_PATH);
  });

  it('precarga el titular y valida por viajero antes de enviar', async () => {
    const { calls } = renderForm(() => booking);
    await screen.findByText(/Sendero de la Memoria/);
    const first = screen.getByRole('group', { name: /^Viajero 1 de 2.*titular$/ });
    expect(within(first).getByLabelText('Nombre completo')).toHaveValue('Carlos Restrepo');

    await submit();
    const doc = within(first).getByLabelText('Número de documento');
    expect(doc).toHaveFocus();
    expect(doc).toHaveAttribute('aria-invalid', 'true');
    expect(calls).toHaveLength(0);
  });

  it('aparta los cupos con Idempotency-Key y muestra la hora límite', async () => {
    const { calls, router } = renderForm(() => booking);
    await fillAll();
    await submit();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/reservas/bk-1');
    });
    expect(calls).toHaveLength(1);
    expect(calls[0]?.key).toMatch(/^[0-9a-f-]{36}$/);
    expect(calls[0]?.body).toEqual({
      scheduleId: 'b',
      requestedSlots: 2,
      emergencyContact: { fullName: 'Carmen Ruiz', phone: '+573007654321' },
      participants: [
        {
          fullName: 'Carlos Restrepo',
          docType: 'CC',
          docNumber: '1094000111',
          medicalEps: 'Sura',
          isHolder: true,
        },
        {
          fullName: 'Luis Restrepo',
          docType: 'CC',
          docNumber: '1094000222',
          medicalEps: 'Sanitas',
          isHolder: false,
        },
      ],
    });
    // Tras el lock se llega al checkout (T-1.10d) con el TTL que informa el servidor.
    expect(await screen.findByRole('heading', { level: 1, name: 'Paga tu reserva' })).toBeVisible();
    expect(screen.getByRole('timer')).toHaveTextContent('15:00');
    // Nada de los viajeros quedó en el almacenamiento del navegador.
    const stored = [localStorage, sessionStorage]
      .flatMap((store) =>
        Array.from({ length: store.length }, (_, i) => store.getItem(store.key(i) ?? '')),
      )
      .join('|');
    expect(stored).not.toMatch(/Sanitas|1094000222|Carmen/);
  });

  it('reintenta con la misma clave tras una caída de red y usa otra si el cuerpo cambia', async () => {
    const { calls } = renderForm((_call, n) =>
      n < 3 ? new NetworkError(new TypeError('sin red')) : booking,
    );
    await fillAll();
    await submit();
    expect(await screen.findByText(/Sin conexión\. Tus datos siguen aquí/)).toBeVisible();
    await submit();
    await waitFor(() => {
      expect(calls).toHaveLength(2);
    });
    expect(calls[1]?.key).toBe(calls[0]?.key);

    await userEvent.type(
      within(screen.getByRole('group', { name: /Viajero 2 de 2/ })).getByLabelText(/^EPS/),
      ' Plus',
    );
    await submit();
    await waitFor(() => {
      expect(calls).toHaveLength(3);
    });
    expect(calls[2]?.key).not.toBe(calls[0]?.key);
  });

  it('explica cuántos cupos quedan ante un 409', async () => {
    renderForm(
      () =>
        new ApiError(
          {
            type: 'about:blank',
            title: 'x',
            status: 409,
            code: 'BOOKING_INSUFFICIENT_SLOTS',
            availableSlots: 1,
          },
          null,
        ),
    );
    await fillAll();
    await submit();
    const alert = await screen.findByText('Solo quedan 1 cupo en esta franja.');
    expect(alert.closest('[role="alert"]')).not.toBeNull();
    expect(
      screen.getByRole('link', { name: 'Elegir otra franja o ajustar el grupo' }),
    ).toHaveAttribute('href', `/experiencias/${EXPERIENCE_ID}/franjas`);
  });

  it('lleva a la vista el aviso cuando el servidor rechaza el envío', async () => {
    const reveal = vi.spyOn(Element.prototype, 'scrollIntoView');
    renderForm(
      () =>
        new ApiError(
          {
            type: 'about:blank',
            title: 'x',
            status: 409,
            code: 'BOOKING_INSUFFICIENT_SLOTS',
            availableSlots: 1,
          },
          null,
        ),
    );
    await fillAll();
    await submit();
    const alert = (await screen.findByText('Solo quedan 1 cupo en esta franja.')).closest(
      '[role="alert"]',
    );
    await waitFor(() => {
      expect(reveal).toHaveBeenCalled();
    });
    expect(reveal.mock.contexts.at(-1)).toBe(alert);
  });

  it('lleva a la vista el error general (por ejemplo, sesión rechazada)', async () => {
    const reveal = vi.spyOn(Element.prototype, 'scrollIntoView');
    renderForm(() => new ApiError({ type: 'about:blank', title: 'x', status: 403 }, null));
    await fillAll();
    await submit();
    const alert = await screen.findByRole('alert');
    await waitFor(() => {
      expect(reveal).toHaveBeenCalled();
    });
    expect(alert.parentElement).toBe(reveal.mock.contexts.at(-1));
  });

  it('pide HEALTH_EMERGENCY antes de enviar y continúa al otorgarla', async () => {
    const { calls, router } = renderForm(() => booking, { health: 'MISSING' });
    await fillAll();
    expect(screen.getByText(/necesitamos tu autorización para tratar la EPS/)).toBeVisible();
    await submit();
    const dialog = screen.getByRole('dialog', { name: 'Autorización necesaria' });
    expect(calls).toHaveLength(0);
    await userEvent.click(within(dialog).getByRole('checkbox'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Acepto' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/reservas/bk-1');
    });
    expect(calls).toHaveLength(1);
  });

  it('reintenta una vez solo ante un 503 por contención', async () => {
    const { calls, router } = renderForm((_call, n) =>
      n === 1
        ? new ApiError(
            { type: 'about:blank', title: 'x', status: 503, code: 'CONCURRENCY_LOCK_TIMEOUT' },
            null,
          )
        : booking,
    );
    await fillAll();
    await submit();
    await waitFor(
      () => {
        expect(router.state.location.pathname).toBe('/reservas/bk-1');
      },
      { timeout: 3000 },
    );
    expect(calls).toHaveLength(2);
    expect(calls[1]?.key).toBe(calls[0]?.key);
  });

  it('advierte antes de salir con datos sin enviar', async () => {
    const { router } = renderForm(() => booking);
    await fillAll();
    await userEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    const sheet = screen.getByRole('dialog', { name: '¿Salir sin apartar los cupos?' });
    await userEvent.click(within(sheet).getByRole('button', { name: 'Seguir llenando' }));
    expect(router.state.location.pathname).toBe('/reservas/nueva');
    expect(
      within(screen.getByRole('group', { name: /Viajero 2 de 2/ })).getByLabelText(/^EPS/),
    ).toHaveValue('Sanitas');

    await userEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
  });
});
