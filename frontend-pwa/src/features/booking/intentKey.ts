/**
 * Clave de idempotencia por intención (ADR-0015 §6): se conserva mientras el cuerpo enviado no
 * cambie, así un reintento tras una respuesta perdida no bloquea cupos dos veces; si el turista
 * corrige algo, el cuerpo cambia y la clave también (si no, el servidor respondería 409).
 */
export interface IntentKeys {
  readonly keyFor: (body: unknown) => string;
  readonly reset: () => void;
}

export function createIntentKeys(generate: () => string = () => crypto.randomUUID()): IntentKeys {
  let current: { readonly key: string; readonly body: string } | null = null;
  return {
    keyFor(body) {
      const serialized = JSON.stringify(body);
      if (current?.body !== serialized) current = { key: generate(), body: serialized };
      return current.key;
    },
    reset() {
      current = null;
    },
  };
}
