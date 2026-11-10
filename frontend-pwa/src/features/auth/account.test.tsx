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
describe('cuenta propia', () => {
  afterEach(async () => {
    await clearAllOfflineStores();
  });
  it.each(['ROLE_TOURIST', 'ROLE_HOST', 'ROLE_LINK_MANAGER', 'ROLE_ADMIN'])(
    'consulta y actualiza sus datos para %s',
    async (role) => {
      const { http, session } = renderApp(
        '/cuenta',
        {
          '/v1/me/consents': consents,
          '/v1/auth/me': (_url: URL, options: RequestOptions) =>
            options.method === 'PATCH'
              ? options.body
              : { fullName: 'Carlos Restrepo', phone: '+573001234567' },
        },
        { roles: [role] },
      );
      const name = await screen.findByLabelText('Nombre completo');
      await userEvent.clear(name);
      await userEvent.type(name, 'Nombre Actualizado');
      await userEvent.click(screen.getByRole('button', { name: 'Guardar datos' }));
      await screen.findByText('Tus datos se guardaron.');
      expect(
        http.calls.some(([path, options]) => path === '/v1/auth/me' && options?.method === 'PATCH'),
      ).toBe(true);
      expect(session.getState()).toMatchObject({ user: { fullName: 'Nombre Actualizado' } });
    },
  );
  it('valida y enfoca el primer campo de contraseña antes de enviar', async () => {
    const { http } = renderApp(
      '/cuenta',
      {
        '/v1/me/consents': consents,
        '/v1/auth/me': { fullName: 'Carlos Restrepo', phone: '+573001234567' },
      },
      { roles: ['ROLE_TOURIST'] },
    );
    await screen.findByLabelText('Nombre completo');
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
    await waitFor(() => expect(screen.getByLabelText('Contraseña actual')).toHaveFocus());
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
      {
        '/v1/me/consents': consents,
        '/v1/auth/me': { fullName: 'Carlos Restrepo', phone: '+573001234567' },
      },
      { roles: ['ROLE_LINK_MANAGER'] },
    );
    await screen.findByLabelText('Nombre completo');
    await userEvent.type(screen.getByLabelText('Contraseña actual'), 'contraseña actual');
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
        '/v1/auth/me': { fullName: 'Carlos Restrepo', phone: '+573001234567' },
        '/v1/auth/me/password': invalid,
      },
      { roles: ['ROLE_TOURIST'] },
    );
    await screen.findByLabelText('Nombre completo');
    await userEvent.type(screen.getByLabelText('Contraseña actual'), 'contraseña anterior');
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
    const { session, router } = renderApp(
      '/cuenta',
      {
        '/v1/me/consents': consents,
        '/v1/auth/me': { fullName: 'Carlos Restrepo', phone: '+573001234567' },
        '/v1/auth/me/password': () => null,
      },
      { roles: ['ROLE_TOURIST'] },
    );
    await screen.findByLabelText('Nombre completo');
    await userEvent.type(screen.getByLabelText('Contraseña actual'), 'contraseña anterior');
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
  });
});
