import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { todayInBogota } from '@/shared/lib';
import { page } from '@/test/fixtures';
import { LAPTOP, stubMatchMedia } from '@/test/matchMedia';
import { renderApp, type FakeRoutes } from '@/test/renderApp';
import { pickOption } from '@/test/select';

/** Sección Personas de la consola (ADR-0043 §1): búsqueda exacta, altas y listas de gestores y anfitriones. */

const ADMIN = { roles: ['ROLE_TOURIST', 'ROLE_ADMIN'] };
const ASSOCIATION_ID = 'a-1';

const association = {
  id: ASSOCIATION_ID,
  nit: '900123456-1',
  rntNumber: '123456',
  legalName: 'Asociación Campesina La Esperanza',
  associationType: 'ASOCIACION_VICTIMAS',
  municipalityCode: '05649',
  address: null,
  contactEmail: 'contacto@ejemplo.com',
  contactPhone: '+573001234567',
  status: 'ACTIVE',
};

const found = {
  userId: 'u-1',
  fullName: 'Laura Gómez',
  email: 'laura@ejemplo.com',
  emailMasked: false,
  status: 'ACTIVE',
  roles: ['TOURIST'],
  hostProfileId: null,
  linkManagerId: null,
  linkManagerStatus: null,
};

const activeManager = {
  linkManagerId: 'm-1',
  userId: 'u-2',
  fullName: 'Gabriel Gestor',
  email: 'gabriel@ejemplo.com',
  associationId: ASSOCIATION_ID,
  associationName: association.legalName,
  accreditationStatus: 'ACTIVE',
  accreditedOn: '2026-10-20',
  accreditedByName: 'Ana Administradora',
};

const revokedManager = {
  ...activeManager,
  linkManagerId: 'm-2',
  userId: 'u-3',
  fullName: 'Gloria Revocada',
  accreditationStatus: 'REVOKED',
};

const host = {
  hostProfileId: 'h-1',
  userId: 'u-4',
  fullName: 'Hernando Anfitrión',
  email: 'h-abcd2345xy@comunidad.integrate.co',
  emailIsSynthetic: true,
  associationId: ASSOCIATION_ID,
  associationName: association.legalName,
  verified: false,
  createdAt: '2026-10-28T15:00:00Z',
};

const apiError = (status: number, code: string) =>
  new ApiError({ type: 'about:blank', title: 'Error', status, code }, 'trace-1');

function routes(extra: FakeRoutes = {}): FakeRoutes {
  return {
    '/v1/admin/users/search': page([found]),
    '/v1/admin/users/u-1': {
      userId: 'u-1',
      fullName: 'Laura Gómez',
      maskedEmail: 'l***@ejemplo.com',
      status: 'ACTIVE',
      docType: 'CC',
      docNumberLast4: '4567',
      roles: ['TOURIST'],
      hostProfileId: null,
      linkManagerId: null,
      linkManagerStatus: null,
      roleHistory: [
        { role: 'TOURIST', revokedAt: null, revokedBy: null },
        { role: 'LINK_MANAGER', revokedAt: '2026-10-30T15:00:00Z', revokedBy: 'u-9' },
      ],
    },
    '/v1/admin/associations': page([association]),
    '/v1/admin/link-managers': (url: URL, options: RequestOptions) => {
      if (options.method === 'POST') {
        return { linkManagerId: 'm-9', accreditationStatus: 'ACTIVE' };
      }
      const status = url.searchParams.get('status');
      const all = [activeManager, revokedManager];
      return page(status ? all.filter((row) => row.accreditationStatus === status) : all);
    },
    '/v1/admin/link-managers/m-1/suspend': { ...activeManager, accreditationStatus: 'SUSPENDED' },
    '/v1/admin/link-managers/m-1/revoke': { ...activeManager, accreditationStatus: 'REVOKED' },
    '/v1/admin/host-profiles': (_url: URL, options: RequestOptions) =>
      options.method === 'POST'
        ? { hostProfileId: 'h-9', userId: 'u-1', associationId: ASSOCIATION_ID }
        : page([host]),
    ...extra,
  };
}

