import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError, NetworkError } from '@/shared/api';
import { addDays, formatLongDate, startOfWeek, todayInBogota } from '@/shared/lib';
import {
  availability,
  catalogRoutes,
  EXPERIENCE_ID,
  page,
  schedule,
  summary,
} from '@/test/fixtures';
import { renderApp } from '@/test/renderApp';
import { pickOption } from '@/test/select';

const plain = (text: string | null) => (text ?? '').replace(/\s/g, ' ');

describe('catálogo (T-1.10a)', () => {
  it('muestra tarjetas tipográficas con lugar, aforo, precio y categoría', async () => {
    renderApp('/', catalogRoutes);
    const link = await screen.findByRole('link', { name: summary.title });
    expect(link).toHaveAttribute('href', `/experiencias/${EXPERIENCE_ID}`);
    const card = link.closest('article');
    if (!card) throw new Error('Sin tarjeta');
    expect(within(card).getByText('San Carlos, Antioquia')).toBeVisible();
    expect(within(card).getByText('Máx. 12 personas por franja')).toBeVisible();
    expect(within(card).getByText('Intensidad media')).toBeVisible();
    expect(within(card).getByText('8,5 km')).toBeVisible();
    expect(within(card).getByText('Ida y vuelta 5 h')).toBeVisible();
    expect(within(card).getByText('2 paradas')).toBeVisible();
    expect(await within(card).findByText('Memoria histórica')).toBeVisible();
    expect(plain(card.textContent)).toContain('$ 80.000');
  });

  it('lleva la búsqueda por texto a la URL y a la consulta', async () => {
    const { router, http } = renderApp('/', catalogRoutes);
    await userEvent.type(
      screen.getByRole('searchbox', { name: /buscar experiencias/i }),
      'memoria',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(router.state.location.search).toBe('?q=memoria');
    await waitFor(() => {
      expect(http.calls.some(([path]) => path.includes('q=memoria'))).toBe(true);
    });
    expect(screen.getByRole('button', { name: 'Quitar filtro Búsqueda: “memoria”' })).toBeVisible();
  });

  it('aplica filtros DIVIPOLA desde el bottom-sheet y los quita con los chips', async () => {
    const { router } = renderApp('/', catalogRoutes);
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    const sheet = screen.getByRole('dialog', { name: 'Filtrar experiencias' });

    const user = userEvent.setup();
    const municipio = within(sheet).getByRole('combobox', { name: 'Municipio' });
    expect(municipio).toBeDisabled();
    await pickOption(
      user,
      within(sheet).getByRole('combobox', { name: 'Departamento' }),
      'Antioquia',
    );
    expect(within(sheet).getByRole('combobox', { name: 'Departamento' })).toHaveTextContent(
      'Antioquia',
    );
    await pickOption(user, municipio, 'San Carlos');
    await userEvent.click(within(sheet).getByRole('radio', { name: 'Alta' }));
    await userEvent.click(within(sheet).getByRole('button', { name: 'Ver resultados' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(new URLSearchParams(router.state.location.search).toString()).toBe(
      'departamento=05&municipio=05649&intensidad=HIGH',
    );
    expect(screen.getByRole('button', { name: 'Filtros, 3 activos' })).toBeVisible();

    // Quitar el departamento arrastra a su municipio.
    await userEvent.click(
      await screen.findByRole('button', { name: 'Quitar filtro Departamento: Antioquia' }),
    );
    expect(router.state.location.search).toBe('?intensidad=HIGH');
  });

  it('cierra el bottom-sheet sin aplicar el borrador', async () => {
    const { router } = renderApp('/', catalogRoutes);
    await userEvent.click(screen.getByRole('button', { name: 'Filtros' }));
    const sheet = screen.getByRole('dialog');
    await userEvent.click(within(sheet).getByRole('radio', { name: 'Baja' }));
    await userEvent.click(within(sheet).getByRole('button', { name: 'Cerrar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('muestra el error con código de seguimiento y permite reintentar', async () => {
    let attempts = 0;
    renderApp('/', {
      ...catalogRoutes,
      '/v1/experiences': () => {
        attempts += 1;
        return attempts === 1
          ? new ApiError({ type: 'about:blank', title: 'x', status: 503, traceId: 'abc123' }, null)
          : page([summary]);
      },
    });
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('El servicio no responde');
    expect(alert).toHaveTextContent('abc123');
    await userEvent.click(within(alert).getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('link', { name: summary.title })).toBeVisible();
  });

  it('distingue la falta de conexión', async () => {
    renderApp('/', { ...catalogRoutes, '/v1/experiences': new NetworkError(new TypeError()) });
    expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión');
  });

  it('ofrece quitar filtros cuando no hay resultados', async () => {
    const { router } = renderApp('/?intensidad=HIGH', {
      ...catalogRoutes,
      '/v1/experiences': page([]),
    });
    expect(
      await screen.findByText('No encontramos experiencias con estos criterios.'),
    ).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Quitar filtros' }));
    expect(router.state.location.search).toBe('');
  });
});

describe('detalle (T-1.10a)', () => {
  it('muestra la ficha, la seguridad y la acción fija hacia las fechas', async () => {
    renderApp(`/experiencias/${EXPERIENCE_ID}`, catalogRoutes);
    expect(await screen.findByRole('heading', { level: 1, name: summary.title })).toBeVisible();
    expect(screen.getByRole('heading', { name: '¿Qué llevar y recomendaciones?' })).toBeVisible();
    // Datos del recorrido (ADR-0022): distancia, ida y vuelta y paradas en orden.
    expect(screen.getByText('8,5 km · Circular')).toBeVisible();
    expect(screen.getByText('5 h')).toBeVisible();
    const stopsSection = screen
      .getByRole('heading', { name: 'Paradas del recorrido' })
      .closest('section');
    if (!stopsSection) throw new Error('Sin sección de paradas');
    expect(
      within(stopsSection)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      '1Mirador del ríoDescanso de 15 minutos con vista al cañón.',
      '2Casa de la memoria',
    ]);
    // Enlace web, no `geo:`: abre en computador y en celular (en el teléfono, la app si existe).
    const googleMaps = screen.getByRole('link', { name: /Abrir en Google Maps/ });
    expect(googleMaps).toHaveAttribute(
      'href',
      'https://www.google.com/maps/search/?api=1&query=6.187%2C-74.995',
    );
    expect(googleMaps).toHaveAttribute('target', '_blank');
    expect(document.querySelector('a[href^="geo:"]')).toBeNull();
    expect(screen.queryByRole('link', { name: /OpenStreetMap/ })).toBeNull();
    expect(
      screen.getByTitle(`Mapa del punto de encuentro de ${summary.title}`),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver fechas' })).toHaveAttribute(
      'href',
      `/experiencias/${EXPERIENCE_ID}/franjas`,
    );
    // Vista de flujo: "Atrás" en lugar de la navegación inferior.
    expect(screen.getByRole('button', { name: 'Atrás' })).toBeVisible();
    expect(screen.queryByRole('navigation', { name: 'Navegación principal' })).toBeNull();
  });

  it('responde con una vista propia si la experiencia no está disponible', async () => {
    renderApp(`/experiencias/${EXPERIENCE_ID}`, {
      [`/v1/experiences/${EXPERIENCE_ID}`]: new ApiError(
        { type: 'about:blank', title: 'No encontrado', status: 404 },
        null,
      ),
    });
    expect(
      await screen.findByRole('heading', { name: 'Esta experiencia no está disponible' }),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: 'Ver otras experiencias' })).toHaveAttribute(
      'href',
      '/',
    );
  });
});

describe('selector de franjas (T-1.10b)', () => {
  const today = todayInBogota();
  // Martes y miércoles de la semana siguiente: siempre en la misma semana y dentro de la ventana de
  // 60 días, sin importar el día en que corra la prueba (un sábado, "mañana" ya es otra semana).
  const nextMonday = startOfWeek(addDays(today, 7));
  const day1 = addDays(nextMonday, 1);
  const day2 = addDays(nextMonday, 2);
  const slotsPath = `/v1/experiences/${EXPERIENCE_ID}/availability`;

  function renderSlots(schedules: readonly unknown[]) {
    return renderApp(`/experiencias/${EXPERIENCE_ID}/franjas`, {
      [slotsPath]: (url: URL) =>
        availability(
          url.searchParams.get('dateFrom') ?? '',
          url.searchParams.get('dateTo') ?? '',
          schedules,
        ),
    });
  }

  it('consulta 60 días desde hoy en Bogotá', async () => {
    const { http } = renderSlots([schedule('b', day1, '14', 2)]);
    await screen.findByRole('heading', { level: 1, name: 'Elige fecha y franja' });
    await waitFor(() => {
      expect(http.calls[0]?.[0]).toBe(
        `${slotsPath}?dateFrom=${today}&dateTo=${addDays(today, 59)}`,
      );
    });
  });

  it('inhabilita las franjas agotadas con texto y calcula el total', async () => {
    renderSlots([
      schedule('a', day1, '07', 0),
      schedule('b', day1, '14', 2),
      schedule('c', day2, '07', 8),
    ]);
    const radios = await screen.findAllByRole('radio');
    expect(radios).toHaveLength(2);
    expect(radios[0]).toBeDisabled();
    expect(radios[0]?.closest('label')).toHaveTextContent('Agotada');
    expect(radios[1]?.closest('label')).toHaveTextContent('Últimos 2 cupos');

    const reservar = screen.getByRole('button', { name: 'Reservar' });
    expect(reservar).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Agregar una persona' }));
    if (!radios[1]) throw new Error('Sin franja');
    await userEvent.click(radios[1]);
    expect(radios[1]).toBeChecked();
    expect(plain(document.body.textContent)).toContain('$ 160.000');
    // Con una franja válida, "Reservar" lleva al formulario solo con identificadores (ADR-0015 §1).
    expect(screen.getByRole('link', { name: 'Reservar' })).toHaveAttribute(
      'href',
      `/reservas/nueva?experiencia=${EXPERIENCE_ID}&franja=b&personas=2`,
    );
  });

  it('limita el grupo a 10 personas, el tope del contrato de reserva', async () => {
    renderSlots([schedule('c', day2, '07', 12)]);
    await screen.findAllByRole('radio');
    // La experiencia de prueba admite 12 por franja; la reserva admite como máximo 10.
    expect(screen.getByText('Hasta 10')).toBeVisible();
    expect(
      screen.getByText('Para grupos de más de 10 personas, haz varias reservas.'),
    ).toBeVisible();
    const add = screen.getByRole('button', { name: 'Agregar una persona' });
    for (let i = 0; i < 9; i++) await userEvent.click(add);
    expect(add).toBeDisabled();
  });

  it('avisa e invalida la selección cuando ya no alcanza para el grupo', async () => {
    renderSlots([schedule('b', day1, '14', 2), schedule('c', day2, '07', 8)]);
    const [radio] = await screen.findAllByRole('radio');
    if (!radio) throw new Error('Sin franja');
    await userEvent.click(radio);
    await userEvent.click(screen.getByRole('button', { name: 'Agregar una persona' }));
    await userEvent.click(screen.getByRole('button', { name: 'Agregar una persona' }));
    const notice = screen.getByText(/ya no tiene cupo para 3 personas/);
    expect(notice.closest('[role="status"]')).not.toBeNull();
    expect(radio).toBeDisabled();
    expect(radio).not.toBeChecked();
  });

  it('marca los días sin cupo y permite elegir otro día', async () => {
    renderSlots([schedule('a', day1, '07', 0), schedule('c', day2, '07', 8)]);
    const fullDay = await screen.findByRole('button', { name: /: agotado$/ });
    expect(fullDay).toBeEnabled();
    const openDay = screen.getByRole('button', { name: /1 franja disponible$/ });
    expect(openDay).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(fullDay);
    expect(fullDay).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('radio')).toBeDisabled();
  });

  const dayButton = (date: string) =>
    screen.getByRole('button', { name: (name) => name.startsWith(`${formatLongDate(date)}:`) });

  it('al tocar el mes despliega el calendario y al elegir un día vuelve a su semana', async () => {
    const later = addDays(nextMonday, 22); // otra semana, dentro de la ventana de 60 días
    renderSlots([schedule('b', day1, '14', 4), schedule('z', later, '09', 4)]);
    const toggle = await screen.findByRole('button', { name: /ver el mes completo/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.queryByRole('list', { name: 'Días de la semana' })).toBeNull();
    expect(screen.getByRole('list', { name: /^Días de .+ de \d{4}$/ })).toBeVisible();
    if (!screen.queryByRole('button', { name: (n) => n.startsWith(`${formatLongDate(later)}:`) })) {
      await userEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
    }
    await userEvent.click(dayButton(later));

    // Vuelve a la semana del día elegido, con sus franjas debajo y el foco en el control del mes.
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(toggle).toHaveFocus();
    expect(screen.getByRole('list', { name: 'Días de la semana' })).toBeVisible();
    expect(dayButton(later)).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('radio')).toBeEnabled();
  });

  it('filtra por período y fin de semana con conteos, y avisa cuando nada coincide', async () => {
    const saturday = addDays(nextMonday, 5);
    renderSlots([
      schedule('m', day1, '07', 4),
      schedule('t', day2, '14', 4),
      schedule('s', saturday, '15', 4),
    ]);
    const morning = await screen.findByRole('button', { name: /^Mañana/ });
    const afternoon = screen.getByRole('button', { name: /^Tarde/ });
    const weekend = screen.getByRole('button', { name: /^Fin de semana/ });
    expect(morning).toHaveTextContent('· 1');
    expect(afternoon).toHaveTextContent('· 2');
    expect(weekend).toHaveTextContent('· 1');
    // Sin franjas de noche, ese filtro no se ofrece.
    expect(screen.queryByRole('button', { name: /^Noche/ })).toBeNull();

    await userEvent.click(afternoon);
    expect(afternoon).toHaveAttribute('aria-pressed', 'true');
    expect(dayButton(day2)).toHaveAttribute('aria-pressed', 'true');
    expect(dayButton(day1)).toBeDisabled();
    expect(screen.getAllByRole('radio')).toHaveLength(1);

    await userEvent.click(weekend);
    expect(dayButton(saturday)).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(afternoon);
    await userEvent.click(morning);
    expect(screen.getByText('Ninguna franja coincide con estos filtros.')).toBeVisible();
    expect(screen.queryByRole('radio')).toBeNull();

    const [clear] = screen.getAllByRole('button', { name: 'Quitar filtros' });
    if (!clear) throw new Error('Sin botón para quitar filtros');
    await userEvent.click(clear);
    expect(screen.queryByText('Ninguna franja coincide con estos filtros.')).toBeNull();
    for (const chip of screen.getAllByRole('button', { name: /^(Mañana|Tarde|Fin de semana)/ })) {
      expect(chip).toHaveAttribute('aria-pressed', 'false');
    }
  });

  it('no ofrece filtros que no cambiarían nada', async () => {
    renderSlots([schedule('a', day1, '07', 4), schedule('c', day2, '08', 4)]);
    await screen.findAllByRole('radio');
    expect(screen.queryByRole('button', { name: /^Mañana/ })).toBeNull();
    expect(screen.queryByText('Encuentra tu franja más rápido')).toBeNull();
  });

  it('lleva a la primera fecha con cupo cuando el turista se movió a otro día', async () => {
    renderSlots([schedule('a', day1, '07', 0), schedule('c', day2, '07', 8)]);
    await screen.findAllByRole('radio');
    expect(screen.queryByRole('button', { name: 'Primera fecha con cupo' })).toBeNull();
    await userEvent.click(dayButton(day1));
    await userEvent.click(screen.getByRole('button', { name: 'Primera fecha con cupo' }));
    expect(dayButton(day2)).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: 'Primera fecha con cupo' })).toBeNull();
  });

  it('informa cuando no hay franjas en la ventana', async () => {
    renderSlots([]);
    expect(
      await screen.findByText('Por ahora no hay franjas abiertas en los próximos 60 días.'),
    ).toBeVisible();
  });
});
