import { screen, waitFor } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { expect } from 'vitest';

/**
 * Elige una opción del `Select` desplegable (shared/ui): espera a que el control esté habilitado
 * (las listas DIVIPOLA cargan por red), abre el menú y toca la opción. Reemplaza a
 * `user.selectOptions`, que solo existe para el `<select>` nativo.
 */
export async function pickOption(
  user: UserEvent,
  combobox: HTMLElement,
  option: string | RegExp,
): Promise<void> {
  await waitFor(() => {
    expect(combobox).toBeEnabled();
  });
  await user.click(combobox);
  await user.click(await screen.findByRole('option', { name: option }));
}