async function searchByEmail(
  user: ReturnType<typeof userEvent.setup>,
  email = 'laura@ejemplo.com',
) {
  await user.type(await screen.findByRole('textbox', { name: 'Correo exacto' }), email);
  await user.click(screen.getByRole('button', { name: 'Buscar' }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Personas · búsqueda (ADR-0043 §1.1)', () => {
  it('encuentra a una persona por correo exacto y ofrece las dos altas', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp('/admin/personas', routes(), ADMIN);

    await searchByEmail(user);
    const card = await screen.findByRole('region', { name: 'Persona encontrada' });
    expect(within(card).getByRole('heading', { level: 3, name: 'Laura Gómez' })).toBeVisible();
    expect(within(card).getByText('laura@ejemplo.com')).toBeVisible();
    expect(within(card).getByRole('list', { name: 'Roles vigentes' })).toHaveTextContent('Turista');
    expect(within(card).getByRole('button', { name: 'Convertir en anfitrión' })).toBeVisible();
    expect(within(card).getByRole('button', { name: 'Acreditar como gestor' })).toBeVisible();
    expect(http.calls.map(([path]) => path)).toContain(
      '/v1/admin/users/search?email=laura%40ejemplo.com',
    );
  });

  it('no busca con un correo mal escrito y deja el foco en el campo', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp('/admin/personas', routes(), ADMIN);

    await searchByEmail(user, 'laura');
    const field = screen.getByRole('textbox', { name: 'Correo exacto' });
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveFocus();
    expect(http.calls.some(([path]) => path.startsWith('/v1/admin/users/search'))).toBe(false);
  });

  it('busca por documento con su tipo y avisa que el correo sale oculto', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp(
      '/admin/personas',
      routes({
        '/v1/admin/users/search': page([
          { ...found, email: 'l***@ejemplo.com', emailMasked: true },
        ]),
      }),
      ADMIN,
    );

    await user.click(await screen.findByRole('radio', { name: 'Documento' }));
    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(screen.getByRole('textbox', { name: 'Número de documento' })).toHaveAttribute(
      'aria-invalid',
      'true',
    );

    await user.type(screen.getByRole('textbox', { name: 'Número de documento' }), 'D1234567');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    const card = await screen.findByRole('region', { name: 'Persona encontrada' });
    expect(within(card).getByText(/parcialmente oculto/)).toBeVisible();
    expect(http.calls.map(([path]) => path)).toContain(
      '/v1/admin/users/search?docType=CC&document=D1234567',
    );
  });

  it('dice cuando no hay una cuenta con ese dato', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp('/admin/personas', routes({ '/v1/admin/users/search': page([]) }), ADMIN);

    await searchByEmail(user, 'nadie@ejemplo.com');
    expect(await screen.findByText(/No hay una cuenta con ese dato/)).toBeVisible();
    expect(screen.queryByRole('region', { name: 'Persona encontrada' })).toBeNull();
  });

  it('explica el límite de 20 búsquedas por minuto (429)', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp(
      '/admin/personas',
      routes({ '/v1/admin/users/search': apiError(429, 'ADMIN_LOOKUP_RATE_LIMITED') }),
      ADMIN,
    );

    await searchByEmail(user);
    expect(await screen.findByText(/Hiciste demasiadas búsquedas seguidas/)).toBeVisible();
  });

  it('muestra el historial de roles solo cuando se pide', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp('/admin/personas', routes(), ADMIN);

    await searchByEmail(user);
    await screen.findByRole('region', { name: 'Persona encontrada' });
    expect(http.calls.some(([path]) => path === '/v1/admin/users/u-1')).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Ver historial de roles' }));
    const history = await screen.findByRole('list', { name: 'Historial de roles' });
    expect(within(history).getByText(/Gestor de enlace/)).toBeVisible();
    expect(within(history).getByText(/revocado el/)).toBeVisible();
    expect(screen.getByText(/terminado en 4567/)).toBeVisible();
  });
});

