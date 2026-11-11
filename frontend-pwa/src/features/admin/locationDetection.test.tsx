import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type * as GoogleMaps from '@/shared/maps/googleMaps';
import { LAPTOP, stubMatchMedia } from '@/test/matchMedia';
import { renderApp, type FakeRoutes } from '@/test/renderApp';

const reverseGeocode = vi.hoisted(() => vi.fn());

vi.mock('@/shared/maps/googleMaps', async (importOriginal) => {
  const original = await importOriginal<typeof GoogleMaps>();
  return { ...original, reverseGeocode };
});

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

const emptyPage = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 1,
  first: true,
  last: true,
};

const routes: FakeRoutes = {
  '/v1/admin/experiences': emptyPage,
  '/v1/admin/host-profiles': emptyPage,
  '/v1/catalog/departments': [
    { code: '05', name: 'ANTIOQUIA' },
    { code: '11', name: 'BOGOTÁ, D.C.' },
  ],
  '/v1/catalog/departments/05/municipalities': [
    { code: '05001', name: 'MEDELLÍN' },
    { code: '05088', name: 'BELLO' },
  ],
  '/v1/catalog/departments/11/municipalities': [{ code: '11001', name: 'BOGOTÁ, D.C.' }],
};

const MEDELLIN_LINK = 'https://www.google.com/maps/@6.2442,-75.5812,17z';

async function openFormAndPickPoint() {
  stubMatchMedia(LAPTOP);
  const user = userEvent.setup();
  renderApp('/admin/experiencias', routes, ADMIN);
  await user.click(await screen.findByRole('button', { name: /Nueva experiencia/i }));
  const dialog = await screen.findByRole('dialog', { name: /Nueva experiencia/i });
  fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: MEDELLIN_LINK } });
  await user.click(within(dialog).getByRole('button', { name: 'Usar este enlace' }));
  return { user, dialog };
}

describe('Fase 3 de ADR-0056: municipio detectado a partir del punto', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_GOOGLE_MAPS_BROWSER_KEY', '');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    reverseGeocode.mockReset();
  });

  it('muestra lo detectado, lo pone en el formulario y espera confirmación', async () => {
    reverseGeocode.mockResolvedValue({
      department: 'Antioquia',
      municipality: 'Medellín',
      vereda: 'Laureles',
    });
    const { user, dialog } = await openFormAndPickPoint();

    expect(
      await within(dialog).findByText('Detectamos: Medellín, Antioquia, vereda Laureles.'),
    ).toBeVisible();
    expect(reverseGeocode).toHaveBeenCalledWith({ lat: 6.2442, lng: -75.5812 });
    expect(within(dialog).getByRole('combobox', { name: 'Departamento' })).toHaveValue('Antioquia');
    expect(within(dialog).getByRole('combobox', { name: 'Municipio' })).toHaveValue('Medellín');
    expect(within(dialog).getByRole('textbox', { name: /Vereda/ })).toHaveValue('Laureles');

    await user.click(within(dialog).getByRole('button', { name: 'Es correcto' }));
    expect(
      within(dialog).getByText('Municipio confirmado: Medellín, Antioquia, vereda Laureles.'),
    ).toBeVisible();
    expect(within(dialog).queryByRole('button', { name: 'Es correcto' })).toBeNull();
  });

  it('avisa si la dirección escrita menciona otro municipio del departamento', async () => {
    reverseGeocode.mockResolvedValue({
      department: 'Antioquia',
      municipality: 'Medellín',
      vereda: undefined,
    });
    const { user, dialog } = await openFormAndPickPoint();
    await within(dialog).findByText(/Detectamos: Medellín, Antioquia/);

    await user.type(
      within(dialog).getByRole('textbox', { name: 'Dirección o punto de encuentro' }),
      'Parque principal de Bello',
    );
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'La dirección menciona Bello, pero el punto está en Medellín. Revisa antes de guardar.',
    );
  });

  it('no repite el nombre cuando el municipio y el departamento son el mismo (Bogotá)', async () => {
    reverseGeocode.mockResolvedValue({
      department: 'Bogotá',
      municipality: 'Bogotá',
      vereda: undefined,
    });
    const { dialog } = await openFormAndPickPoint();

    expect(await within(dialog).findByText('Detectamos: Bogotá, D.C.')).toBeVisible();
  });

  it('pide elegir el municipio a mano si Google no lo identifica', async () => {
    reverseGeocode.mockResolvedValue(null);
    const { dialog } = await openFormAndPickPoint();

    expect(
      await within(dialog).findByText(
        'No pudimos identificar el municipio con este punto. Elígelo en la lista.',
      ),
    ).toBeVisible();
    expect(within(dialog).getByRole('combobox', { name: 'Municipio' })).toBeDisabled();
  });

  it('no inventa un municipio cuando el nombre de Google no está en DIVIPOLA', async () => {
    reverseGeocode.mockResolvedValue({
      department: 'Antioquia',
      municipality: 'Lugar inexistente',
      vereda: undefined,
    });
    const { dialog } = await openFormAndPickPoint();

    expect(await within(dialog).findByText(/No pudimos identificar el municipio/)).toBeVisible();
    expect(within(dialog).getByRole('combobox', { name: 'Departamento' })).toHaveValue('');
  });
});
