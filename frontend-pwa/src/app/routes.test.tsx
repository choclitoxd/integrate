import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { catalogRoutes } from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';

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

  it('muestra el panel del gestor solo con ROLE_LINK_MANAGER', async () => {
    const { router } = renderApp('/', catalogRoutes, { roles: ['ROLE_LINK_MANAGER'] });
    await userEvent.click(screen.getByRole('link', { name: 'Gestor' }));
    expect(router.state.location.pathname).toBe('/gestor');
    expect(screen.getByRole('heading', { level: 1, name: 'Panel de jornada' })).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navegación del gestor' })).toBeInTheDocument();
  });

  it('el panel del gestor lleva a enrolar y a sus anfitriones (ADR-0018 §1)', async () => {
    const { router } = renderApp('/gestor', catalogRoutes, { roles: ['ROLE_LINK_MANAGER'] });
    const nav = screen.getByRole('navigation', { name: 'Navegación del gestor' });
    expect(within(nav).getByRole('link', { name: 'Jornada' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('link', { name: 'Anfitriones' })).toHaveAttribute(
      'href',
      '/gestor/anfitriones',
    );
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

  it('un turista ve "Mis experiencias" en la barra; un visitante o un gestor sin ese rol, no', () => {
    const visitor = renderApp('/', catalogRoutes);
    expect(screen.queryByRole('link', { name: 'Mis experiencias' })).toBeNull();
    visitor.unmount();
    const manager = renderApp('/', catalogRoutes, { roles: ['ROLE_LINK_MANAGER'] });
    expect(screen.queryByRole('link', { name: 'Mis experiencias' })).toBeNull();
    manager.unmount();

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
    expect(screen.getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute('href', '/cuenta');
    expect(screen.queryByRole('link', { name: 'Ingresar' })).toBeNull();
  });

  it('responde 404 accesible para rutas desconocidas', () => {
    renderApp('/no-existe');
    expect(screen.getByRole('heading', { level: 1, name: 'Página no encontrada' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });
});
