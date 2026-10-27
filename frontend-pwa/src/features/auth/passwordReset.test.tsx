import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { renderApp } from '@/test/renderApp';

import { resetTokenFromHash } from './passwordReset';

const PASSWORD = 'una-frase-larga-y-propia';

function problem(status: number, code: string) {
  return new ApiError({ type: 'about:blank', title: 'x', status, code }, null);
}

describe('recuperación de contraseña (T-1.13b, ADR-0014 §5)', () => {
  it('lee el token del fragmento', () => {
    expect(resetTokenFromHash('#token=abc123')).toBe('abc123');
    expect(resetTokenFromHash('#otro=1')).toBeNull();
    expect(resetTokenFromHash('')).toBeNull();
  });

  it('"Ingresar" enlaza a la recuperación', () => {
    renderApp('/ingresar');
    expect(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' })).toHaveAttribute(
      'href',
      '/recuperar-contrasena',
    );
  });

  it('pide el enlace y muestra siempre el mismo mensaje neutral', async () => {
    const bodies: unknown[] = [];
    renderApp('/recuperar-contrasena', {
      '/v1/auth/forgot-password': (_url: URL, options: RequestOptions) => {
        bodies.push(options.body);
        return null;
      },
    });
    await userEvent.type(screen.getByLabelText('Correo'), ' persona@ejemplo.co ');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }));
    expect(bodies).toEqual([{ email: 'persona@ejemplo.co' }]);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Revisa tu correo' }),
    ).toHaveFocus();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Si el correo está registrado, te enviamos un enlace válido por 15 minutos.',
    );
  });

  it('valida el correo antes de enviar', async () => {
    const bodies: unknown[] = [];
    renderApp('/recuperar-contrasena', {
      '/v1/auth/forgot-password': (_url: URL, options: RequestOptions) => bodies.push(options.body),
    });
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }));
    expect(screen.getByText('Escribe tu correo.')).toBeVisible();
    expect(screen.getByLabelText('Correo')).toHaveFocus();
    expect(bodies).toHaveLength(0);
  });

  it('restablece con el token del enlace, lo borra de la URL y lleva a ingresar con aviso', async () => {
    const bodies: unknown[] = [];
    const { router } = renderApp('/restablecer-contrasena#token=tok-del-correo', {
      '/v1/auth/reset-password': (_url: URL, options: RequestOptions) => {
        bodies.push(options.body);
        return null;
      },
    });
    expect(router.state.location.hash).toBe('');
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), PASSWORD);
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
    expect(bodies).toEqual([{ token: 'tok-del-correo', newPassword: PASSWORD }]);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Ingresa a tu cuenta' }),
    ).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Tu contraseña cambió. Ingresa con la nueva.',
    );
  });

  it('valida la confirmación antes de enviar', async () => {
    const bodies: unknown[] = [];
    renderApp('/restablecer-contrasena#token=t', {
      '/v1/auth/reset-password': (_url: URL, options: RequestOptions) => bodies.push(options.body),
    });
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), PASSWORD);
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'otra-frase-distinta');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
    expect(screen.getByText(/Las contraseñas no coinciden/)).toBeVisible();
    expect(bodies).toHaveLength(0);
  });

  it('un 401 dice que el enlace venció y ofrece pedir otro', async () => {
    renderApp('/restablecer-contrasena#token=viejo', {
      '/v1/auth/reset-password': () => problem(401, 'AUTH_RESET_TOKEN_INVALID'),
    });
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), PASSWORD);
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El enlace venció o ya se usó.');
    expect(screen.getByRole('link', { name: 'Pedir otro enlace' })).toHaveAttribute(
      'href',
      '/recuperar-contrasena',
    );
  });

  it('sin token en el enlace no muestra el formulario', () => {
    renderApp('/restablecer-contrasena');
    expect(screen.getByRole('alert')).toHaveTextContent('El enlace venció o ya se usó.');
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument();
  });

  it('un 422 de la política de contraseña se muestra sin perder el formulario', async () => {
    renderApp('/restablecer-contrasena#token=t', {
      '/v1/auth/reset-password': () => problem(422, 'VALIDATION_FAILED'),
    });
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), PASSWORD);
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
    expect(await screen.findByRole('button', { name: 'Guardar contraseña' })).toBeEnabled();
    expect(screen.getByLabelText('Nueva contraseña')).toBeInTheDocument();
  });
});
