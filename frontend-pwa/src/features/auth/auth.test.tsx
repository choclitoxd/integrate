import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ApiError, type RequestOptions } from '@/shared/api';
import { catalogRoutes } from '@/test/fixtures';
import { renderApp, sessionResponse } from '@/test/renderApp';

const policy = {
  version: 'v0.1',
  title: 'Política de Tratamiento de Datos',
  documentUrl: 'https://integrate.co/legal/politica-v0.1.pdf',
  effectiveFrom: '2026-10-01T05:00:00Z',
  contentSha256: 'a'.repeat(64),
};

function problem(status: number, extra: Record<string, unknown> = {}) {
  return new ApiError({ type: 'about:blank', title: 'x', status, ...extra }, null);
}

const consentStatus = (health: 'VALID' | 'MISSING') => ({
  currentPolicyVersion: 'v0.1',
  consents: [
    {
      purpose: 'PERSONAL_DATA',
      state: 'VALID',
      policyVersion: 'v0.1',
      occurredAt: '2026-10-09T10:00:00Z',
    },
    { purpose: 'HEALTH_EMERGENCY', state: health, policyVersion: null, occurredAt: null },
  ],
  requiresConsentUpdate: false,
  pendingPurposes: [],
});

describe('ingresar (T-1.11)', () => {
  async function submitLogin(email: string, password: string) {
    await userEvent.type(screen.getByLabelText('Correo'), email);
    await userEvent.type(screen.getByLabelText('Contraseña'), password);
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
  }

  it('valida en el cliente antes de llamar al servidor', async () => {
    const { http } = renderApp('/ingresar', catalogRoutes);
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
    expect(screen.getByLabelText('Correo')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Correo')).toHaveFocus();
    expect(screen.getByLabelText('Correo')).toHaveAccessibleDescription('Escribe tu correo.');
    expect(http.calls).toHaveLength(0);
  });

  it('inicia sesión y vuelve al destino pedido', async () => {
    const { router, session } = renderApp('/ingresar?volver=%2Fexperiencias%2F1', {
      ...catalogRoutes,
      '/v1/auth/login': sessionResponse(['ROLE_TOURIST']),
    });
    await submitLogin('turista@ejemplo.com', 'una clave');
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/experiencias/1');
    });
    expect(session.getState().status).toBe('authenticated');
  });

  it('no revela qué falló ante credenciales inválidas', async () => {
    renderApp('/ingresar', { '/v1/auth/login': problem(401) });
    await submitLogin('turista@ejemplo.com', 'mala');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El correo o la contraseña no coinciden',
    );
  });

  it('informa el bloqueo temporal con la hora de desbloqueo', async () => {
    renderApp('/ingresar', {
      '/v1/auth/login': problem(423, { lockedUntil: '2026-10-09T20:15:00Z' }),
    });
    await submitLogin('turista@ejemplo.com', 'mala');
    // 20:15 UTC = 3:15 p. m. en Bogotá.
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /A partir de las 3:15\sp\.\s?m\. podrás/,
    );
  });
});

