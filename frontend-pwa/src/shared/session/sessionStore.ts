import { ApiError, NetworkError, type HttpClient } from '@/shared/api';
import {
  guardAndPurgeOnLogout,
  INACTIVITY_PURGE_MS,
  requestPersistentStorage,
  touchOfflineActivity,
} from '@/shared/offline';

import {
  authTokenResponseSchema,
  ROLES,
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
  /** Gancho de guardia y limpieza selectiva al cerrar sesión (ADR-0036 §3.4). */
  readonly onBeforeSignOut?: () => Promise<void>;
}

const LOCK_NAME = 'integrate-auth-refresh';
const CHANNEL_NAME = 'integrate-auth';
/** El refresh proactivo ocurre 60 s antes de vencer el access token de 900 s. */
const REFRESH_MARGIN_MS = 60_000;
/**
 * Clave de la tupla pública del usuario offline (`offline_data_indexeddb.md` §1 y ADR-0036 §4).
 * Jamás almacena `accessToken` ni `refresh_token` (FE-DIR-001 Directiva 4.1).
 */
export const OFFLINE_SESSION_USER_KEY = 'integrate.offline.session.v1' as const;

interface StoredOfflineSession {
  readonly user: SessionUser;
  readonly savedAtMs: number;
}

type ChannelMessage = { readonly type: 'signed-in' } | { readonly type: 'signed-out' };

export function hasRole(state: SessionState, role: Role): boolean {
  return state.status === 'authenticated' && state.user.roles.includes(role);
}

function isFieldOperator(user: SessionUser): boolean {
  return user.roles.includes('ROLE_LINK_MANAGER') || user.roles.includes('ROLE_HOST');
}

function saveOfflineSessionUser(user: SessionUser, nowMs: number): void {
  if (!isFieldOperator(user)) return;
  try {
    if (typeof localStorage === 'undefined') return;
    const record: StoredOfflineSession = { user, savedAtMs: nowMs };
    localStorage.setItem(OFFLINE_SESSION_USER_KEY, JSON.stringify(record));
  } catch {
    // Ignorado si localStorage no está disponible.
  }
}

function clearOfflineSessionUser(): void {
  try {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(OFFLINE_SESSION_USER_KEY);
  } catch {
    // Ignorado si localStorage no está disponible.
  }
}

function readOfflineSessionUser(nowMs: number): SessionUser | null {
  try {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(OFFLINE_SESSION_USER_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredOfflineSession>;
    if (
      typeof parsed.savedAtMs !== 'number' ||
      nowMs - parsed.savedAtMs > INACTIVITY_PURGE_MS ||
      !parsed.user ||
      typeof parsed.user.id !== 'string' ||
      typeof parsed.user.email !== 'string' ||
      typeof parsed.user.fullName !== 'string' ||
      !Array.isArray(parsed.user.roles)
    ) {
      clearOfflineSessionUser();
      return null;
    }
    const validRoles = parsed.user.roles.filter((r): r is Role =>
      (ROLES as readonly string[]).includes(r),
    );
    const user: SessionUser = {
      id: parsed.user.id,
      email: parsed.user.email,
      fullName: parsed.user.fullName,
      roles: validRoles,
    };
    return isFieldOperator(user) ? user : null;
  } catch {
    return null;
  }
}

export function createSessionStore({
  http,
  initialStatus = 'checking',
  now = () => Date.now(),
  locks = typeof navigator !== 'undefined' ? navigator.locks : undefined,
  channelName = CHANNEL_NAME,
  onBeforeSignOut = () => guardAndPurgeOnLogout({ now: () => new Date(now()) }),
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
    clearOfflineSessionUser();
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
    saveOfflineSessionUser(response.user, now());
    if (isFieldOperator(response.user)) {
      touchOfflineActivity({ now: () => new Date(now()) });
      void requestPersistentStorage();
    }
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
            // ADR-0036 §4: en modo avión (NetworkError), si existe la tupla pública de un Gestor/Anfitrión
            // activo (< 7 días), se habilita la sesión local sin token para abrir /gestor desconectado.
            const offlineUser =
              error instanceof NetworkError ? readOfflineSessionUser(now()) : null;
            if (offlineUser) {
              setState({ status: 'authenticated', user: offlineUser, consentUpdate: null });
            } else {
              setState({ status: 'anonymous' });
            }
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
      // ADR-0036 §3.4 (Logout Guard): bloquea el cierre de sesión si hay mutaciones pendientes en sync_outbox;
      // si está en 0, purga selectivamente operational_roster, revocation_list y local_validations.
      await onBeforeSignOut();
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

  // Al recuperar conectividad desde modo avión, obtiene un accessToken fresco en memoria.
  if (typeof window !== 'undefined') {
    window.addEventListener('online', () => {
      if (state.status === 'authenticated' && accessToken === null) {
        void store.refresh();
      }
    });
  }

  return store;
}
