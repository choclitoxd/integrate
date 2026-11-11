import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { catalogRoutes } from '@/test/fixtures';
import { renderApp, type FakeRoutes } from '@/test/renderApp';

/** Estado de la acreditación del gestor (ADR-0069) y del perfil del anfitrión (ADR-0070) en la navegación. */

const gestorRoutes = (status: string): FakeRoutes => ({
  ...catalogRoutes,
  '/v1/gestor/accreditation': { status, accreditedOn: '2026-10-01' },
});

const hostRoutes = (status: string): FakeRoutes => ({
  ...catalogRoutes,
  '/v1/host/status': { hostProfileId: 'h-1', status },
});

const GESTOR = { roles: ['ROLE_LINK_MANAGER'] };
const HOST = { roles: ['ROLE_HOST'] };

const modulesButton = () => screen.queryByRole('button', { name: 'Módulos' });

describe('estado de la acreditación del gestor (ADR-0069)', () => {
  it('con la acreditación vigente la barra ofrece «Módulos»', async () => {
    renderApp('/', gestorRoutes('ACTIVE'), GESTOR);
    expect(await screen.findByRole('button', { name: 'Módulos' })).toBeVisible();
  });

  it('suspendida: oculta las herramientas del gestor y conserva «Mi cuenta»', async () => {
    renderApp('/', gestorRoutes('SUSPENDED'), GESTOR);
    await waitFor(() => {
      expect(modulesButton()).toBeNull();
    });
    const nav = screen.getByRole('navigation');
    expect(within(nav).getByRole('link', { name: 'Explorar' })).toBeVisible();
    expect(within(nav).getByRole('link', { name: 'Mi cuenta' })).toBeVisible();
  });

  it('suspendida: en el panel del gestor se explica en lugar del rechazo genérico', async () => {
    renderApp('/gestor', gestorRoutes('SUSPENDED'), GESTOR);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Tu acreditación está suspendida' }),
    ).toBeVisible();
    expect(screen.getByText(/Comunícate con la administradora/)).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Panel de jornada' })).toBeNull();
  });

  it('revocada: el aviso lo dice con claridad', async () => {
    renderApp('/gestor', gestorRoutes('REVOKED'), GESTOR);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Tu acreditación fue revocada' }),
    ).toBeVisible();
  });

  it('suspendido sigue pudiendo abrir «Mi cuenta»', async () => {
    const { http } = renderApp('/gestor/cuenta', gestorRoutes('SUSPENDED'), GESTOR);
    await waitFor(() => {
      expect(http.calls.map(([path]) => path)).toContain('/v1/gestor/accreditation');
    });
    expect(screen.queryByRole('heading', { name: /Tu acreditación/ })).toBeNull();
  });

  it('si la consulta falla (sin conexión) se muestran las herramientas como siempre', async () => {
    const { http } = renderApp(
      '/',
      { ...catalogRoutes, '/v1/gestor/accreditation': new Error('sin red') },
      GESTOR,
    );
    await waitFor(() => {
      expect(http.calls.map(([path]) => path)).toContain('/v1/gestor/accreditation');
    });
    expect(await screen.findByRole('button', { name: 'Módulos' })).toBeVisible();
  });

  it('quien no es gestor no consulta ese estado', () => {
    const { http } = renderApp('/', catalogRoutes, { roles: ['ROLE_TOURIST'] });
    expect(http.calls.map(([path]) => path)).not.toContain('/v1/gestor/accreditation');
    expect(http.calls.map(([path]) => path)).not.toContain('/v1/host/status');
  });
});

describe('estado del perfil del anfitrión (ADR-0070)', () => {
  it('activo: la barra ofrece «Módulos»', async () => {
    renderApp('/', hostRoutes('ACTIVE'), HOST);
    expect(await screen.findByRole('button', { name: 'Módulos' })).toBeVisible();
  });

  it('suspendido: oculta el panel de anfitrión de la barra', async () => {
    renderApp('/', hostRoutes('SUSPENDED'), HOST);
    await waitFor(() => {
      expect(modulesButton()).toBeNull();
    });
  });

  it.each([
    ['SUSPENDED', 'Tu perfil de anfitrión está suspendido'],
    ['REVOKED', 'Tu perfil de anfitrión fue revocado'],
  ])(
    '%s: el panel explica el estado en lugar de las pantallas del anfitrión',
    async (status, title) => {
      renderApp('/anfitrion', hostRoutes(status), HOST);
      expect(await screen.findByRole('heading', { level: 1, name: title })).toBeVisible();
      expect(screen.getByRole('link', { name: 'Volver a explorar' })).toHaveAttribute('href', '/');
    },
  );

  it('con ambos roles, uno suspendido oculta solo sus propias herramientas', async () => {
    renderApp(
      '/',
      {
        ...gestorRoutes('ACTIVE'),
        '/v1/host/status': { hostProfileId: 'h-1', status: 'SUSPENDED' },
      },
      { roles: ['ROLE_HOST', 'ROLE_LINK_MANAGER'] },
    );
    await waitFor(async () => {
      await screen.findByRole('button', { name: 'Módulos' });
    });
    const open = await screen.findByRole('button', { name: 'Módulos' });
    open.click();
    const sheet = await screen.findByRole('dialog', { name: 'Módulos' });
    await waitFor(() => {
      expect(within(sheet).queryByRole('link', { name: 'Mis métricas' })).toBeNull();
    });
    expect(within(sheet).getByRole('link', { name: 'Jornada' })).toBeVisible();
  });
});
