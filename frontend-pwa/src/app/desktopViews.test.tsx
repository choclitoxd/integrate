import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { addDays, startOfWeek, todayInBogota } from '@/shared/lib';
import { availability, catalogRoutes, EXPERIENCE_ID, schedule, summary } from '@/test/fixtures';
import { LAPTOP, NARROW_WINDOW, PHONE, stubMatchMedia, TABLET } from '@/test/matchMedia';
import { renderApp } from '@/test/renderApp';
import { pickOption } from '@/test/select';

const plain = (text: string | null) => (text ?? '').replace(/\s/g, ' ');

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('vistas por factor de forma (ADR-0020)', () => {
  it('en un computador muestra el shell de escritorio y los filtros en la barra lateral', async () => {
    stubMatchMedia(LAPTOP);
    const { router } = renderApp('/', catalogRoutes);

    const aside = await screen.findByRole('complementary', { name: 'Filtrar experiencias' });
    expect(await screen.findByRole('link', { name: summary.title })).toBeVisible();
    // Sin hoja inferior ni botón "Filtros": los filtros siempre están a la vista.
    expect(screen.queryByRole('button', { name: 'Filtros' })).not.toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeVisible();

    await pickOption(
      userEvent.setup(),
      within(aside).getByRole('combobox', { name: 'Departamento' }),
      'Antioquia',
    );
    await userEvent.click(within(aside).getByRole('radio', { name: 'Alta' }));
    await userEvent.click(within(aside).getByRole('button', { name: 'Aplicar filtros' }));
    expect(new URLSearchParams(router.state.location.search).toString()).toBe(
      'departamento=05&intensidad=HIGH',
    );

    await userEvent.click(within(aside).getByRole('button', { name: 'Limpiar' }));
    expect(router.state.location.search).toBe('');
  });

  it.each([
    ['un teléfono', PHONE],
    ['una tablet', TABLET],
  ] as const)('en %s conserva la vista móvil con la hoja de filtros', async (_name, device) => {
    stubMatchMedia(device);
    renderApp('/', catalogRoutes);
    expect(await screen.findByRole('button', { name: 'Filtros' })).toBeVisible();
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  });

  it('el detalle en escritorio lleva la reserva en un panel lateral', async () => {
    stubMatchMedia(LAPTOP);
    renderApp(`/experiencias/${EXPERIENCE_ID}`, catalogRoutes);
    const aside = await screen.findByRole('complementary', { name: 'Reserva' });
    expect(within(aside).getByRole('link', { name: 'Ver fechas disponibles' })).toHaveAttribute(
      'href',
      `/experiencias/${EXPERIENCE_ID}/franjas`,
    );
    expect(screen.getByRole('heading', { level: 1, name: summary.title })).toBeVisible();
  });

  it('el selector de franjas en escritorio resume la selección y lleva a la reserva', async () => {
    stubMatchMedia(LAPTOP);
    const day = addDays(startOfWeek(addDays(todayInBogota(), 7)), 1);
    renderApp(`/experiencias/${EXPERIENCE_ID}/franjas`, {
      [`/v1/experiences/${EXPERIENCE_ID}/availability`]: (url: URL) =>
        availability(url.searchParams.get('dateFrom') ?? '', url.searchParams.get('dateTo') ?? '', [
          schedule('b', day, '14', 4),
        ]),
    });

    const aside = await screen.findByRole('complementary', { name: 'Tu selección' });
    expect(within(aside).getByRole('button', { name: 'Reservar' })).toBeDisabled();
    await userEvent.click(await screen.findByRole('radio'));
    expect(plain(aside.textContent)).toContain('$ 80.000');
    expect(within(aside).getByRole('link', { name: 'Reservar' })).toHaveAttribute(
      'href',
      `/reservas/nueva?experiencia=${EXPERIENCE_ID}&franja=b&personas=1`,
    );
  });

  it('al angostar la ventana pasa a la vista móvil sin recargar', async () => {
    const media = stubMatchMedia(LAPTOP);
    renderApp('/', catalogRoutes);
    await screen.findByRole('complementary', { name: 'Filtrar experiencias' });
    media.setDevice(NARROW_WINDOW);
    expect(await screen.findByRole('button', { name: 'Filtros' })).toBeVisible();
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  });
});
