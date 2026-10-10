import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { renderApp } from '@/test/renderApp';

import { normalizeActivationCode } from './activation';

const PASSWORD = 'una-frase-larga-y-propia';

function renderActivate(
  response: () => unknown,
  path = '/activar?correo=cc1094857483%40comunidad.integrate.co',
) {
  const bodies: unknown[] = [];
  const view = renderApp(path, {
    '/v1/auth/activate': (_url: URL, options: RequestOptions) => {
      bodies.push(options.body);
      return response();
    },
  });
  return { ...view, bodies };
}

async function fill(code: string, confirmation = PASSWORD) {
  await userEvent.type(screen.getByLabelText('Código de activación'), code);
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), PASSWORD);
  await userEvent.type(screen.getByLabelText('Repite la contraseña'), confirmation);
  await userEvent.click(screen.getByRole('button', { name: 'Activar cuenta' }));
}

describe('activación del anfitrión (ADR-0018 §7)', () => {
  it('normaliza el código que dicta el gestor', () => {
    expect(normalizeActivationCode(' 7k3qd m9x2p ')).toBe('7K3QD-M9X2P');
    expect(normalizeActivationCode('7K3QD-M9X2P')).toBe('7K3QD-M9X2P');
  });

  it('activa con el correo precargado y lleva a ingresar', async () => {
    const { bodies } = renderActivate(() => null);
    expect(screen.getByLabelText('Correo')).toHaveValue('cc1094857483@comunidad.integrate.co');
    await fill('7k3qd m9x2p');
    expect(bodies).toEqual([
      {
        email: 'cc1094857483@comunidad.integrate.co',
        code: '7K3QD-M9X2P',
        newPassword: PASSWORD,
      },
    ]);
    expect(await screen.findByRole('heading', { level: 1, name: 'Cuenta activada' })).toHaveFocus();
    expect(screen.getByRole('link', { name: 'Ingresar' })).toHaveAttribute('href', '/ingresar');
  });

  it('valida la confirmación antes de enviar', async () => {
    const { bodies } = renderActivate(() => null);
    await fill('7K3QD-M9X2P', 'otra-frase-distinta');
    expect(screen.getByText(/Las contraseñas no coinciden/)).toBeVisible();
    expect(screen.getByLabelText('Repite la contraseña')).toHaveFocus();
    expect(bodies).toHaveLength(0);
  });

  it('un 401 da un mensaje único sin revelar la causa', async () => {
    renderActivate(
      () =>
        new ApiError(
          { type: 'about:blank', title: 'x', status: 401, code: 'AUTH_ACTIVATION_INVALID' },
          null,
        ),
    );
    await fill('7K3QD-M9X2P');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El código no es válido, venció o ya se usó. Pide uno nuevo a tu gestor.',
    );
  });
});
