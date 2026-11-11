import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api';
import { pickOption } from '@/test/select';
import { renderApp } from '@/test/renderApp';

const page = {
  items: [
    {
      bookingId: 'bk-1',
      experienceTitle: 'Recorrido del río',
      startsAt: '2026-11-12T14:00:00Z',
      holderName: 'Ana Gómez',
      scheduleId: 'schedule-1',
      slotsCount: 2,
      scheduleBookings: 3,
      scheduleSlots: 7,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('reservas del anfitrión', () => {
  it('aplica los filtros en el servidor y muestra los totales de la franja', async () => {
    const user = userEvent.setup();
    const { http } = renderApp(
      '/anfitrion',
      { '/v1/host/bookings': page },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText('Jornada: 3 reservas · 7 cupos reservados')).toBeVisible();
    await pickOption(user, screen.getByRole('combobox', { name: 'Jornada' }), 'Próximas');
    await userEvent.type(screen.getByLabelText('Nombre de la experiencia'), 'río');
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
    expect(await screen.findByText('Personas en la reserva: 2')).toBeVisible();
    expect(
      http.calls.some(([path]) => {
        const params = new URL(path, 'http://test').searchParams;
        return params.get('period') === 'UPCOMING' && params.get('experience') === 'río';
      }),
    ).toBe(true);
  });

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

  it('muestra el resumen, aplica una fecha rápida y limpia los filtros', async () => {
    const user = userEvent.setup();
    const { http } = renderApp(
      '/anfitrion',
      { '/v1/host/bookings': page },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText('Personas en esta lista')).toBeVisible();
    expect(screen.getByText('Reservas', { selector: 'dt' })).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Hoy' }));
    expect(screen.getByLabelText('Fecha de la jornada')).not.toHaveValue('');
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
    await waitFor(() => {
      expect(
        http.calls.some(([path]) => new URL(path, 'http://test').searchParams.has('date')),
      ).toBe(true);
    });

    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    expect(screen.getByLabelText('Fecha de la jornada')).toHaveValue('');
  });

  it('explica el estado vacío y lleva a las experiencias del anfitrión', async () => {
    renderApp(
      '/anfitrion',
      { '/v1/host/bookings': { items: [], page: 0, totalPages: 0, totalElements: 0 } },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText('No tienes reservas confirmadas para mostrar.')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Ver mis experiencias' })).toHaveAttribute(
      'href',
      '/anfitrion/experiencias',
    );
  });
});
