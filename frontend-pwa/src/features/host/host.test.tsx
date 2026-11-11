import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api';
import { renderApp } from '@/test/renderApp';

const page = {
  items: [
    {
      bookingId: 'bk-1',
      experienceTitle: 'Recorrido del río',
      startsAt: '2026-11-12T14:00:00Z',
      holderName: 'Ana Gómez',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('reservas del anfitrión', () => {
  it('consulta la EPS solo al pedirla y la borra al cerrar', async () => {
    const { http } = renderApp(
      '/anfitrion',
      {
        '/v1/host/bookings': page,
        '/v1/host/bookings/bk-1/holder-eps': { medicalEps: 'EPS de prueba' },
      },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText('Titular: Ana Gómez')).toBeVisible();
    expect(http.calls.filter(([path]) => path.includes('holder-eps'))).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: /Consultar EPS en emergencia/ }));
    const dialog = screen.getByRole('dialog', { name: 'EPS del titular' });
    expect(await within(dialog).findByText('EPS de prueba')).toBeVisible();
    expect(http.calls.filter(([path]) => path.includes('holder-eps'))).toHaveLength(1);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cerrar' }));
    expect(screen.queryByText('EPS de prueba')).not.toBeInTheDocument();
    expect(JSON.stringify([localStorage, sessionStorage])).not.toContain('EPS de prueba');
  });

  it('informa cuando no existe autorización para divulgar la EPS', async () => {
    renderApp(
      '/anfitrion',
      {
        '/v1/host/bookings': page,
        '/v1/host/bookings/bk-1/holder-eps': new ApiError(
          { type: 'about:blank', title: 'x', status: 403 },
          'trace-1',
        ),
      },
      { roles: ['ROLE_HOST'] },
    );
    await userEvent.click(
      await screen.findByRole('button', { name: /Consultar EPS en emergencia/ }),
    );
    const dialog = screen.getByRole('dialog', { name: 'EPS del titular' });
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'La autorización requerida no está disponible',
    );
    expect(within(dialog).getByText(/trace-1/)).toBeVisible();
  });

  it('distingue una EPS no proporcionada y protege la ruta por rol', async () => {
    const first = renderApp(
      '/anfitrion',
      {
        '/v1/host/bookings': page,
        '/v1/host/bookings/bk-1/holder-eps': { medicalEps: null },
      },
      { roles: ['ROLE_HOST'] },
    );
    await userEvent.click(
      await screen.findByRole('button', { name: /Consultar EPS en emergencia/ }),
    );
    expect(await screen.findByText('El titular no proporcionó una EPS.')).toBeVisible();
    first.unmount();
    const second = renderApp('/anfitrion', {}, { roles: ['ROLE_TOURIST'] });
    expect(
      await screen.findByRole('heading', {
        name: 'Esta sección no está disponible para tu cuenta',
      }),
    ).toBeVisible();
    expect(second.http.calls.some(([path]) => path.includes('/host/bookings'))).toBe(false);
  });
});
