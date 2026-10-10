import { ApiError, type HttpClient } from '@/shared/api';

import {
  authTokenResponseSchema,
  type AuthTokenResponse,
  type ConsentPurpose,
  type Role,
  type SessionUser,
} from './schemas';

/** Re-consentimiento exigido por el login o el refresh (`requiresConsentUpdate`, RF-02). */
export interface ConsentUpdate {
  readonly policyVersion: string;
  readonly purposes: readonly ConsentPurpose[];
}

/** Estado visible para React. El access token nunca forma parte de él (ADR-0013 §2). */
export type SessionState =
  | { readonly status: 'checking' }
  | { readonly status: 'anonymous' }
  | {
      readonly status: 'authenticated';
      readonly user: SessionUser;
      readonly consentUpdate: ConsentUpdate | null;
    };

export interface Credentials {
  readonly email: string;
  readonly password: string;
}

export interface SessionStore {
  readonly getState: () => SessionState;
  readonly subscribe: (listener: () => void) => () => void;
  /** Token vigente en memoria, para el HttpClient. */
  readonly getAccessToken: () => string | null;
  /** Renovación single-flight: una por pestaña y serializada entre pestañas. */
  readonly refresh: () => Promise<string | null>;
  /** Restaura la sesión al arrancar con la cookie de refresh; un 401 deja `anonymous`. */
  readonly restore: () => Promise<void>;
  readonly signIn: (credentials: Credentials) => Promise<SessionUser>;
  readonly signOut: () => Promise<void>;
  /** El titular aceptó las finalidades pendientes. */
  readonly consentCompleted: () => void;
  /** Adopta una respuesta de login o refresh ya validada (también usado por las pruebas). */
  readonly adopt: (response: AuthTokenResponse) => void;
}

export interface SessionStoreOptions {
  /** Cliente sin token ni renovación: solo para /v1/auth/*. */
  readonly http: HttpClient;
  readonly initialStatus?: 'checking' | 'anonymous';
  readonly now?: () => number;
  readonly locks?: Pick<LockManager, 'request'> | undefined;
  readonly channelName?: string | null;
}

const LOCK_NAME = 'integrate-auth-refresh';
const CHANNEL_NAME = 'integrate-auth';
/** El refresh proactivo ocurre 60 s antes de vencer el access token de 900 s. */
const REFRESH_MARGIN_MS = 60_000;

type ChannelMessage = { readonly type: 'signed-in' } | { readonly type: 'signed-out' };

export function hasRole(state: SessionState, role: Role): boolean {
  return state.status === 'authenticated' && state.user.roles.includes(role);
}

export function createSessionStore({
  http,
  initialStatus = 'checking',
  now = () => Date.now(),
  locks = typeof navigator !== 'undefined' ? navigator.locks : undefined,
  channelName = CHANNEL_NAME,
}: SessionStoreOptions): SessionStore {
  let state: SessionState = { status: initialStatus };
  let accessToken: string | null = null;
  let expiresAt = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let inFlight: Promise<string | null> | null = null;
  const listeners = new Set<() => void>();

  const channel =
    channelName && typeof BroadcastChannel !== 'undefined'
      ? new BroadcastChannel(channelName)
      : null;

  function setState(next: SessionState) {
    state = next;
    for (const listener of listeners) listener();
  }

  function clear(broadcast: boolean) {
    clearTimeout(timer);
    accessToken = null;
    expiresAt = 0;
    setState({ status: 'anonymous' });
    if (broadcast) channel?.postMessage({ type: 'signed-out' } satisfies ChannelMessage);
  }

  function schedule() {
    clearTimeout(timer);
    const delay = Math.max(0, expiresAt - REFRESH_MARGIN_MS - now());
    timer = setTimeout(() => void store.refresh(), delay);
  }

  function adopt(response: AuthTokenResponse) {
    accessToken = response.accessToken;
    expiresAt = now() + response.expiresIn * 1000;
    const consentUpdate =
      response.requiresConsentUpdate && response.pendingPolicyVersion
        ? { policyVersion: response.pendingPolicyVersion, purposes: response.pendingPurposes }
        : null;
    setState({ status: 'authenticated', user: response.user, consentUpdate });
    schedule();
  }

  async function exchange(path: string, body?: Credentials): Promise<AuthTokenResponse> {
    const raw = await http.request<unknown>(path, {
      method: 'POST',
      ...(body !== undefined && { body }),
    });
    const parsed = authTokenResponseSchema.safeParse(raw);
    if (!parsed.success) throw new Error('La respuesta de sesión del servidor no es válida.');
    return parsed.data;
  }

  /** Serializa entre pestañas: la segunda espera y usa la cookie que la primera ya rotó. */
  function withLock<T>(task: () => Promise<T>): Promise<T> {
    return locks ? locks.request(LOCK_NAME, task) : task();
  }

  const store: SessionStore = {
    getState: () => state,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    getAccessToken: () => accessToken,

    refresh() {
      inFlight ??= withLock(async () => {
        try {
          const response = await exchange('/v1/auth/refresh');
          adopt(response);
          return response.accessToken;
        } catch (error) {
          // Cookie inválida, vencida o cuenta suspendida: la sesión terminó. Un fallo de red no la cierra.
          if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
            clear(false);
          } else if (state.status === 'checking') {
            setState({ status: 'anonymous' });
          }
          return null;
        }
      }).finally(() => {
        inFlight = null;
      });
      return inFlight;
    },

    async restore() {
      await store.refresh();
    },

    async signIn(credentials) {
      const response = await exchange('/v1/auth/login', credentials);
      adopt(response);
      channel?.postMessage({ type: 'signed-in' } satisfies ChannelMessage);
      return response.user;
    },

    async signOut() {
      try {
        await http.request('/v1/auth/logout', { method: 'POST' });
      } catch {
        // Idempotente en el servidor; sin red, la sesión local se cierra igual y el token vence solo.
      }
      clear(true);
    },

    consentCompleted() {
      if (state.status === 'authenticated') setState({ ...state, consentUpdate: null });
    },

    adopt,
  };

  channel?.addEventListener('message', (event: MessageEvent<ChannelMessage>) => {
    if (event.data.type === 'signed-out' && state.status === 'authenticated') clear(false);
    if (event.data.type === 'signed-in' && state.status !== 'authenticated') void store.refresh();
  });

  // Una pestaña dormida puede despertar con el token vencido: se renueva al volver a primer plano.
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (
        document.visibilityState === 'visible' &&
        state.status === 'authenticated' &&
        expiresAt - now() < REFRESH_MARGIN_MS
      ) {
        void store.refresh();
      }
    });
  }

  return store;
}
