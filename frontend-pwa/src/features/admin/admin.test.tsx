import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { catalogRoutes, page } from '@/test/fixtures';
import { LAPTOP, stubMatchMedia } from '@/test/matchMedia';
import { renderApp, type FakeRoutes } from '@/test/renderApp';

const ADMIN = { roles: ['ROLE_TOURIST', 'ROLE_ADMIN'] };

const experience = {
  id: 'e-1',
  hostId: 'h-1',
  title: 'Sendero de la Memoria',
  description: 'Recorrido por la memoria del retorno campesino.',
  safetyGuidelines: 'Usa botas y lleva agua.',
  physicalIntensity: 'MEDIUM',
  durationMinutes: 270,
  municipalityCode: '05649',
  vereda: 'La Esperanza',
  meetingPoint: 'Parque principal de San Carlos',
  latitude: 6.187,
  longitude: -74.995,
  stops: [{ name: 'Mirador del río', description: null }],
  maxCapacityPerSlot: 12,
  unitPriceCop: 80000,
  categoryCodes: ['MEMORIA_HISTORICA'],
  status: 'PENDING_REVIEW',
  rejectionReason: null,
  createdAt: '2026-11-01T15:00:00Z',
  meetingPointCheck: {
    status: 'MISMATCH',
    distanceMeters: 820,
    suggestedLatitude: null,
    suggestedLongitude: null,
    checkedAt: '2026-11-01T15:01:00Z',
  },
};

