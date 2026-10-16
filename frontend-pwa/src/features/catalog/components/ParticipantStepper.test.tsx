import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { ParticipantStepper } from './ParticipantStepper';

function StepperHarness({ initial = 1, max = 10 }: { initial?: number; max?: number }) {
  const [val, setVal] = useState(initial);
  return <ParticipantStepper value={val} max={max} onChange={setVal} />;
}

describe('ParticipantStepper', () => {
  it('permite cambiar el valor directamente tocando y escribiendo en el campo numérico', async () => {
    render(<StepperHarness initial={1} max={10} />);
    const input = screen.getByRole('spinbutton', { name: 'Número de personas' });
    expect(input).toHaveValue('1');

    await userEvent.click(input);
    await userEvent.clear(input);
    await userEvent.type(input, '5');
    expect(input).toHaveValue('5');
  });

  it('limita al tope máximo cuando se ingresa un valor superior', async () => {
    render(<StepperHarness initial={1} max={6} />);
    const input = screen.getByRole('spinbutton', { name: 'Número de personas' });

    await userEvent.click(input);
    await userEvent.clear(input);
    await userEvent.type(input, '9');
    expect(input).toHaveValue('6');
  });

  it('restablece a 1 si el campo queda vacío al perder el foco', async () => {
    render(<StepperHarness initial={3} max={10} />);
    const input = screen.getByRole('spinbutton', { name: 'Número de personas' });

    await userEvent.click(input);
    await userEvent.clear(input);
    await userEvent.tab();
    expect(input).toHaveValue('1');
  });

  it('permite incrementar y decrementar con las teclas de flecha y los botones', async () => {
    render(<StepperHarness initial={2} max={5} />);
    const input = screen.getByRole('spinbutton', { name: 'Número de personas' });
    const addButton = screen.getByRole('button', { name: 'Agregar una persona' });
    const subButton = screen.getByRole('button', { name: 'Quitar una persona' });

    await userEvent.click(addButton);
    expect(input).toHaveValue('3');

    await userEvent.click(subButton);
    expect(input).toHaveValue('2');

    input.focus();
    await userEvent.keyboard('{ArrowUp}');
    expect(input).toHaveValue('3');

    await userEvent.keyboard('{ArrowDown}');
    expect(input).toHaveValue('2');
  });
});
