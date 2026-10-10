import { ApiError, NetworkError } from '@/shared/api';

import { Button } from './Button';
import { Icon } from './Icon';

interface ProblemNoticeProps {
  readonly error: unknown;
  /** Si se provee, se ofrece "Reintentar". */
  readonly onRetry?: () => void;
  /** Mensajes por `code` del contrato que la vista quiere personalizar. */
  readonly messages?: Readonly<Record<string, string>>;
}

interface Copy {
  readonly title: string;
  readonly body: string;
}

function describe(error: unknown, messages: Readonly<Record<string, string>>): Copy {
  if (error instanceof NetworkError) {
    return {
      title: 'Sin conexión',
      body: 'No pudimos comunicarnos con Intégrate. Revisa tu señal o datos móviles e inténtalo de nuevo.',
    };
  }
  if (error instanceof ApiError) {
    const custom = error.code ? messages[error.code] : undefined;
    if (custom) return { title: 'No pudimos completar la consulta', body: custom };
    if (error.status === 404) {
      return { title: 'No encontramos lo que buscas', body: 'Puede que ya no esté disponible.' };
    }
    if (error.status === 400 || error.status === 422) {
      return {
        title: 'Revisa la búsqueda',
        body: 'Algunos datos de la consulta no son válidos. Ajusta los filtros e inténtalo de nuevo.',
      };
    }
    if (error.status >= 500) {
      return {
        title: 'El servicio no responde',
        body: 'Estamos teniendo un problema temporal. Inténtalo de nuevo en unos minutos.',
      };
    }
  }
  return { title: 'Algo salió mal', body: 'No pudimos completar la consulta. Inténtalo de nuevo.' };
}

/**
 * Error perceptible en español con el código de seguimiento (FE-DIR-001, Directiva 5.3).
 * Nunca muestra el payload crudo ni el mensaje técnico del servidor.
 */
export function ProblemNotice({ error, onRetry, messages = {} }: ProblemNoticeProps) {
  const { title, body } = describe(error, messages);
  const traceId = error instanceof ApiError ? error.traceId : undefined;
  const offline = error instanceof NetworkError;

  return (
    <div
      role="alert"
      className="rounded-xl border-2 border-danger-border bg-danger-bg px-4 py-4 text-danger-text"
    >
      <p className="flex items-center gap-2 font-bold">
        <Icon name={offline ? 'wifiOff' : 'alert'} />
        {title}
      </p>
      <p className="mt-1 text-ink">{body}</p>
      {traceId && (
        <p className="mt-2 text-sm text-ink-muted">
          Código de seguimiento: <code className="font-mono break-all text-ink">{traceId}</code>
        </p>
      )}
      {onRetry && (
        <Button variant="secondary" className="mt-3" onClick={onRetry}>
          <Icon name="refresh" />
          Reintentar
        </Button>
      )}
    </div>
  );
}