const association = {
  id: 'a-1',
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

const auditRecord = {
  id: 41,
  createdAt: '2026-11-02T19:05:33Z',
  module: 'booking',
  entityName: 'Booking',
  entityId: '0d9f2c1e-aaaa-bbbb-cccc-000000000001',
  action: 'BOOKING_CONFIRMED',
  severity: 'INFO',
  actorId: '7a1b2c3d-aaaa-bbbb-cccc-000000000002',
  actorIp: '203.0.113.7',
  userAgent: 'Mozilla/5.0',
  details: { slots: 2 },
};

const conflict = (code: string) =>
  new ApiError({ type: 'about:blank', title: 'Conflict', status: 409, code }, 'trace-1');

function adminRoutes(extra: FakeRoutes = {}): FakeRoutes {
  return {
    ...catalogRoutes,
    '/v1/admin/experiences': (url: URL) => {
      const status = url.searchParams.get('status');
      return page(status === null || status === experience.status ? [experience] : []);
    },
    '/v1/admin/associations': (_url: URL, options: RequestOptions) =>
      options.method === 'POST' ? association : page([association]),
    '/v1/admin/incidents': page([]),
    '/v1/admin/payout-batches': page([]),
    '/v1/admin/audit': page([auditRecord]),
    '/v1/admin/audit/verify': {
      intact: true,
      fromId: 1,
      toId: 41,
      checked: 41,
      brokenId: null,
      problem: null,
    },
    ...extra,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('consola de administración (ADR-0042, ADR-0044)', () => {
  it('lleva a ingresar al visitante y conserva el destino', async () => {
    const { router } = renderApp('/admin', adminRoutes());
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/ingresar');
    });
    expect(router.state.location.search).toBe('?volver=%2Fadmin');
  });

  it.each([['ROLE_TOURIST'], ['ROLE_LINK_MANAGER']])(
    'niega la consola a una cuenta con solo %s',
    async (role) => {
      stubMatchMedia(LAPTOP);
      renderApp('/admin', adminRoutes(), { roles: [role] });
      expect(
        await screen.findByRole('heading', {
          name: 'Esta sección no está disponible para tu cuenta',
        }),
      ).toBeVisible();
      expect(screen.queryByRole('navigation', { name: 'Navegación de administración' })).toBeNull();
    },
  );

  it('usa AppShell con la barra verde institucional y navegación exclusiva de administrador (sin Explorar ni Mis experiencias)', async () => {
    stubMatchMedia(LAPTOP);
    renderApp('/admin', adminRoutes(), ADMIN);

    const heading = await screen.findByRole('heading', { level: 1, name: 'Resumen' });
    expect(heading).toBeVisible();
    expect(heading.className).toContain('text-brand');

    const nav = screen.getByRole('navigation', { name: 'Navegación de administración' });
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual([
      'Resumen',
      'Experiencias',
      'Asociaciones',
      'Personas',
      'Incidentes',
      'Liquidaciones',
      'Auditoría',
      'Mi cuenta',
    ]);
    expect(within(nav).getByRole('link', { name: 'Resumen' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    const accountLink = within(nav).getByRole('link', { name: 'Mi cuenta' });
    expect(accountLink.className).toContain('whitespace-nowrap');
    expect(accountLink.closest('li')?.className).toContain('border-brand-border');
    // Sin los apartados del turista ni enlace de «Volver al sitio».
    expect(screen.queryByRole('link', { name: 'Explorar' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Mis experiencias' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Volver al sitio' })).toBeNull();
    expect(screen.queryByRole('navigation', { name: 'Navegación principal' })).toBeNull();
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido');

    const pending = await screen.findByRole('link', { name: /Por revisar/ });
    await waitFor(() => {
      expect(pending).toHaveTextContent('1');
    });
    expect(screen.getByRole('link', { name: /Puntos de encuentro dudosos/ })).toHaveTextContent(
      '1',
    );
  });

  it('en celular mantiene el mismo AppShell verde y sus secciones administrativas', async () => {
    renderApp('/admin/experiencias', adminRoutes(), ADMIN);
    expect(await screen.findByRole('heading', { level: 1, name: 'Experiencias' })).toBeVisible();
    expect(await screen.findByRole('table', { name: /Por revisar/ })).toBeVisible();
    const nav = screen.getByRole('navigation', { name: 'Navegación de administración' });
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Resumen', 'Mi cuenta']);
    await userEvent.click(within(nav).getByRole('button', { name: 'Módulos' }));
    const menu = await screen.findByRole('dialog', { name: 'Módulos de administración' });
    expect(within(menu).getByRole('link', { name: 'Experiencias' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).queryByRole('link', { name: 'Explorar' })).toBeNull();
  });

  it('redirige al administrador desde / y /mis-experiencias hacia /admin, y /cuenta hacia /admin/cuenta', async () => {
    const fromRoot = renderApp('/', adminRoutes(), ADMIN);
    await waitFor(() => {
      expect(fromRoot.router.state.location.pathname).toBe('/admin');
    });
    fromRoot.unmount();

    const fromBookings = renderApp('/mis-experiencias', adminRoutes(), ADMIN);
    await waitFor(() => {
      expect(fromBookings.router.state.location.pathname).toBe('/admin');
    });
    fromBookings.unmount();

    const account = renderApp(
      '/cuenta',
      adminRoutes({
        '/v1/me/consents': {
          policyVersion: 'v0.1',
          consents: [
            {
              purpose: 'PERSONAL_DATA',
              state: 'VALID',
              policyVersion: 'v0.1',
              occurredAt: '2026-10-09T10:00:00Z',
            },
            {
              purpose: 'HEALTH_EMERGENCY',
              state: 'MISSING',
              policyVersion: null,
              occurredAt: null,
            },
          ],
        },
      }),
      ADMIN,
    );
    await waitFor(() => {
      expect(account.router.state.location.pathname).toBe('/admin/cuenta');
    });
    expect(await screen.findByRole('heading', { level: 1, name: 'Mi cuenta' })).toBeVisible();
    const nav = screen.getByRole('navigation', { name: 'Navegación de administración' });
    expect(within(nav).getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.queryByRole('link', { name: 'Explorar' })).toBeNull();
    expect(
      screen.queryByRole('button', { name: 'Autorizar datos de salud para reservar' }),
    ).toBeNull();
  });

  it('tras ingresar sin destino pedido, el administrador aterriza en la consola', async () => {
    const admin = renderApp('/ingresar', adminRoutes(), ADMIN);
    await waitFor(() => {
      expect(admin.router.state.location.pathname).toBe('/admin');
    });
    admin.unmount();
    const withTarget = renderApp('/ingresar?volver=%2Fcuenta', adminRoutes(), ADMIN);
    await waitFor(() => {
      expect(withTarget.router.state.location.pathname).toBe('/admin/cuenta');
    });
  });
});

describe('moderación de experiencias', () => {
  it('lista la cola por revisar y abre la ficha en ventana emergente desde la columna Acciones', async () => {
    stubMatchMedia(LAPTOP);
    const { router } = renderApp('/admin/experiencias', adminRoutes(), ADMIN);

    const table = await screen.findByRole('table', { name: /Por revisar/ });
    expect(await within(table).findByRole('rowheader', { name: experience.title })).toBeVisible();
    await userEvent.click(
      within(table).getByRole('button', { name: `Gestionar ${experience.title}` }),
    );
    expect(router.state.location.search).toBe('?estado=PENDING_REVIEW&ver=e-1');

    const ficha = screen.getByRole('dialog', { name: experience.title });
    expect(within(ficha).getByRole('heading', { level: 2, name: experience.title })).toBeVisible();
    expect(within(ficha).getByText('a 820 m de la dirección escrita')).toBeVisible();
    expect(
      within(ficha).getByRole('button', { name: 'Confirmar el punto de encuentro' }),
    ).toBeVisible();
    expect(within(ficha).getByRole('button', { name: 'Aprobar y publicar' })).toBeVisible();
    expect(within(ficha).getByRole('button', { name: 'Rechazar' })).toBeVisible();
    expect(within(ficha).getByRole('button', { name: 'Guardar cambios' })).toBeVisible();
  });

  it('permite al administrador editar y guardar los datos de una experiencia desde la ventana emergente', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const updatedExperience = {
      ...experience,
      title: 'Sendero de la Memoria Renovado',
      unitPriceCop: 95000,
    };
    const { http } = renderApp(
      '/admin/experiencias?ver=e-1',
      adminRoutes({
        '/v1/admin/experiences/e-1': (_url: URL, options: RequestOptions) =>
          options.method === 'PUT' ? updatedExperience : experience,
      }),
      ADMIN,
    );
    const ficha = await screen.findByRole('dialog', { name: experience.title });
    const titleInput = within(ficha).getByRole('textbox', { name: 'Título' });
    await user.clear(titleInput);
    await user.type(titleInput, 'Sendero de la Memoria Renovado');

    const priceInput = within(ficha).getByRole('textbox', { name: 'Precio por persona (COP)' });
    await user.clear(priceInput);
    await user.type(priceInput, '95000');

    await user.click(within(ficha).getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText('Se guardaron los cambios de «Sendero de la Memoria Renovado».'),
    ).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/experiences/e-1',
      {
        method: 'PUT',
        body: {
          title: 'Sendero de la Memoria Renovado',
          description: experience.description,
          safetyGuidelines: experience.safetyGuidelines,
          physicalIntensity: experience.physicalIntensity,
          durationMinutes: experience.durationMinutes,
          municipalityCode: experience.municipalityCode,
          vereda: experience.vereda,
          meetingPoint: experience.meetingPoint,
          latitude: experience.latitude,
          longitude: experience.longitude,
          maxCapacityPerSlot: experience.maxCapacityPerSlot,
          unitPriceCop: 95000,
          categoryCodes: experience.categoryCodes,
          stops: experience.stops,
        },
      },
    ]);
  });

  it('explica el bloqueo por punto de encuentro y permite confirmarlo', async () => {
    stubMatchMedia(LAPTOP);
    const { http } = renderApp(
      '/admin/experiencias?ver=e-1',
      adminRoutes({
        '/v1/admin/experiences/e-1/approve': conflict('CATALOG_MEETING_POINT_MISMATCH'),
        '/v1/admin/experiences/e-1/meeting-point/confirm': experience,
      }),
      ADMIN,
    );
    const ficha = await screen.findByRole('dialog', { name: experience.title });

    await userEvent.click(within(ficha).getByRole('button', { name: 'Aprobar y publicar' }));
    const approve = screen.getByRole('dialog', { name: 'Aprobar y publicar' });
    expect(within(approve).getByText(`«${experience.title}»`)).toBeVisible();
    await userEvent.click(within(approve).getByRole('button', { name: 'Aprobar y publicar' }));
    expect(await within(approve).findByRole('alert')).toHaveTextContent(
      'El punto de encuentro no coincide con la dirección',
    );
    await userEvent.click(within(approve).getByRole('button', { name: 'Cancelar' }));

    const reopenedFicha = await screen.findByRole('dialog', { name: experience.title });
    await userEvent.click(
      within(reopenedFicha).getByRole('button', { name: 'Confirmar el punto de encuentro' }),
    );
    const confirm = screen.getByRole('dialog', { name: 'Confirmar el punto de encuentro' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Confirmar punto' }));

    expect(
      await screen.findByText('Punto de encuentro confirmado: ya puedes aprobar la experiencia.'),
    ).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/experiences/e-1/meeting-point/confirm',
      { method: 'POST' },
    ]);
  });

  it('exige el motivo para rechazar y lo envía al servidor', async () => {
    stubMatchMedia(LAPTOP);
    const { http } = renderApp(
      '/admin/experiencias?ver=e-1',
      adminRoutes({ '/v1/admin/experiences/e-1/reject': experience }),
      ADMIN,
    );
    const ficha = await screen.findByRole('dialog', { name: experience.title });
    await userEvent.click(within(ficha).getByRole('button', { name: 'Rechazar' }));
    const dialog = screen.getByRole('dialog', { name: 'Rechazar' });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Rechazar' }));
    const reason = within(dialog).getByRole('textbox', { name: 'Motivo' });
    expect(reason).toHaveAttribute('aria-invalid', 'true');
    expect(reason).toHaveFocus();
    expect(http.calls.some(([path]) => path.endsWith('/reject'))).toBe(false);

    await userEvent.type(reason, '  Falta el punto de encuentro exacto ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Rechazar' }));
    expect(
      await screen.findByText('Experiencia rechazada: el anfitrión verá el motivo.'),
    ).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/experiences/e-1/reject',
      { method: 'POST', body: { reason: 'Falta el punto de encuentro exacto' } },
    ]);
  });

  it('avisa cuando la experiencia ya cambió de estado (409)', async () => {
    stubMatchMedia(LAPTOP);
    renderApp(
      '/admin/experiencias?ver=e-1',
      adminRoutes({
        '/v1/admin/experiences/e-1/approve': conflict('CATALOG_INVALID_TRANSITION'),
      }),
      ADMIN,
    );
    const ficha = await screen.findByRole('dialog', { name: experience.title });
    await userEvent.click(within(ficha).getByRole('button', { name: 'Aprobar y publicar' }));
    const dialog = screen.getByRole('dialog', { name: 'Aprobar y publicar' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Aprobar y publicar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'La experiencia cambió de estado mientras la revisabas',
    );
  });

  it('filtra por estado desde la URL', async () => {
    stubMatchMedia(LAPTOP);
    const { http, router } = renderApp('/admin/experiencias', adminRoutes(), ADMIN);
    const table = await screen.findByRole('table', { name: /Por revisar/ });
    expect(table.className).toContain('bg-transparent');
    expect(table.querySelector('caption')?.className).toContain('sr-only');
    expect(screen.getByRole('searchbox', { name: 'Buscar en experiencias' })).toBeVisible();
    const filters = screen.getByRole('navigation', { name: 'Filtrar por estado' });
    await userEvent.click(within(filters).getByRole('link', { name: 'Suspendidas' }));
    expect(router.state.location.search).toBe('?estado=SUSPENDED');
    expect(await screen.findByText('No hay experiencias en este estado.')).toBeVisible();
    expect(http.calls.map(([path]) => path)).toContain(
      '/v1/admin/experiences?page=0&size=20&status=SUSPENDED',
    );
  });
});