describe('Personas · altas', () => {
  it('convierte en anfitrión tras elegir la asociación y avisa del rol diferido', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp('/admin/personas', routes(), ADMIN);

    await searchByEmail(user);
    await user.click(await screen.findByRole('button', { name: 'Convertir en anfitrión' }));
    const dialog = await screen.findByRole('dialog', { name: 'Convertir en anfitrión' });
    expect(within(dialog).getByText(/vuelva a ingresar o se renueve su sesión/)).toBeVisible();

    await user.click(within(dialog).getByRole('button', { name: 'Convertir en anfitrión' }));
    expect(await within(dialog).findByText('Elige la asociación.')).toBeVisible();
    expect(
      http.calls.some(
        ([path, options]) => path.endsWith('/host-profiles') && options?.method === 'POST',
      ),
    ).toBe(false);

    await pickOption(
      user,
      within(dialog).getByRole('combobox', { name: 'Asociación' }),
      association.legalName,
    );
    await user.click(within(dialog).getByRole('button', { name: 'Convertir en anfitrión' }));

    expect(await screen.findByText('Laura Gómez ya es anfitrión.')).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/host-profiles',
      { method: 'POST', body: { userId: 'u-1', associationId: ASSOCIATION_ID } },
    ]);
    // La tarjeta se actualiza sin repetir la búsqueda.
    const card = screen.getByRole('region', { name: 'Persona encontrada' });
    expect(within(card).getByText('Ya es anfitrión.')).toBeVisible();
    expect(within(card).queryByRole('button', { name: 'Convertir en anfitrión' })).toBeNull();
  });

  it('acredita a un gestor con la fecha de hoy y traduce el 409 de acreditación vigente', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    let attempts = 0;
    const { http } = renderApp(
      '/admin/personas',
      routes({
        '/v1/admin/link-managers': (_url: URL, options: RequestOptions) => {
          if (options.method !== 'POST') return page([activeManager]);
          attempts += 1;
          return attempts === 1
            ? apiError(409, 'ENROLLMENT_MANAGER_EXISTS')
            : { linkManagerId: 'm-9', accreditationStatus: 'ACTIVE' };
        },
      }),
      ADMIN,
    );

    await searchByEmail(user);
    await user.click(await screen.findByRole('button', { name: 'Acreditar como gestor' }));
    const dialog = await screen.findByRole('dialog', { name: 'Acreditar como gestor de enlace' });
    expect(within(dialog).getByLabelText('Fecha de acreditación')).toHaveValue(todayInBogota());

    await pickOption(
      user,
      within(dialog).getByRole('combobox', { name: 'Asociación' }),
      association.legalName,
    );
    await user.click(within(dialog).getByRole('button', { name: 'Acreditar gestor' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Esa persona ya tiene una acreditación de gestor vigente.',
    );

    await user.click(within(dialog).getByRole('button', { name: 'Acreditar gestor' }));
    expect(await screen.findByText(/quedó acreditado como gestor de enlace/)).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/link-managers',
      {
        method: 'POST',
        body: { userId: 'u-1', associationId: ASSOCIATION_ID, accreditedOn: todayInBogota() },
      },
    ]);
    const card = screen.getByRole('region', { name: 'Persona encontrada' });
    expect(within(card).getByText(/Ya tiene acreditación de gestor \(Vigente\)/)).toBeVisible();
  });

  it('no ofrece el alta a quien ya es anfitrión o tiene acreditación', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp(
      '/admin/personas',
      routes({
        '/v1/admin/users/search': page([
          {
            ...found,
            roles: ['TOURIST', 'HOST', 'LINK_MANAGER'],
            hostProfileId: 'h-1',
            linkManagerId: 'm-1',
            linkManagerStatus: 'SUSPENDED',
          },
        ]),
      }),
      ADMIN,
    );

    await searchByEmail(user);
    const card = await screen.findByRole('region', { name: 'Persona encontrada' });
    expect(within(card).getByText('Ya es anfitrión.')).toBeVisible();
    expect(within(card).getByText(/Ya tiene acreditación de gestor \(Suspendido\)/)).toBeVisible();
    expect(within(card).queryByRole('button', { name: /Convertir|Acreditar/ })).toBeNull();
  });
});

