import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { PhoneField } from './PhoneField';

function Harness({
  initial = '+57 ',
  error,
  onChange = vi.fn(),
}: {
  initial?: string;
  error?: string;
  onChange?: (value: string) => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <PhoneField
      label="Celular"
      name="phone"
      value={value}
      error={error}
      onChange={(next) => {
        setValue(next);
        onChange(next);
      }}
    />
  );
}

const number = () => screen.getByLabelText('Celular');
const country = () => screen.getByRole('combobox', { name: 'Indicativo del país' });

describe('PhoneField', () => {
  it('separa el indicativo (lista) del número y dice cuántos dígitos lleva el país', () => {
    render(<Harness />);
    expect(country()).toHaveTextContent('+57');
    expect(number()).toHaveValue('');
    expect(number()).toHaveAccessibleDescription(/Colombia: 10 dígitos/);
    expect(number()).toHaveAttribute('name', 'phone');
  });

  it('no admite más dígitos que el máximo del país ni caracteres que no sean dígitos', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await user.type(number(), '300-123 4567 999 abc');
    expect(number()).toHaveValue('3001234567');
    expect(onChange).toHaveBeenLastCalledWith('+57 3001234567');
  });

  it('al cambiar de país aplica su largo: recorta lo escrito y actualiza la ayuda', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness initial="+57 3001234567" onChange={onChange} />);

    await user.click(country());
    await user.click(screen.getByRole('option', { name: 'Ecuador (+593)' }));
    expect(country()).toHaveTextContent('+593');
    expect(number()).toHaveValue('300123456');
    expect(number()).toHaveAccessibleDescription(/Ecuador: 9 dígitos/);
    expect(onChange).toHaveBeenLastCalledWith('+593 300123456');

    // Un país con rango dice "10 u 11".
    await user.click(country());
    await user.click(screen.getByRole('option', { name: 'Brasil (+55)' }));
    expect(number()).toHaveAccessibleDescription(/Brasil: 10 u 11 dígitos/);
    await user.type(number(), '99999');
    expect(number()).toHaveValue('30012345699');
  });

  it('un número pegado con su indicativo cambia el país y se queda con sus dígitos', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(number());
    await user.paste('+58 412 123 4567');
    expect(country()).toHaveTextContent('+58');
    expect(number()).toHaveValue('4121234567');
  });

  it('muestra el error enlazado al campo del número', () => {
    render(<Harness error="Escribe un celular de 10 dígitos para Colombia (+57)." />);
    expect(number()).toBeInvalid();
    expect(number()).toHaveAccessibleDescription(/Escribe un celular de 10 dígitos/);
  });

  it('el selector se maneja con teclado y su menú es más ancho que el campo', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    country().focus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('listbox')).toBeVisible();
    expect(screen.getAllByRole('option').length).toBeGreaterThan(10);
    await user.keyboard('v{Enter}');
    expect(country()).toHaveTextContent('+58');
  });
});
