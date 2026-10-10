import { useEffect, useId, useRef, useState } from 'react';

import { ApiError, NetworkError, useHttpClient } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { Button, Icon } from '@/shared/ui';

import { issueActivationCode, type ActivationCode } from '../api';

interface ActivationCodeIssuerProps {
  readonly hostProfileId: string;
  readonly email: string;
  /** Ya hubo un código antes (lista): emitir otro anula el anterior. */
  readonly reissue?: boolean;
}

type IssueState =
  | { readonly status: 'idle' }
  | { readonly status: 'issuing' }
  | { readonly status: 'issued'; readonly code: ActivationCode }
  | { readonly status: 'failed'; readonly message: string };

function issueFailure(error: unknown): string {
  if (error instanceof NetworkError) {
    return 'Sin conexión: el código no se emitió. Inténtalo cuando tengas señal.';
  }
  if (error instanceof ApiError) {
    if (error.status === 409) return 'Esta persona recupera su acceso por correo.';
    if (error.status === 404) return 'Este anfitrión ya no está en tu jurisdicción.';
    if (error.code === 'ENROLLMENT_MANAGER_NOT_ACTIVE') {
      return 'Tu acreditación como gestor no está activa: no puedes emitir códigos.';
    }
  }
  return 'No se pudo emitir el código. Inténtalo de nuevo.';
}

/**
 * Emite y muestra el código de activación una sola vez (ADR-0018 §5): vive solo en el estado de
 * este componente, nunca en la caché de consultas ni en el almacenamiento del teléfono.
 */
export function ActivationCodeIssuer({
  hostProfileId,
  email,
  reissue = false,
}: ActivationCodeIssuerProps) {
  const http = useHttpClient();
  const headingId = useId();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [state, setState] = useState<IssueState>({ status: 'idle' });

  useEffect(() => {
    if (state.status === 'issued') headingRef.current?.focus();
  }, [state.status]);

  async function issue() {
    setState({ status: 'issuing' });
    try {
      setState({ status: 'issued', code: await issueActivationCode(http, hostProfileId) });
    } catch (error) {
      setState({ status: 'failed', message: issueFailure(error) });
    }
  }

  if (state.status === 'issued') {
    const { code, expiresAt } = state.code;
    return (
      <section
        aria-labelledby={headingId}
        className="rounded-xl border-2 border-brand-border bg-brand-soft px-4 py-4"
      >
        <h3 id={headingId} ref={headingRef} tabIndex={-1} className="font-bold text-ink">
          Código de activación
        </h3>
        <p className="mt-2 text-center font-mono text-3xl font-bold tracking-widest text-ink">
          {code}
        </p>
        <p className="mt-1 text-center text-sm text-ink-muted first-letter:uppercase">
          Vence el {formatDateTime(Date.parse(expiresAt))}
        </p>
        <ol className="mt-3 grid list-decimal gap-1 pl-5 text-ink">
          <li>
            Entrégalo en persona. El anfitrión entra a{' '}
            <span className="font-semibold break-all">{window.location.host}/activar</span>.
          </li>
          <li>
            Escribe su correo: <span className="font-semibold break-all">{email}</span>
          </li>
          <li>Escribe el código y crea su contraseña.</li>
        </ol>
        <p className="mt-3 flex items-start gap-2 font-semibold text-warning-text">
          <Icon name="alert" className="mt-0.5" />
          No se volverá a mostrar. Si se pierde, emite uno nuevo: el anterior dejará de servir.
        </p>
      </section>
    );
  }

  return (
    <div>
      <Button
        variant="secondary"
        disabled={state.status === 'issuing'}
        onClick={() => void issue()}
      >
        {state.status === 'issuing' ? 'Emitiendo código…' : 'Emitir código de activación'}
      </Button>
      {reissue && state.status === 'idle' && (
        <p className="mt-1.5 text-sm text-ink-muted">
          Si ya tenía uno, ese código dejará de servir.
        </p>
      )}
      {state.status === 'failed' && (
        <p
          role="alert"
          className="mt-1.5 flex items-start gap-1.5 text-sm font-semibold text-danger-text"
        >
          <Icon name="alert" size={18} className="mt-px" />
          {state.message}
        </p>
      )}
    </div>
  );
}