describe('asociaciones', () => {
  it('lista las asociaciones y registra una nueva tras validar el formulario', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp('/admin/asociaciones', adminRoutes(), ADMIN);

    const table = await screen.findByRole('table', { name: /Asociaciones registradas/ });
    expect(
      await within(table).findByRole('rowheader', { name: association.legalName }),
    ).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Nueva asociación' }));
    const dialog = screen.getByRole('dialog', { name: 'Nueva asociación' });
    await user.click(within(dialog).getByRole('button', { name: 'Registrar asociación' }));
    const legalName = within(dialog).getByRole('textbox', { name: 'Razón social' });
    expect(legalName).toHaveAttribute('aria-invalid', 'true');
    expect(legalName).toHaveFocus();

    await user.type(legalName, 'Asociación QA');
    await user.type(within(dialog).getByRole('textbox', { name: 'NIT' }), '900123456-1');
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Registro Nacional de Turismo' }),
      '123456',
    );
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Municipio (código DANE)' }),
      '05001',
    );
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Correo de contacto' }),
      'qa@ejemplo.com',
    );
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Celular de contacto' }),
      '3001234567',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Registrar asociación' }));

    expect(await screen.findByText(`Se registró «${association.legalName}».`)).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/associations',
      {
        method: 'POST',
        body: {
          legalName: 'Asociación QA',
          nit: '900123456-1',
          rntNumber: '123456',
          associationType: 'ASOCIACION_VICTIMAS',
          municipalityCode: '05001',
          contactEmail: 'qa@ejemplo.com',
          contactPhone: '+573001234567',
        },
      },
    ]);
  });

  it('marca el NIT cuando la asociación ya existe (409)', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    renderApp(
      '/admin/asociaciones',
      adminRoutes({
        '/v1/admin/associations': (_url: URL, options: RequestOptions) =>
          options.method === 'POST' ? conflict('CATALOG_NIT_TAKEN') : page([association]),
      }),
      ADMIN,
    );
    await user.click(await screen.findByRole('button', { name: 'Nueva asociación' }));
    const dialog = screen.getByRole('dialog', { name: 'Nueva asociación' });
    await user.type(within(dialog).getByRole('textbox', { name: 'Razón social' }), 'Asociación QA');
    const nit = within(dialog).getByRole('textbox', { name: 'NIT' });
    await user.type(nit, '900123456-1');
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Registro Nacional de Turismo' }),
      '123456',
    );
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Municipio (código DANE)' }),
      '05001',
    );
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Correo de contacto' }),
      'qa@ejemplo.com',
    );
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Celular de contacto' }),
      '3001234567',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Registrar asociación' }));

    expect(
      await within(dialog).findByText('Ya existe una asociación registrada con este NIT.'),
    ).toBeVisible();
    await waitFor(() => {
      expect(nit).toHaveFocus();
    });
  });

  it('permite editar una asociación existente desde la ventana emergente de Gestionar', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { http } = renderApp(
      '/admin/asociaciones',
      adminRoutes({
        '/v1/admin/associations/a-1': (_url: URL, options: RequestOptions) =>
          options.method === 'PUT'
            ? { ...association, legalName: 'Asociación Campesina Renovada' }
            : association,
      }),
      ADMIN,
    );

    const table = await screen.findByRole('table', { name: /Asociaciones registradas/ });
    await user.click(
      await within(table).findByRole('button', { name: `Gestionar ${association.legalName}` }),
    );
    const dialog = screen.getByRole('dialog', { name: association.legalName });
    const legalNameInput = within(dialog).getByRole('textbox', { name: 'Razón social' });
    await user.clear(legalNameInput);
    await user.type(legalNameInput, 'Asociación Campesina Renovada');
    await user.click(within(dialog).getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText('Se actualizaron los datos de «Asociación Campesina Renovada».'),
    ).toBeVisible();
    expect(http.calls).toContainEqual([
      '/v1/admin/associations/a-1',
      {
        method: 'PUT',
        body: {
          legalName: 'Asociación Campesina Renovada',
          nit: association.nit,
          rntNumber: association.rntNumber,
          associationType: association.associationType,
          municipalityCode: association.municipalityCode,
          contactEmail: association.contactEmail,
          contactPhone: association.contactPhone,
        },
      },
    ]);
  });
});

