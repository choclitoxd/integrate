import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { clearAllOfflineStores, getOfflineDB } from '@/shared/offline';
import { renderApp } from '@/test/renderApp';
import { ApiError, type RequestOptions } from '@/shared/api';

const consents = {
  consents: [],
  currentPolicyVersion: null,
  requiresConsentUpdate: false,
  pendingPurposes: [],
};
const profile = { fullName: 'Carlos Restrepo', phone: '+573001234567' };
const reauth = () => ({
  reauthToken: 'token-de-prueba',
  expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
});

/** Abre la hoja de confirmación desde `trigger` y escribe la contraseña. */
async function confirmIdentity(trigger: string, password = 'mi contraseña actual') {
  await userEvent.click(await screen.findByRole('button', { name: trigger }));
  await userEvent.type(await screen.findByLabelText('Contraseña'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
}

describe('cuenta propia', () => {
  afterEach(async () => {
    await clearAllOfflineStores();
  });
  it.each(['ROLE_TOURIST', 'ROLE_HOST', 'ROLE_LINK_MANAGER', 'ROLE_ADMIN'])(
    'consulta, confirma la identidad y actualiza sus datos para %s',
    async (role) => {
      const { http, session } = renderApp(
        '/cuenta',
        {
          '/v1/me/consents': consents,
          '/v1/auth/me/reauth': reauth,
          '/v1/auth/me': (_url: URL, options: RequestOptions) =>
            options.method === 'PATCH' ? options.body : profile,
        },
        { roles: [role] },
      );
      await confirmIdentity('Editar mis datos');
      const name = await screen.findByLabelText('Nombre completo');
      await userEvent.clear(name);
      await userEvent.type(name, 'Nombre Actualizado');
      await userEvent.click(screen.getByRole('button', { name: 'Guardar datos' }));
      await screen.findByText('Tus datos se guardaron.');
      const patch = http.calls.find(
        ([path, options]) => path === '/v1/auth/me' && options?.method === 'PATCH',
      );
      expect(patch?.[1]?.headers).toEqual({ 'X-Reauth-Token': 'token-de-prueba' });
      expect(session.getState()).toMatchObject({ user: { fullName: 'Nombre Actualizado' } });
    },
  );
  it('muestra los datos en solo lectura hasta confirmar la contraseña', async () => {
    const { http } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile },
      { roles: ['ROLE_TOURIST'] },
    );
    expect(await screen.findByText('Carlos Restrepo')).toBeVisible();
    expect(await screen.findByText('+57 300 123 4567')).toBeVisible();
    expect(screen.queryByLabelText('Nombre completo')).toBeNull();
    expect(screen.queryByLabelText('Contraseña actual')).toBeNull();
    expect(http.calls.some(([path]) => path === '/v1/auth/me/reauth')).toBe(false);
  });
  it('no habilita la edición si la contraseña es incorrecta', async () => {
    const wrong = new ApiError(
      {
        type: 'about:blank',
        title: 'Datos inválidos',
        status: 422,
        invalidParams: [{ name: 'password', reason: 'incorrect password' }],
      },
      null,
    );
    renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile, '/v1/auth/me/reauth': wrong },
      { roles: ['ROLE_TOURIST'] },
    );
    await confirmIdentity('Editar mis datos', 'contraseña equivocada');
    expect(await screen.findByText('La contraseña no es correcta.')).toBeVisible();
    expect(screen.queryByLabelText('Nombre completo')).toBeNull();
  });
  it('vuelve a la vista de solo lectura si el servidor rechaza la confirmación vencida', async () => {
    const expired = new ApiError(
      { type: 'about:blank', title: 'Forbidden', status: 403, code: 'AUTH_REAUTH_REQUIRED' },
      null,
    );
    renderApp(
      '/cuenta',
      {
        '/v1/me/consents': consents,
        '/v1/auth/me/reauth': reauth,
        '/v1/auth/me': (_url: URL, options: RequestOptions) =>
          options.method === 'PATCH' ? expired : profile,
      },
      { roles: ['ROLE_TOURIST'] },
    );
    await confirmIdentity('Editar mis datos');
    await screen.findByLabelText('Nombre completo');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar datos' }));
    expect(await screen.findByText(/Tu confirmación venció/)).toBeVisible();
    expect(screen.queryByLabelText('Nombre completo')).toBeNull();
    expect(screen.getByRole('button', { name: 'Editar mis datos' })).toBeVisible();
  });
  it('termina la edición a pedido y vuelve a solo lectura', async () => {
    renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile, '/v1/auth/me/reauth': reauth },
      { roles: ['ROLE_TOURIST'] },
    );
    await confirmIdentity('Editar mis datos');
    expect((await screen.findAllByText(/Puedes editar durante/)).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Terminar edición' }));
    expect(await screen.findByRole('button', { name: 'Editar mis datos' })).toBeVisible();
    expect(screen.queryByLabelText('Nombre completo')).toBeNull();
  });
  it('valida y enfoca el primer campo de contraseña antes de enviar', async () => {
    const { http } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile, '/v1/auth/me/reauth': reauth },
      { roles: ['ROLE_TOURIST'] },
    );
    await confirmIdentity('Cambiar contraseña');
    await screen.findByLabelText('Contraseña actual');
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
    await waitFor(() => expect(screen.getByLabelText('Contraseña actual')).toHaveFocus());
    expect(await screen.findByText('Escribe tu contraseña actual.')).toBeVisible();
    expect(http.calls.some(([path]) => path === '/v1/auth/me/password')).toBe(false);
  });
  it('impide cambiar la contraseña mientras haya asistencias pendientes', async () => {
    const db = await getOfflineDB();
    await db.put('sync_outbox', {
      mutationId: 'm1',
      sequenceNumber: 1,
      syncBatchId: null,
      aggregateType: 'OFFLINE_VALIDATION',
      aggregateId: 'a1',
      actionType: 'CREATE_VALIDATION',
      payload: {},
      status: 'QUEUED',
      retryCount: 0,
      maxRetries: 3,
      lastError: null,
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    });
    const { http } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile, '/v1/auth/me/reauth': reauth },
      { roles: ['ROLE_LINK_MANAGER'] },
    );
    await confirmIdentity('Cambiar contraseña');
    await userEvent.type(await screen.findByLabelText('Contraseña actual'), 'contraseña actual');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
    expect(await screen.findByText(/Hay asistencias pendientes/)).toBeVisible();
    expect(http.calls.some(([path]) => path === '/v1/auth/me/password')).toBe(false);
  });
  it('informa error de contraseña actual del servidor y conserva la sesión', async () => {
    const invalid = new ApiError(
      {
        type: 'about:blank',
        title: 'Datos inválidos',
        status: 422,
        invalidParams: [{ name: 'currentPassword', reason: 'La contraseña actual no coincide.' }],
      },
      null,
    );
    const { session } = renderApp(
      '/cuenta',
      {
        '/v1/me/consents': consents,
        '/v1/auth/me': profile,
        '/v1/auth/me/reauth': reauth,
        '/v1/auth/me/password': invalid,
      },
      { roles: ['ROLE_TOURIST'] },
    );
    await confirmIdentity('Cambiar contraseña');
    await userEvent.type(await screen.findByLabelText('Contraseña actual'), 'contraseña anterior');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
    expect(await screen.findByText('La contraseña actual no coincide.')).toBeVisible();
    expect(screen.getByLabelText('Contraseña actual')).toHaveFocus();
    expect(session.getState().status).toBe('authenticated');
  });
  it('cierra sesión y confirma el cambio de contraseña', async () => {
    const { http, session, router } = renderApp(
      '/cuenta',
      {
        '/v1/me/consents': consents,
        '/v1/auth/me': profile,
        '/v1/auth/me/reauth': reauth,
        '/v1/auth/me/password': () => null,
      },
      { roles: ['ROLE_TOURIST'] },
    );
    await confirmIdentity('Cambiar contraseña');
    await userEvent.type(await screen.findByLabelText('Contraseña actual'), 'contraseña anterior');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/ingresar');
    });
    expect(await screen.findByText('Tu contraseña cambió. Ingresa de nuevo.')).toBeVisible();
    expect(session.getState().status).toBe('anonymous');
    const call = http.calls.find(([path]) => path === '/v1/auth/me/password');
    expect(call?.[1]?.headers).toEqual({ 'X-Reauth-Token': 'token-de-prueba' });
  });
});
