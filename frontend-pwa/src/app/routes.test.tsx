import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { catalogRoutes } from '@/test/fixtures';
import { LAPTOP, stubMatchMedia } from '@/test/matchMedia';
import { renderApp } from '@/test/renderApp';

/** Abre la hoja «Módulos» de la barra (ADR-0067) y devuelve el diálogo. */
async function openModules() {
  await userEvent.click(screen.getByRole('button', { name: 'Módulos' }));
  return screen.findByRole('dialog', { name: 'Módulos' });
}

/** Cierra la hoja con su botón: jsdom no simula la tecla Esc de un <dialog> nativo. */
async function closeModules(modules: HTMLElement) {
  await userEvent.click(within(modules).getByRole('button', { name: 'Cerrar' }));
  await waitFor(() => {
    expect(screen.queryByRole('dialog')).toBeNull();
  });
}

const labelsOf = (scope: HTMLElement) =>
  within(scope)
    .getAllByRole('link')
    .map((link) => link.textContent.trim());

describe('routes', () => {
  it('abre en el catálogo del turista con landmarks y navegación inferior', async () => {
    renderApp('/', catalogRoutes);
    expect(
      screen.getByRole('heading', { level: 1, name: 'Experiencias comunitarias' }),
    ).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido');
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Explorar' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByText('1 experiencia')).toBeVisible();
  });

  it('el primer elemento enfocable es el skip link hacia <main>', async () => {
    renderApp('/', catalogRoutes);
    await userEvent.tab();
    const skip = screen.getByRole('link', { name: 'Saltar al contenido principal' });
    expect(skip).toHaveFocus();
    expect(skip).toHaveAttribute('href', '#contenido');
  });

  it('lleva a ingresar al visitante que abre una ruta protegida y conserva el destino', () => {
    const { router } = renderApp('/gestor', catalogRoutes);
    expect(router.state.location.pathname).toBe('/ingresar');
    expect(router.state.location.search).toBe('?volver=%2Fgestor');
    expect(screen.getByRole('heading', { level: 1, name: 'Ingresa a tu cuenta' })).toBeVisible();
  });

  it('conserva la navegación inferior en /ingresar con la pestaña activa', () => {
    renderApp('/ingresar', catalogRoutes);
    const nav = screen.getByRole('navigation', { name: 'Navegación principal' });
    expect(nav).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Ingresar' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('mantiene las opciones del gestor al volver a Explorar y deja «Mi cuenta» a la derecha', async () => {
    const { router } = renderApp('/', catalogRoutes, { roles: ['ROLE_LINK_MANAGER'] });
    // ADR-0067: los módulos del gestor viven en «Módulos»; la barra queda corta y «Mi cuenta» al final.
    const expected = ['Explorar', 'Mi cuenta'];
    expect(labelsOf(screen.getByRole('navigation'))).toEqual(expected);
    const modules = await openModules();
    expect(labelsOf(modules)).toEqual(['Jornada', 'Validar QR', 'Anfitriones']);
    await userEvent.click(within(modules).getByRole('link', { name: 'Jornada' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/gestor');
    });
    expect(labelsOf(screen.getByRole('navigation'))).toEqual(expected);
    await userEvent.click(screen.getByRole('link', { name: 'Explorar' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
    expect(labelsOf(screen.getByRole('navigation'))).toEqual(expected);
  });

  it('muestra el panel del gestor solo con ROLE_LINK_MANAGER', async () => {
    const { router } = renderApp('/', catalogRoutes, { roles: ['ROLE_LINK_MANAGER'] });
    const modules = await openModules();
    await userEvent.click(within(modules).getByRole('link', { name: 'Jornada' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/gestor');
    });
    expect(screen.getByRole('heading', { level: 1, name: 'Panel de jornada' })).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navegación del gestor' })).toBeInTheDocument();
  });

  it('mantiene las opciones del anfitrión al volver a Explorar y deja «Mi cuenta» a la derecha', async () => {
    const { router } = renderApp('/', catalogRoutes, { roles: ['ROLE_TOURIST', 'ROLE_HOST'] });
    const nav = () => screen.getByRole('navigation', { name: 'Navegación principal' });
    // ADR-0067: la barra del anfitrión cabe en 4 entradas; sus módulos van en la hoja «Módulos».
    const expected = ['Explorar', 'Mis experiencias', 'Mi cuenta'];
    expect(labelsOf(nav())).toEqual(expected);
    expect(within(nav()).getByRole('button', { name: 'Módulos' })).toBeVisible();
    const modules = await openModules();
    expect(labelsOf(modules)).toEqual(['Anfitrión', 'Mis métricas', 'Experiencias del anfitrión']);
    await userEvent.click(within(modules).getByRole('link', { name: 'Anfitrión' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Reservas confirmadas' }),
    ).toBeVisible();
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/anfitrion');
    });
    expect(labelsOf(nav())).toEqual(expected);
    await userEvent.click(within(nav()).getByRole('link', { name: 'Explorar' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Experiencias comunitarias' }),
    ).toBeVisible();
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
    expect(labelsOf(nav())).toEqual(expected);
  });

  it.each([
    [['ROLE_HOST'], 'Anfitrión', '/anfitrion/cuenta'],
    [['ROLE_LINK_MANAGER'], 'Gestor de enlace', '/gestor/cuenta'],
  ])('identifica el rol %s en el catálogo y en la cuenta', (roles, label, accountPath) => {
    const first = renderApp('/', catalogRoutes, { roles });
    expect(screen.getByLabelText(`Tus roles: ${label}`)).toBeVisible();
    first.unmount();
    renderApp(accountPath, catalogRoutes, { roles });
    expect(screen.getByLabelText(`Tus roles: ${label}`)).toBeVisible();
    const nav = screen.getByRole('navigation');
    expect(within(nav).getAllByRole('link', { current: 'page' })).toHaveLength(1);
    expect(within(nav).getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('el panel del gestor lleva a enrolar y a sus anfitriones (ADR-0018 §1)', async () => {
    const { router } = renderApp('/gestor', catalogRoutes, { roles: ['ROLE_LINK_MANAGER'] });
    const nav = screen.getByRole('navigation', { name: 'Navegación del gestor' });
    expect(within(nav).getByRole('button', { name: 'Módulos' })).toBeVisible();
    const modules = await openModules();
    expect(within(modules).getByRole('link', { name: 'Jornada' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(modules).getByRole('link', { name: 'Anfitriones' })).toHaveAttribute(
      'href',
      '/gestor/anfitriones',
    );
    await closeModules(modules);
    await userEvent.click(screen.getByRole('link', { name: /Enrolar anfitrión/ }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/gestor/anfitriones/enrolamiento');
    });
  });

  it('no ofrece la pestaña «Gestor» a visitantes ni a turistas', () => {
    const visitor = renderApp('/', catalogRoutes);
    expect(screen.queryByRole('link', { name: 'Gestor' })).toBeNull();
    visitor.unmount();
    renderApp('/', catalogRoutes, { roles: ['ROLE_TOURIST'] });
    expect(screen.queryByRole('link', { name: 'Gestor' })).toBeNull();
  });

  it('niega el panel del gestor a un turista', () => {
    renderApp('/gestor', catalogRoutes, { roles: ['ROLE_TOURIST'] });
    expect(
      screen.getByRole('heading', { name: 'Esta sección no está disponible para tu cuenta' }),
    ).toBeVisible();
  });

  it('todo anfitrión y gestor es también turista: ven Explorar y Mis experiencias (ADR-0064)', async () => {
    const host = renderApp('/anfitrion', catalogRoutes, { roles: ['ROLE_HOST', 'ROLE_TOURIST'] });
    expect(
      labelsOf(await screen.findByRole('navigation', { name: 'Navegación principal' })),
    ).toEqual(['Explorar', 'Mis experiencias', 'Mi cuenta']);
    host.unmount();

    renderApp('/gestor', catalogRoutes, { roles: ['ROLE_LINK_MANAGER', 'ROLE_TOURIST'] });
    expect(
      labelsOf(await screen.findByRole('navigation', { name: 'Navegación del gestor' })),
    ).toEqual(['Explorar', 'Mis experiencias', 'Mi cuenta']);
    expect(screen.getByRole('link', { name: 'Mis experiencias' })).toHaveAttribute(
      'href',
      '/mis-experiencias',
    );
  });

  it('«Módulos» agrupa las entradas del anfitrión en una hoja sin encabezados (ADR-0067)', async () => {
    renderApp('/', catalogRoutes, { roles: ['ROLE_HOST', 'ROLE_TOURIST'] });
    const modules = await openModules();
    expect(within(modules).queryByRole('heading', { level: 3 })).toBeNull();
    expect(within(modules).getByRole('link', { name: 'Mis métricas' })).toHaveAttribute(
      'href',
      '/anfitrion/metricas',
    );
    expect(
      within(modules).getByRole('link', { name: 'Experiencias del anfitrión' }),
    ).toHaveAttribute('href', '/anfitrion/experiencias');
  });

  it('con ambos roles de zona, «Módulos» separa las entradas por rol con un encabezado cada uno (ADR-0067)', async () => {
    renderApp('/', catalogRoutes, { roles: ['ROLE_HOST', 'ROLE_LINK_MANAGER', 'ROLE_TOURIST'] });
    const modules = await openModules();
    expect(within(modules).getByRole('heading', { level: 3, name: 'Anfitrión' })).toBeVisible();
    expect(
      within(modules).getByRole('heading', { level: 3, name: 'Gestor de enlace' }),
    ).toBeVisible();
    expect(labelsOf(modules)).toEqual([
      'Anfitrión',
      'Mis métricas',
      'Experiencias del anfitrión',
      'Jornada',
      'Validar QR',
      'Anfitriones',
    ]);
  });

  it('quien no tiene rol de zona no ve «Módulos» (ADR-0067)', () => {
    renderApp('/', catalogRoutes, { roles: ['ROLE_TOURIST'] });
    expect(screen.queryByRole('button', { name: 'Módulos' })).toBeNull();
  });

  it('un turista ve "Mis experiencias" en la barra; un visitante, no', () => {
    const visitor = renderApp('/', catalogRoutes);
    expect(screen.queryByRole('link', { name: 'Mis experiencias' })).toBeNull();
    visitor.unmount();

    renderApp('/', catalogRoutes, { roles: ['ROLE_TOURIST'] });
    const nav = screen.getByRole('navigation', { name: 'Navegación principal' });
    const links = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent);
    expect(links).toEqual(['Explorar', 'Mis experiencias', 'Mi cuenta']);
    expect(within(nav).getByRole('link', { name: 'Mis experiencias' })).toHaveAttribute(
      'href',
      '/mis-experiencias',
    );
  });
  it('cambia "Ingresar" por "Mi cuenta" con sesión', () => {
    renderApp('/', catalogRoutes, { roles: ['ROLE_TOURIST'] });
    expect(screen.getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute(
      'href',
      '/turista/cuenta',
    );
    expect(screen.queryByRole('link', { name: 'Ingresar' })).toBeNull();
  });

  it('«Mi cuenta» de cada rol vive en su zona y /cuenta lleva a ella (ADR-0062)', async () => {
    const cases = [
      [['ROLE_TOURIST'], '/turista/cuenta'],
      [['ROLE_HOST'], '/anfitrion/cuenta'],
      [['ROLE_LINK_MANAGER'], '/gestor/cuenta'],
      // El rol de zona manda sobre el de turista (ADR-0064): todos son turistas, pero siguen en su zona.
      [['ROLE_TOURIST', 'ROLE_HOST'], '/anfitrion/cuenta'],
      [['ROLE_TOURIST', 'ROLE_LINK_MANAGER'], '/gestor/cuenta'],
      [['ROLE_TOURIST', 'ROLE_HOST', 'ROLE_LINK_MANAGER'], '/anfitrion/cuenta'],
      [['ROLE_ADMIN', 'ROLE_TOURIST'], '/admin/cuenta'],
    ] as const;
    for (const [roles, zone] of cases) {
      const { router, unmount } = renderApp('/cuenta', catalogRoutes, { roles });
      await waitFor(() => {
        expect(router.state.location.pathname).toBe(zone);
      });
      unmount();
    }
  });

  it('un rol ajeno no entra a la cuenta de otra zona', () => {
    renderApp('/gestor/cuenta', {}, { roles: ['ROLE_TOURIST'] });
    expect(
      screen.getByRole('heading', { name: 'Esta sección no está disponible para tu cuenta' }),
    ).toBeVisible();
  });

  it('responde 404 accesible para rutas desconocidas', () => {
    renderApp('/no-existe');
    expect(screen.getByRole('heading', { level: 1, name: 'Página no encontrada' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });
});

describe('perfil del administrador en /admin/cuenta', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const adminRoles = ['ROLE_ADMIN', 'ROLE_TOURIST'];
  const consentsOk = {
    currentPolicyVersion: 'v0.1',
    consents: [],
    requiresConsentUpdate: false,
    pendingPurposes: [],
  };

  it('muestra "Mi cuenta" dentro del panel, sin Explorar ni Mis experiencias', async () => {
    stubMatchMedia(LAPTOP);
    renderApp('/admin/cuenta', { '/v1/me/consents': consentsOk }, { roles: adminRoles });
    expect(await screen.findByRole('heading', { level: 1, name: 'Mi cuenta' })).toBeVisible();
    const nav = screen.getByRole('navigation', { name: 'Navegación de administración' });
    expect(within(nav).getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute(
      'href',
      '/admin/cuenta',
    );
    expect(within(nav).queryByRole('link', { name: 'Explorar' })).toBeNull();
    expect(within(nav).queryByRole('link', { name: 'Mis experiencias' })).toBeNull();
  });

  it('un enlace a /cuenta de un administrador lo lleva a /admin/cuenta', async () => {
    const { router } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consentsOk },
      { roles: adminRoles },
    );
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/admin/cuenta');
    });
    expect(await screen.findByRole('heading', { level: 1, name: 'Mi cuenta' })).toBeVisible();
  });

  it('una ruta desconocida dentro del panel vuelve a su inicio, no a la página de no encontrada', async () => {
    const { router } = renderApp('/admin/no-existe', {}, { roles: adminRoles });
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/admin');
    });
    expect(screen.queryByRole('heading', { name: 'Página no encontrada' })).toBeNull();
  });

  it('el perfil de administrador no es accesible para un turista', async () => {
    stubMatchMedia(LAPTOP);
    renderApp('/admin/cuenta', {}, { roles: ['ROLE_TOURIST'] });
    expect(
      await screen.findByRole('heading', {
        name: 'Esta sección no está disponible para tu cuenta',
      }),
    ).toBeVisible();
  });
});