describe('auditoría', () => {
  it('consulta con los filtros de la URL, con las fechas como días de Bogotá', async () => {
    stubMatchMedia(LAPTOP);
    const { http } = renderApp(
      '/admin/auditoria?modulo=booking&severidad=INFO&desde=2026-11-01&hasta=2026-11-02',
      adminRoutes(),
      ADMIN,
    );
    const table = await screen.findByRole('table', { name: /Registros/ });
    expect(
      await within(table).findByRole('rowheader', { name: 'BOOKING_CONFIRMED' }),
    ).toBeVisible();
    expect(http.calls.map(([path]) => path)).toContain(
      '/v1/admin/audit?module=booking&severity=INFO&from=2026-11-01T05%3A00%3A00.000Z&to=2026-11-03T05%3A00%3A00.000Z&page=0&size=20',
    );
    expect(screen.getByRole('note')).toHaveTextContent('queda registrada');
  });

  it('aplica los filtros del formulario y rechaza un módulo mal escrito', async () => {
    stubMatchMedia(LAPTOP);
    const user = userEvent.setup();
    const { router } = renderApp('/admin/auditoria', adminRoutes(), ADMIN);
    const form = await screen.findByRole('form', { name: 'Filtrar la bitácora' });
    const module = within(form).getByRole('textbox', { name: 'Módulo' });

    await user.type(module, 'Booking 2');
    await user.click(within(form).getByRole('button', { name: 'Aplicar filtros' }));
    expect(module).toHaveAttribute('aria-invalid', 'true');
    expect(router.state.location.search).toBe('');

    await user.clear(module);
    await user.type(module, 'booking');
    await user.type(within(form).getByRole('textbox', { name: 'Acción' }), 'AUDIT_QUERIED');
    await user.click(within(form).getByRole('button', { name: 'Aplicar filtros' }));
    expect(router.state.location.search).toBe('?modulo=booking&accion=AUDIT_QUERIED');
  });

  it('verifica la cadena a pedido y exporta el CSV con los filtros vigentes', async () => {
    stubMatchMedia(LAPTOP);
    const createObjectURL = vi.fn(() => 'blob:auditoria');
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      // jsdom no navega: basta con saber que se pidió la descarga.
    });
    const csv = new Blob(['id;accion'], { type: 'text/csv' });
    const { http } = renderApp(
      '/admin/auditoria?modulo=booking',
      adminRoutes({ '/v1/admin/audit/export': csv }),
      ADMIN,
    );

    await screen.findByRole('table', { name: /Registros/ });
    expect(http.calls.some(([path]) => path === '/v1/admin/audit/verify')).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Verificar la cadena' }));
    expect(await screen.findByText(/La cadena está íntegra: 41 registros revisados/)).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Exportar CSV' }));
    await waitFor(() => {
      expect(click).toHaveBeenCalledOnce();
    });
    expect(createObjectURL).toHaveBeenCalledWith(csv);
    expect(http.calls).toContainEqual([
      '/v1/admin/audit/export?module=booking',
      { responseType: 'blob' },
    ]);
  });
});

