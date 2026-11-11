import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test/renderApp';

const monthly = [
  ...Array.from({ length: 10 }, (_, index) => ({
    month: `2026-${String(index + 1).padStart(2, '0')}`,
    reservations: 0,
    people: 0,
  })),
  { month: '2026-10', reservations: 3, people: 7 },
  { month: '2026-11', reservations: 10, people: 24 },
];

const metrics = {
  month: '2026-11',
  currentMonth: '2026-11',
  earliestMonth: '2025-12',
  updatedAt: '2026-11-11T15:00:00Z',
  reservations: 10,
  people: 24,
  upcoming: 3,
  upcomingPeople: 9,
  ranking: [
    { experienceId: 'e1', title: 'Caminata al mirador', reservations: 6, people: 14 },
    { experienceId: 'e2', title: 'Taller de tejido', reservations: 4, people: 10 },
  ],
  daily: [
    { date: '2026-11-12', reservations: 4, people: 9 },
    { date: '2026-11-14', reservations: 6, people: 15 },
  ],
  monthly,
  occupancy: { slots: 40, booked: 10 },
};

describe('mis métricas del anfitrión', () => {
  it('muestra las cifras y las gráficas con su resumen para lectores de pantalla', async () => {
    renderApp('/anfitrion/metricas', { '/v1/host/metrics': metrics }, { roles: ['ROLE_HOST'] });
    expect(await screen.findByText('Reservas confirmadas')).toBeVisible();
    expect(screen.getByText('Personas reservadas')).toBeVisible();

    expect(
      screen.getByRole('img', { name: /Reservas por día: 10 reservas\. El mayor valor fue .*14/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Reservas por mes: 13 reservas/ })).toBeInTheDocument();

    expect(screen.getByText('25 %')).toBeVisible();
    expect(screen.getByRole('meter', { name: 'Ocupación de próximas jornadas' })).toHaveAttribute(
      'aria-valuetext',
      '10 de 40 cupos reservados',
    );
    expect(screen.getByText('Caminata al mirador: 6 reservas · 60 % del mes')).toBeInTheDocument();
  });

  it('ofrece cada gráfica también como tabla', async () => {
    renderApp('/anfitrion/metricas', { '/v1/host/metrics': metrics }, { roles: ['ROLE_HOST'] });
    const heading = await screen.findByRole('heading', { name: 'Reservas por día' });
    const daily = heading.closest('section');
    if (!daily) throw new Error('La gráfica diaria no está dentro de una sección.');
    expect(within(daily).queryByRole('table')).toBeNull();

    await userEvent.click(within(daily).getByRole('button', { name: 'Ver como tabla' }));
    const table = within(daily).getByRole('table', { name: 'Reservas y personas por día del mes' });
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).getByRole('row', { name: /14 de noviembre.* 6 15/ })).toBeVisible();

    await userEvent.click(within(daily).getByRole('button', { name: 'Ver gráfica' }));
    expect(within(daily).queryByRole('table')).toBeNull();
  });

  it('explica el mes sin reservas y la falta de jornadas próximas en vez de mostrar solo ceros', async () => {
    renderApp(
      '/anfitrion/metricas',
      {
        '/v1/host/metrics': {
          ...metrics,
          reservations: 0,
          people: 0,
          ranking: [],
          daily: [],
          occupancy: { slots: 0, booked: 0 },
        },
      },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText(/No hay reservas confirmadas para este mes/)).toBeVisible();
    expect(screen.getByText(/Sin jornadas próximas/)).toBeVisible();
    expect(
      screen.getByRole('img', { name: 'Reservas por día: sin reservas.' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('meter')).toBeNull();
  });
});
