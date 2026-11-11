import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { todayInBogota } from '@/shared/lib';

import { DatePickerField } from './DatePickerField';

describe('DatePickerField', () => {
  it('renderiza la etiqueta y el valor inicial', () => {
    render(<DatePickerField label="Fecha de la jornada" value="2026-11-11" onChange={vi.fn()} />);
    const input = screen.getByLabelText('Fecha de la jornada');
    expect(input).toBeVisible();
    expect(input).toHaveValue('2026-11-11');
    expect(screen.getByRole('button', { name: 'Abrir calendario' })).toBeVisible();
  });

  it('abre el calendario al hacer clic en el botón del calendario y muestra los días', async () => {
    const user = userEvent.setup();
    render(<DatePickerField label="Fecha de la jornada" value="2026-11-11" onChange={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Abrir calendario' }));
    expect(screen.getByRole('dialog', { name: /Calendario de/ })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Mes anterior' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Mes siguiente' })).toBeVisible();
  });

  it('permite elegir un día y emite la fecha seleccionada', async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(
      <DatePickerField label="Fecha de la jornada" value="2026-11-11" onChange={handleChange} />,
    );
    await user.click(screen.getByRole('button', { name: 'Abrir calendario' }));
    // Seleccionar el día 15 de noviembre de 2026
    const dayButton = screen.getByRole('button', { name: /15 de noviembre/ });
    await user.click(dayButton);
    expect(handleChange).toHaveBeenCalledWith('2026-11-15');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('navega entre meses con los botones anterior y siguiente', async () => {
    const user = userEvent.setup();
    render(<DatePickerField label="Fecha de la jornada" value="2026-11-11" onChange={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Abrir calendario' }));
    expect(screen.getByText(/Noviembre/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Mes siguiente' }));
    expect(screen.getByText(/Diciembre/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Mes anterior' }));
    expect(screen.getByText(/Noviembre/)).toBeVisible();
  });

  it('permite seleccionar "Hoy" y "Mañana" desde las acciones rápidas del popover', async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    const today = todayInBogota();
    render(<DatePickerField label="Fecha de la jornada" value="" onChange={handleChange} />);
    await user.click(screen.getByRole('button', { name: 'Abrir calendario' }));
    await user.click(screen.getByRole('button', { name: 'Hoy' }));
    expect(handleChange).toHaveBeenCalledWith(today);
  });

  it('cierra el calendario al presionar Escape', async () => {
    const user = userEvent.setup();
    render(<DatePickerField label="Fecha de la jornada" value="2026-11-11" onChange={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Abrir calendario' }));
    expect(screen.getByRole('dialog')).toBeVisible();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('permite quitar la fecha con el botón de borrar', async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(
      <DatePickerField label="Fecha de la jornada" value="2026-11-11" onChange={handleChange} />,
    );
    await user.click(screen.getByRole('button', { name: 'Quitar fecha' }));
    expect(handleChange).toHaveBeenCalledWith('');
  });
});
