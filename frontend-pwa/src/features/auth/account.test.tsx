import { screen, waitFor, within } from '@testing-library/react';
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

/** Abre la ventana emergente de cambiar la contraseña desde «Mi cuenta». */
async function openPassword() {
  await userEvent.click(await screen.findByRole('button', { name: 'Cambiar contraseña' }));
  await screen.findByRole('dialog', { name: 'Cambiar contraseña' });
}

/** El botón de enviar de la ventana (comparte nombre con el que la abre). */
function submitPassword() {
  return within(screen.getByRole('dialog', { name: 'Cambiar contraseña' })).getByRole('button', {
    name: 'Cambiar contraseña',
  });
}

describe('cuenta propia', () => {
  afterEach(async () => {
    await clearAllOfflineStores();
  });
  it.each(['ROLE_TOURIST', 'ROLE_HOST', 'ROLE_LINK_MANAGER', 'ROLE_ADMIN'])(
    'edita sus datos en una ventana emergente, sin pedir contraseña, para %s',
    async (role) => {
      const { http, session } = renderApp(
        '/cuenta',
        {
          '/v1/me/consents': consents,
          '/v1/auth/me': (_url: URL, options: RequestOptions) =>
            options.method === 'PATCH' ? options.body : profile,
        },
        { roles: [role] },
      );
      await userEvent.click(await screen.findByRole('button', { name: 'Editar mis datos' }));
      const dialog = await screen.findByRole('dialog', { name: 'Editar mis datos' });
      const name = within(dialog).getByLabelText('Nombre completo');
      await userEvent.clear(name);
      await userEvent.type(name, 'Nombre Actualizado');
      await userEvent.click(within(dialog).getByRole('button', { name: 'Guardar datos' }));
      await screen.findByText('Tus datos se guardaron.');
      expect((await screen.findAllByText('Nombre Actualizado')).length).toBeGreaterThan(0);
      const patch = http.calls.find(
        ([path, options]) => path === '/v1/auth/me' && options?.method === 'PATCH',
      );
      expect(patch?.[1]?.headers).toBeUndefined();
      expect(http.calls.some(([path]) => path === '/v1/auth/me/reauth')).toBe(false);
      expect(session.getState()).toMatchObject({ user: { fullName: 'Nombre Actualizado' } });
    },
  );
  it('muestra los datos en «Mi cuenta» sin formularios hasta abrir la ventana', async () => {
    renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile },
      { roles: ['ROLE_TOURIST'] },
    );
    expect(await screen.findByText('Carlos Restrepo')).toBeVisible();
    expect(await screen.findByText('+57 300 123 4567')).toBeVisible();
    expect(screen.queryByLabelText('Nombre completo')).toBeNull();
    expect(screen.queryByLabelText('Contraseña actual')).toBeNull();
  });
  it('cancelar la edición cierra la ventana sin guardar', async () => {
    const { http } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile },
      { roles: ['ROLE_TOURIST'] },
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Editar mis datos' }));
    const dialog = await screen.findByRole('dialog', { name: 'Editar mis datos' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => {
      expect(screen.queryByLabelText('Nombre completo')).toBeNull();
    });
    expect(http.calls.some(([, options]) => options?.method === 'PATCH')).toBe(false);
  });
  it('cada rol tiene «Mi cuenta» en su zona', async () => {
    const zones = {
      ROLE_TOURIST: '/turista/cuenta',
      ROLE_HOST: '/anfitrion/cuenta',
      ROLE_LINK_MANAGER: '/gestor/cuenta',
      ROLE_ADMIN: '/admin/cuenta',
    } as const;
    for (const [role, zone] of Object.entries(zones)) {
      const { router, unmount } = renderApp(
        zone,
        { '/v1/me/consents': consents, '/v1/auth/me': profile },
        { roles: [role] },
      );
      expect(await screen.findByRole('button', { name: 'Editar mis datos' })).toBeVisible();
      expect(router.state.location.pathname).toBe(zone);
      unmount();
    }
  });
  it('valida y enfoca el primer campo de contraseña antes de enviar', async () => {
    const { http } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile, '/v1/auth/me/reauth': reauth },
      { roles: ['ROLE_TOURIST'] },
    );
    await openPassword();
    await userEvent.click(submitPassword());
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
    await openPassword();
    await userEvent.type(await screen.findByLabelText('Contraseña actual'), 'contraseña actual');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(submitPassword());
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
    await openPassword();
    await userEvent.type(await screen.findByLabelText('Contraseña actual'), 'contraseña anterior');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(submitPassword());
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
    await openPassword();
    await userEvent.type(await screen.findByLabelText('Contraseña actual'), 'contraseña anterior');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(submitPassword());
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/ingresar');
    });
    expect(await screen.findByText('Tu contraseña cambió. Ingresa de nuevo.')).toBeVisible();
    expect(session.getState().status).toBe('anonymous');
    const call = http.calls.find(([path]) => path === '/v1/auth/me/password');
    expect(call?.[1]?.headers).toEqual({ 'X-Reauth-Token': 'token-de-prueba' });
  });
  it('no cambia la contraseña si la actual es incorrecta', async () => {
    const wrong = new ApiError(
      {
        type: 'about:blank',
        title: 'Datos inválidos',
        status: 422,
        invalidParams: [{ name: 'password', reason: 'incorrect password' }],
      },
      null,
    );
    const { http } = renderApp(
      '/cuenta',
      { '/v1/me/consents': consents, '/v1/auth/me': profile, '/v1/auth/me/reauth': wrong },
      { roles: ['ROLE_TOURIST'] },
    );
    await openPassword();
    await userEvent.type(screen.getByLabelText('Contraseña actual'), 'contraseña equivocada');
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'contraseña nueva segura');
    await userEvent.type(
      screen.getByLabelText('Confirmar contraseña nueva'),
      'contraseña nueva segura',
    );
    await userEvent.click(submitPassword());
    expect(await screen.findByText('incorrect password')).toBeVisible();
    expect(http.calls.some(([path]) => path === '/v1/auth/me/password')).toBe(false);
  });
});