describe('operación administrativa (ADR-0045)', () => {
  it('requiere nota y confirmación antes de cerrar un incidente', async () => {
    const user = userEvent.setup();
    let incident = {
      id: 'i-1',
      status: 'OPEN',
      severity: 'HIGH',
      type: 'OVER_REDEMPTION_COMMUNITY_PROTECTED',
      financialImpactCop: 20000,
      createdAt: '2026-11-10T10:00:00Z',
      bookingId: 'r-1',
      voucherId: 'v-1',
      resolutionNotes: '',
    };
    const resolve = vi.fn((_url: URL, options: RequestOptions) => {
      incident = {
        ...incident,
        status: 'RESOLVED',
        resolutionNotes: (options.body as { resolutionNotes: string }).resolutionNotes,
      };
      return incident;
    });
    renderApp(
      '/admin/incidentes?severity=HIGH',
      adminRoutes({
        '/v1/admin/incidents': (url: URL) => {
          expect(url.searchParams.get('severity')).toBe('HIGH');
          return page([incident]);
        },
        '/v1/admin/incidents/i-1': () => incident,
        '/v1/admin/incidents/i-1/take': () => {
          incident = { ...incident, status: 'IN_REVIEW' };
          return incident;
        },
        '/v1/admin/incidents/i-1/resolve': resolve,
      }),
      ADMIN,
    );

    await user.click(await screen.findByRole('button', { name: 'Ver incidente i-1' }));
    const sheet = within(await screen.findByRole('dialog', { name: 'Detalle del incidente' }));
    await user.click(await sheet.findByRole('button', { name: 'Tomar caso' }));
    expect(await sheet.findByRole('button', { name: 'Resolver' })).toBeDisabled();
    await user.type(sheet.getByLabelText('Nota de cierre'), 'Evidencia revisada');
    await user.click(sheet.getByRole('button', { name: 'Resolver' }));
    expect(resolve).not.toHaveBeenCalled();
    await user.click(sheet.getByRole('button', { name: 'Confirmar resolución' }));
    expect(await sheet.findByText('Evidencia revisada')).toBeVisible();
    expect(resolve).toHaveBeenCalledTimes(1);
  });

  it('informa si la referencia del lote no se puede verificar y permite volver a conciliar', async () => {
    const user = userEvent.setup();
    const batch = {
      id: 'l-1',
      associationName: 'Asociación comunitaria',
      status: 'SUBMITTED',
      grossCop: 100000,
      gatewayFeeCop: 3000,
      platformFeeCop: 15000,
      netCommunityCop: 85000,
      submittedAt: '2026-11-10T10:00:00Z',
    };
    renderApp(
      '/admin/liquidaciones',
      adminRoutes({
        '/v1/admin/associations': (url: URL) => ({
          ...page(
            url.searchParams.get('page') === '1'
              ? [{ ...association, id: 'a-2', legalName: 'Segunda asociación' }]
              : [association],
          ),
          totalPages: 2,
          totalElements: 21,
        }),
        '/v1/admin/payout-batches': (url: URL) =>
          page(url.searchParams.get('associationId') === 'a-2' ? [batch] : []),
        '/v1/admin/payout-batches/l-1': { batch, disbursements: [] },
        '/v1/admin/payout-batches/l-1/reconcile': { batch, lookupStatus: 'UNAVAILABLE' },
      }),
      ADMIN,
    );

    const associationPages = await screen.findByRole('navigation', {
      name: 'Páginas de opciones de asociación',
    });
    await user.click(within(associationPages).getByRole('button', { name: 'Siguiente' }));
    await screen.findByRole('option', { name: 'Segunda asociación' });
    await user.selectOptions(screen.getByRole('combobox', { name: 'Asociación' }), 'a-2');
    await user.click(await screen.findByRole('button', { name: 'Ver lote l-1' }));
    const sheet = within(await screen.findByRole('dialog', { name: 'Detalle de liquidación' }));
    await user.click(await sheet.findByRole('button', { name: 'Conciliar lote' }));
    expect(await sheet.findByText(/No se pudo verificar/)).toBeVisible();
    expect(sheet.getByRole('button', { name: 'Conciliar lote' })).toBeEnabled();
  });
});

describe('accesibilidad de la consola', () => {
  it(
    'las secciones no tienen violaciones WCAG 2.1 A/AA detectables y llevan un único h1',
    { timeout: 60_000 },
    async () => {
      stubMatchMedia(LAPTOP);
      const findings: string[] = [];
      for (const path of [
        '/admin',
        '/admin/experiencias?ver=e-1',
        '/admin/asociaciones',
        '/admin/incidentes',
        '/admin/liquidaciones',
        '/admin/auditoria',
      ]) {
        const { unmount } = renderApp(path, adminRoutes(), ADMIN);
        await screen.findByRole('heading', { level: 1 });
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