describe('Personas · listas', () => {
  it('lista los gestores vigentes y suspende a uno tras confirmar', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp('/admin/personas', routes(), ADMIN);

    const table = await screen.findByRole('table', { name: /Gestores de enlace/ });
    expect(await within(table).findByRole('rowheader', { name: /Gabriel Gestor/ })).toBeVisible();
    expect(http.calls.map(([path]) => path)).toContain(
      '/v1/admin/link-managers?page=0&size=20&status=ACTIVE',
    );
    expect(within(table).getByText('por Ana Administradora')).toBeVisible();

    await user.click(within(table).getByRole('button', { name: 'Suspender a Gabriel Gestor' }));
    const dialog = screen.getByRole('dialog', { name: 'Suspender al gestor' });
    expect(within(dialog).getByText('Gabriel Gestor')).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Suspender' }));

    expect(await screen.findByText('Gabriel Gestor quedó suspendido.')).toBeVisible();
    expect(http.calls).toContainEqual(['/v1/admin/link-managers/m-1/suspend', { method: 'POST' }]);
  });

  it('avisa que revocar no se puede deshacer y que el token puede seguir vigente', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp('/admin/personas', routes(), ADMIN);

    await user.click(await screen.findByRole('button', { name: 'Revocar a Gabriel Gestor' }));
    const dialog = screen.getByRole('dialog', { name: 'Revocar la acreditación' });
    expect(within(dialog).getByText(/no se puede deshacer/)).toBeVisible();
    expect(within(dialog).getByText(/hasta 15 minutos/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Revocar definitivamente' }));
    expect(await screen.findByText(/su acreditación fue revocada/)).toBeVisible();
  });

  it('traduce el 409 cuando la acreditación ya cambió de estado', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp(
      '/admin/personas',
      routes({
        '/v1/admin/link-managers/m-1/suspend': apiError(409, 'CATALOG_INVALID_STATUS_TRANSITION'),
      }),
      ADMIN,
    );

    await user.click(await screen.findByRole('button', { name: 'Suspender a Gabriel Gestor' }));
    const dialog = screen.getByRole('dialog', { name: 'Suspender al gestor' });
    await user.click(within(dialog).getByRole('button', { name: 'Suspender' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'La acreditación cambió de estado mientras la revisabas',
    );
  });

  it('los revocados no tienen acciones y el filtro «Todos» los muestra', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { router } = renderApp('/admin/personas', routes(), ADMIN);

    await screen.findByRole('rowheader', { name: /Gabriel Gestor/ });
    expect(screen.queryByRole('rowheader', { name: /Gloria Revocada/ })).toBeNull();
    const filters = screen.getByRole('navigation', { name: 'Filtrar gestores por estado' });
    await user.click(within(filters).getByRole('link', { name: 'Todos' }));
    expect(router.state.location.search).toBe('?vista=gestores&estado=TODOS');

    const row = (await screen.findByRole('rowheader', { name: /Gloria Revocada/ })).closest('tr');
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText('Sin acciones')).toBeVisible();
  });

  it('la lista de anfitriones rotula los correos internos y filtra por asociación', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http, router } = renderApp('/admin/personas?vista=anfitriones', routes(), ADMIN);

    const table = await screen.findByRole('table', { name: /Anfitriones/ });
    expect(await within(table).findByText(/\(interno\)/)).toBeVisible();
    expect(within(table).getByText('Sin verificar')).toBeVisible();

    await pickOption(
      user,
      screen.getByRole('combobox', { name: 'Filtrar por asociación' }),
      association.legalName,
    );
    await waitFor(() => {
      expect(router.state.location.search).toBe(`?vista=anfitriones&asociacion=${ASSOCIATION_ID}`);
    });
    await waitFor(() => {
      expect(http.calls.map(([path]) => path)).toContain(
        `/v1/admin/host-profiles?page=0&size=20&associationId=${ASSOCIATION_ID}`,
      );
    });
  });
});

describe('Personas · accesibilidad', () => {
  it(
    'la pantalla, con un resultado y las listas, no tiene violaciones WCAG 2.1 A/AA',
    { timeout: 60_000 },
    async () => {
      stubMatchMedia(LAPTOP);
      const findings: string[] = [];
      for (const path of ['/admin/personas', '/admin/personas?vista=anfitriones']) {
        const user = userEvent.setup();
        const { unmount } = renderApp(path, routes(), ADMIN);
        await screen.findByRole('heading', { level: 1, name: 'Personas' });
        await searchByEmail(user);
        await screen.findByRole('region', { name: 'Persona encontrada' });
        await waitFor(() => {
          expect(screen.queryByText(/^Cargando/)).toBeNull();
        });
        expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
        const results = await axe.run(document.body, {
          runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
          rules: { 'color-contrast': { enabled: false } },
        });
        for (const violation of results.violations) {
          findings.push(`${path}: ${violation.id} (${String(violation.nodes.length)})`);
        }
        unmount();
      }
      expect(findings).toEqual([]);
    },
  );
});
