import { createContext, useContext, useSyncExternalStore } from 'react';

import type { SessionState, SessionStore } from './sessionStore';

export const SessionContext = createContext<SessionStore | null>(null);

export function useSessionStore(): SessionStore {
  const store = useContext(SessionContext);
  if (!store) throw new Error('useSessionStore requiere <SessionContext.Provider>.');
  return store;
}

/** Estado de la sesión suscrito al almacén externo (ADR-0013 §2). */
export function useSession(): SessionState {
  const store = useSessionStore();
  return useSyncExternalStore(store.subscribe, store.getState);
}