describe('registro (T-1.11)', () => {
  async function fillRegister() {
    await userEvent.type(screen.getByLabelText('Nombre completo'), 'Carlos Restrepo');
    await userEvent.type(screen.getByLabelText('Número de documento'), '1094857483');
    const phone = screen.getByLabelText('Celular');
    await userEvent.clear(phone);
    await userEvent.type(phone, '300 123 4567');
    await userEvent.type(screen.getByLabelText('Correo'), 'nuevo@ejemplo.com');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'una frase larga');
  }

  it('enlaza la política vigente y exige la autorización de datos personales', async () => {
    const { http } = renderApp('/registro', { '/v1/privacy-policies/current': policy });
    expect(
      await screen.findByRole('link', {
        name: /leer la política de tratamiento de datos \(v0\.1\)/i,
      }),
    ).toHaveAttribute('href', policy.documentUrl);
    const personal = screen.getByRole('checkbox', {
      name: /mis datos personales \(obligatorio\)/i,
    });
    const health = screen.getByRole('checkbox', { name: /datos de salud.*\(opcional\)/i });
    expect(personal).not.toBeChecked();
    expect(health).not.toBeChecked();

    await fillRegister();
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(personal).toHaveFocus();
    expect(personal).toHaveAccessibleDescription(/debes autorizar el tratamiento/);
    expect(http.calls.some(([path]) => path === '/v1/auth/register')).toBe(false);
  });

  it('registra con el consentimiento elegido e inicia sesión automáticamente', async () => {
    let registered: RequestOptions['body'];
    const { router, session } = renderApp('/registro?volver=%2Fcuenta', {
      '/v1/privacy-policies/current': policy,
      '/v1/auth/register': (_url: URL, options: RequestOptions) => {
        registered = options.body;
        return { userId: 'u-9', email: 'nuevo@ejemplo.com', roles: ['ROLE_TOURIST'] };
      },
      '/v1/auth/login': sessionResponse(['ROLE_TOURIST']),
      '/v1/me/consents': consentStatus('VALID'),
    });
    await screen.findByRole('link', { name: /leer la política/i });
    await fillRegister();
    await userEvent.click(screen.getByRole('checkbox', { name: /mis datos personales/i }));
    await userEvent.click(screen.getByRole('checkbox', { name: /datos de salud/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/cuenta');
    });
    expect(registered).toEqual({
      email: 'nuevo@ejemplo.com',
      password: 'una frase larga',
      fullName: 'Carlos Restrepo',
      docType: 'CC',
      docNumber: '1094857483',
      phone: '+573001234567',
      consent: { policyVersion: 'v0.1', purposes: ['PERSONAL_DATA', 'HEALTH_EMERGENCY'] },
    });
    expect(session.getState().status).toBe('authenticated');
  });

  it('ofrece ingresar si la cuenta ya existe', async () => {
    renderApp('/registro', {
      '/v1/privacy-policies/current': policy,
      '/v1/auth/register': problem(409, { code: 'AUTH_USER_ALREADY_EXISTS' }),
    });
    await screen.findByRole('link', { name: /leer la política/i });
    await fillRegister();
    await userEvent.click(screen.getByRole('checkbox', { name: /mis datos personales/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Ya existe una cuenta con ese correo o documento.');
    expect(within(alert).getByRole('link', { name: 'Ingresar con esa cuenta' })).toHaveAttribute(
      'href',
      '/ingresar',
    );
  });

  it('asigna los errores 422 del servidor a sus campos', async () => {
    renderApp('/registro', {
      '/v1/privacy-policies/current': policy,
      '/v1/auth/register': problem(422, {
        code: 'VALIDATION_FAILED',
        invalidParams: [{ name: 'phone', reason: 'El celular no es válido.' }],
      }),
    });
    await screen.findByRole('link', { name: /leer la política/i });
    await fillRegister();
    await userEvent.click(screen.getByRole('checkbox', { name: /mis datos personales/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    const phone = screen.getByLabelText('Celular');
    await waitFor(() => {
      expect(phone).toHaveAccessibleDescription(/El celular no es válido\./);
    });
    expect(phone).toHaveFocus();
  });
});

describe('consentimiento y cuenta (T-1.11)', () => {
  it('bloquea con el re-consentimiento y no cierra la sesión al aceptar', async () => {
    let granted: RequestOptions['body'];
    const { session } = renderApp(
      '/',
      {
        ...catalogRoutes,
        '/v1/privacy-policies/current': policy,
        '/v1/me/consents': (_url: URL, options: RequestOptions) => {
          granted = options.body;
          return consentStatus('MISSING');
        },
      },
      { roles: ['ROLE_TOURIST'], pendingPurposes: ['PERSONAL_DATA'] },
    );
    const dialog = screen.getByRole('dialog', { name: 'Actualizamos nuestra política de datos' });
    expect(within(dialog).queryByRole('button', { name: 'Cerrar' })).toBeNull();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Acepto' }));
    expect(within(dialog).getByRole('checkbox')).toHaveAccessibleDescription(
      /Marca la casilla para continuar\./,
    );

    await userEvent.click(within(dialog).getByRole('checkbox'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Acepto' }));
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).toBeNull();
    });
    expect(granted).toEqual({ policyVersion: 'v0.2', purposes: ['PERSONAL_DATA'] });
    expect(session.getState().status).toBe('authenticated');
  });

  it('cierra la sesión si el titular no acepta', async () => {
    const { session } = renderApp(
      '/',
      { ...catalogRoutes, '/v1/auth/logout': undefined, '/v1/privacy-policies/current': policy },
      { roles: ['ROLE_TOURIST'], pendingPurposes: ['PERSONAL_DATA'] },
    );
    await userEvent.click(screen.getByRole('button', { name: 'No acepto' }));
    await waitFor(() => {
      expect(session.getState().status).toBe('anonymous');
    });
  });

  it('muestra las autorizaciones, permite otorgar la de salud y cerrar sesión', async () => {
    let health: 'VALID' | 'MISSING' = 'MISSING';
    const { router, session } = renderApp(
      '/cuenta',
      {
        ...catalogRoutes,
        '/v1/privacy-policies/current': policy,
        '/v1/auth/logout': undefined,
        '/v1/me/consents': (_url: URL, options: RequestOptions) => {
          if (options.method === 'POST') health = 'VALID';
          return consentStatus(health);
        },
      },
      { roles: ['ROLE_TOURIST'] },
    );
    expect(await screen.findByText('Sin autorizar')).toBeVisible();
    await userEvent.click(
      screen.getByRole('button', { name: 'Autorizar datos de salud para reservar' }),
    );
    const dialog = screen.getByRole('dialog', { name: 'Autorización necesaria' });
    await userEvent.click(within(dialog).getByRole('checkbox'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Acepto' }));
    await waitFor(() => {
      expect(screen.getAllByText('Autorizado')).toHaveLength(2);
    });

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
    expect(session.getState().status).toBe('anonymous');
  });
});
