import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { Select, type SelectOption } from './Select';

const OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'Todos' },
  { value: 'a', label: 'Antioquia' },
  { value: 'b', label: 'Boyacá' },
  { value: 'c', label: 'Caldas' },
  { value: 'ca', label: 'Caquetá' },
];

function Harness({
  onChange = vi.fn(),
  disabled = false,
}: {
  onChange?: (v: string) => void;
  disabled?: boolean;
}) {
  const [value, setValue] = useState('');
  return (
    <>
      <Select
        label="Departamento"
        name="dept"
        value={value}
        options={OPTIONS}
        disabled={disabled}
        onChange={(next) => {
          setValue(next);
          onChange(next);
        }}
      />
      <button type="button">Fuera</button>
    </>
  );
}

const combobox = () => screen.getByRole('combobox', { name: 'Departamento' });

describe('Select', () => {
  it('muestra la opción elegida y abre el menú con listbox, opciones y aria-selected', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    expect(combobox()).toHaveTextContent('Todos');
    expect(combobox()).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await user.click(combobox());
    expect(combobox()).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('listbox', { name: 'Departamento' })).toBeVisible();
    expect(screen.getAllByRole('option')).toHaveLength(5);
    expect(screen.getByRole('option', { name: 'Todos' })).toHaveAttribute('aria-selected', 'true');
  });

  it('elige con un toque, cierra el menú y devuelve el foco al botón', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await user.click(combobox());
    await user.click(screen.getByRole('option', { name: 'Boyacá' }));
    expect(onChange).toHaveBeenCalledWith('b');
    expect(combobox()).toHaveTextContent('Boyacá');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(combobox()).toHaveFocus();
  });

  it('se maneja con teclado: flechas, Inicio, Fin, Enter y Espacio', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    combobox().focus();

    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('listbox')).toBeVisible();
    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(combobox()).toHaveAttribute(
      'aria-activedescendant',
      screen.getByRole('option', { name: 'Boyacá' }).id,
    );
    await user.keyboard('{Enter}');
    expect(onChange).toHaveBeenLastCalledWith('b');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await user.keyboard('{ArrowDown}{End}{ }');
    expect(onChange).toHaveBeenLastCalledWith('ca');
    await user.keyboard('{ArrowUp}{Home}{Enter}');
    expect(onChange).toHaveBeenLastCalledWith('');
  });

  it('se abre con Enter, Espacio y con la activación de un lector de pantalla (clic sin ratón)', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    combobox().focus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('listbox')).toBeVisible();
    await user.keyboard('{Escape}');
    await user.keyboard(' ');
    expect(screen.getByRole('listbox')).toBeVisible();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    // Un clic con detail 0, como el que envían VoiceOver, TalkBack o el control por voz.
    fireEvent.click(combobox());
    expect(screen.getByRole('listbox')).toBeVisible();
    fireEvent.click(combobox());
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('Escape y Tab cierran sin cambiar el valor', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    combobox().focus();
    await user.keyboard('{ArrowDown}{ArrowDown}{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    await user.keyboard('{ArrowDown}{ArrowDown}{Tab}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('Escape no se propaga: no cierra la hoja que contiene al control', async () => {
    const user = userEvent.setup();
    const onOuterKey = vi.fn();
    render(
      // eslint-disable-next-line jsx-a11y-x/no-static-element-interactions
      <div onKeyDown={onOuterKey}>
        <Harness />
      </div>,
    );
    combobox().focus();
    await user.keyboard('{ArrowDown}');
    onOuterKey.mockClear();
    await user.keyboard('{Escape}');
    expect(onOuterKey).not.toHaveBeenCalled();
  });

  it('busca escribiendo la inicial y repite la letra para recorrer coincidencias', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    combobox().focus();
    await user.keyboard('c');
    expect(screen.getByRole('listbox')).toBeVisible();
    await user.keyboard('{Enter}');
    expect(onChange).toHaveBeenLastCalledWith('c');
  });

  it('cierra al tocar fuera del control', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(combobox());
    expect(screen.getByRole('listbox')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Fuera' }));
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('inhabilitado no se abre', async () => {
    const user = userEvent.setup();
    render(<Harness disabled />);
    expect(combobox()).toBeDisabled();
    await user.click(combobox());
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });
});
