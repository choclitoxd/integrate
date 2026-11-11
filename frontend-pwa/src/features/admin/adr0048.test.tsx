import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LAPTOP, stubMatchMedia } from '@/test/matchMedia';
import { renderApp, type FakeRoutes } from '@/test/renderApp';

const ADMIN = {
  id: 'u-admin',
  fullName: 'Admin User',
  email: 'admin@integrate.co',
  roles: ['ROLE_ADMIN'] as const,
  docType: 'CC' as const,
  docNumberLast4: '0000',
  phone: '+57 300 000 0000',
  requiresPasswordChange: false,
};

const hostOption = {
  hostProfileId: 'h-1',
  userId: 'u-1',
  fullName: 'Carlos Anfitrión',
  email: 'carlos@ejemplo.com',
  emailIsSynthetic: false,
  associationId: 'a-1',
  associationName: 'Asociación Chameza',
  verified: true,
  createdAt: '2026-11-01T10:00:00Z',
};

const touristUser = {
  userId: 'u-tourist',
  fullName: 'Turista Prueba',
  email: 'turista@ejemplo.com',
  emailMasked: false,
  status: 'ACTIVE',
  roles: ['TOURIST'],
  hostProfileId: null,
  linkManagerId: null,
  linkManagerStatus: null,
};

function page<T>(items: readonly T[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
    first: true,
    last: true,
  };
}

function routes(extra: FakeRoutes = {}): FakeRoutes {
  return {
    '/v1/admin/experiences': page([]),
    '/v1/admin/host-profiles': page([hostOption]),
    '/v1/admin/users': page([touristUser]),
    '/v1/admin/associations': page([]),
    ...extra,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ADR-0048: Altas administrativas y listado por roles', () => {
  it('en Experiencias muestra el botón de Nueva experiencia y abre la ventana de creación', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp(
      '/admin/experiencias',
      routes({
        '/v1/admin/experiences': (_url: URL, options: { method?: string }) => {
          if (options.method === 'POST') {
            return {
              id: 'exp-new',
              title: 'Caminata Senderos de la Cascada',
              status: 'DRAFT',
              unitPriceCop: 85000,
              meetingPoint: 'Parque Principal',
              meetingPointDoubtful: false,
              municipalityCode: '85015',
              vereda: 'El Bosque',
              categoryCodes: ['ECOL', 'AGRO'],
              createdAt: '2026-11-10T15:00:00Z',
            };
          }
          return page([]);
        },
      }),
      ADMIN,
    );

    const newBtn = await screen.findByRole('button', { name: /Nueva experiencia/i });
    expect(newBtn).toBeVisible();
    await user.click(newBtn);

    const dialog = await screen.findByRole('dialog', { name: /Nueva experiencia/i });
    expect(dialog).toBeVisible();
    expect(within(dialog).getByRole('combobox', { name: /Anfitrión/i })).toBeVisible();
  });

  it('en Personas muestra el bloque de Roles con Todos, Turistas, Gestores y Anfitriones y botón de alta', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp('/admin/personas', routes(), ADMIN);

    expect(await screen.findByText('Roles')).toBeVisible();
    const rolesNav = screen.getByRole('navigation', { name: 'Roles' });
    expect(within(rolesNav).getByRole('link', { name: 'Todos' })).toBeVisible();
    expect(within(rolesNav).getByRole('link', { name: 'Turistas' })).toBeVisible();
    expect(within(rolesNav).getByRole('link', { name: 'Gestores de enlace' })).toBeVisible();
    expect(within(rolesNav).getByRole('link', { name: 'Anfitriones' })).toBeVisible();

    const newUserBtn = screen.getByRole('button', { name: /Nuevo usuario/i });
    expect(newUserBtn).toBeVisible();
    await user.click(newUserBtn);

    const sheet = await screen.findByRole('dialog', { name: /Nuevo usuario/i });
    expect(sheet).toBeVisible();
    expect(within(sheet).getByRole('textbox', { name: /Nombre completo/i })).toBeVisible();
    expect(within(sheet).getByRole('textbox', { name: /Correo electrónico/i })).toBeVisible();
  });
});
