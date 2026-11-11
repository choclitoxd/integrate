import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test/renderApp';

const experience = {
  id: 'exp-1',
  title: 'Caminata al mirador',
  description: 'Recorrido por el sendero.',
  safetyGuidelines: 'Lleva agua.',
  status: 'DRAFT',
  rejectionReason: null,
  unitPriceCop: 45000,
  maxCapacityPerSlot: 8,
  durationMinutes: 180,
  physicalIntensity: 'MEDIUM',
  municipalityCode: '63001',
  vereda: null,
  meetingPoint: 'Plaza principal',
  latitude: null,
  longitude: null,
  categoryCodes: [],
  meetingPointCheck: null,
  createdAt: '2026-11-10T12:00:00Z',
};

describe('mis experiencias del anfitrión (mini administrador)', () => {
  it('crea la experiencia en la misma ventana emergente que usa el administrador', async () => {
    renderApp(
      '/anfitrion/experiencias',
      { '/v1/host/experiences': { items: [], totalPages: 1 } },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText(/Aún no tienes experiencias/)).toBeVisible();
    expect(screen.queryByRole('dialog')).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Nueva experiencia' }));

    const dialog = await screen.findByRole('dialog', { name: 'Nueva experiencia' });
    expect(within(dialog).getByLabelText('Título')).toBeVisible();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cerrar' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByRole('button', { name: 'Nueva experiencia' })).toBeVisible();
  });

  it('lista las experiencias y las gestiona en una ventana emergente con su estado', async () => {
    renderApp(
      '/anfitrion/experiencias',
      {
        '/v1/host/experiences': { items: [experience], totalPages: 1 },
        '/v1/host/experiences/exp-1': experience,
        '/v1/host/experiences/exp-1/schedules': [],
      },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText('Caminata al mirador')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Gestionar Caminata al mirador' }));

    const dialog = await screen.findByRole('dialog', { name: 'Caminata al mirador' });
    expect(await within(dialog).findByRole('button', { name: 'Enviar a revisión' })).toBeVisible();
    expect(within(dialog).getByLabelText('Título')).toHaveValue('Caminata al mirador');
  });

  it('busca por nombre y filtra por estado entre sus experiencias', async () => {
    const published = {
      ...experience,
      id: 'exp-2',
      title: 'Taller de tejido',
      status: 'APPROVED',
      meetingPoint: 'Casa cural',
    };
    renderApp(
      '/anfitrion/experiencias',
      { '/v1/host/experiences': { items: [experience, published], totalPages: 1 } },
      { roles: ['ROLE_HOST'] },
    );
    expect(await screen.findByText('Caminata al mirador')).toBeVisible();
    expect(screen.getByText('Taller de tejido')).toBeVisible();

    await userEvent.type(
      screen.getByRole('searchbox', { name: 'Buscar en mis experiencias' }),
      'tejido',
    );
    expect(screen.queryByText('Caminata al mirador')).toBeNull();
    expect(screen.getByText('Taller de tejido')).toBeVisible();

    await userEvent.clear(screen.getByRole('searchbox', { name: 'Buscar en mis experiencias' }));
    await userEvent.click(screen.getByRole('button', { name: /^Borrador/ }));
    expect(screen.getByText('Caminata al mirador')).toBeVisible();
    expect(screen.queryByText('Taller de tejido')).toBeNull();

    await userEvent.type(
      screen.getByRole('searchbox', { name: 'Buscar en mis experiencias' }),
      'zzz',
    );
    expect(await screen.findByText(/Ninguna experiencia coincide/)).toBeVisible();
  });
});
