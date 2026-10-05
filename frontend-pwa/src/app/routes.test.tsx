import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';

import { routes } from './routes';

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe('routes', () => {
  it('muestra el portal del turista con landmarks accesibles', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { level: 1, name: /turismo comunitario/i })).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido');
    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('aria-current', 'page');
  });

  it('el primer elemento enfocable es el skip link hacia <main>', async () => {
    renderAt('/');
    await userEvent.tab();
    const skip = screen.getByRole('link', { name: 'Saltar al contenido principal' });
    expect(skip).toHaveFocus();
    expect(skip).toHaveAttribute('href', '#contenido');
  });

  it('navega al panel del gestor', async () => {
    const router = renderAt('/');
    await userEvent.click(screen.getByRole('link', { name: 'Soy Gestor de Enlace' }));
    expect(router.state.location.pathname).toBe('/gestor');
    expect(screen.getByRole('heading', { level: 1, name: 'Panel de jornada' })).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navegación del gestor' })).toBeInTheDocument();
  });

  it('responde 404 accesible para rutas desconocidas', () => {
    renderAt('/no-existe');
    expect(screen.getByRole('heading', { level: 1, name: 'Página no encontrada' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });
});
