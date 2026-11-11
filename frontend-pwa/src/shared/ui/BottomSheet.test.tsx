import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LAPTOP, PHONE, stubMatchMedia, TABLET } from '@/test/matchMedia';

import { BottomSheet } from './BottomSheet';

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderSheet(
  props: { onClose?: () => void; dismissible?: boolean; size?: 'md' | 'xl' } = {},
) {
  return render(
    <BottomSheet
      open
      onClose={props.onClose ?? vi.fn()}
      title="Actualizamos nuestra política de datos"
      dismissible={props.dismissible ?? true}
      size={props.size}
      footer={<button type="button">Acepto</button>}
    >
      <p>Contenido</p>
    </BottomSheet>,
  );
}

describe('BottomSheet según el dispositivo (ADR-0020 §2)', () => {
  it.each([
    ['un teléfono', PHONE],
    ['una tablet', TABLET],
  ] as const)('en %s es una hoja pegada al borde inferior', (_name, device) => {
    stubMatchMedia(device);
    renderSheet();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('data-placement', 'bottom');
    expect(dialog).toHaveClass('bottom-0', 'rounded-t-2xl', 'max-w-md');
    expect(dialog).not.toHaveClass('sheet-center');
  });

  it('en un computador es un diálogo centrado, más ancho y con las cuatro esquinas redondeadas', () => {
    stubMatchMedia(LAPTOP);
    renderSheet();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('data-placement', 'center');
    expect(dialog).toHaveClass('sheet-center', 'inset-0', 'm-auto', 'max-w-lg', 'rounded-2xl');
    expect(dialog).not.toHaveClass('bottom-0');
    expect(dialog).not.toHaveClass('rounded-t-2xl');
  });

  it('en un computador con size="xl" se expande a max-w-5xl (ADR-0059)', () => {
    stubMatchMedia(LAPTOP);
    renderSheet({ size: 'xl' });
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('data-size', 'xl');
    expect(dialog).toHaveClass('sheet-center', 'max-w-5xl');
    expect(dialog).not.toHaveClass('max-w-lg');
  });

  it('en un teléfono con size="xl" conserva la hoja inferior max-w-md sin alteración (ADR-0059)', () => {
    stubMatchMedia(PHONE);
    renderSheet({ size: 'xl' });
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('data-placement', 'bottom');
    expect(dialog).toHaveClass('max-w-md', 'bottom-0');
    expect(dialog).not.toHaveClass('max-w-5xl');
  });

  it('el contenido, el título y las acciones son los mismos en ambos', () => {
    stubMatchMedia(LAPTOP);
    renderSheet();
    expect(
      screen.getByRole('heading', { name: 'Actualizamos nuestra política de datos' }),
    ).toBeVisible();
    expect(screen.getByText('Contenido')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Acepto' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeVisible();
  });

  it('una decisión que no se puede posponer no ofrece cerrar, también en computador', () => {
    stubMatchMedia(LAPTOP);
    renderSheet({ dismissible: false });
    expect(screen.queryByRole('button', { name: 'Cerrar' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Acepto' })).toBeVisible();
  });

  it('en computador se cierra con el botón Cerrar', async () => {
    stubMatchMedia(LAPTOP);
    const onClose = vi.fn();
    renderSheet({ onClose });
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
