/** Error de un parámetro concreto (contrato `InvalidParam`, dtos_contrato_api.md §2). */
export interface InvalidParam {
  readonly name: string;
  readonly reason: string;
}

/** Cuerpo `application/problem+json` (RFC 9457) emitido por el backend (ADR-0003). */
export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly code?: string;
  readonly traceId?: string;
  readonly invalidParams?: readonly InvalidParam[];
  /** Extensiones por caso: availableSlots, lockedUntil, requiredPurposes, policyVersion… */
  readonly [extension: string]: unknown;
}

/** El servidor respondió con un estado HTTP de error. */
export class ApiError extends Error {
  override readonly name = 'ApiError';
  readonly status: number;
  readonly code: string | undefined;
  readonly traceId: string | undefined;
  readonly invalidParams: readonly InvalidParam[];
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail, traceIdHeader: string | null) {
    super(problem.detail ?? problem.title);
    this.problem = problem;
    this.status = problem.status;
    this.code = problem.code;
    this.traceId = problem.traceId ?? traceIdHeader ?? undefined;
    this.invalidParams = problem.invalidParams ?? [];
  }
}

/**
 * La petición no obtuvo respuesta HTTP (sin red, DNS, CORS, cancelación).
 * Se distingue de ApiError para que la cola offline (T-2.7) decida si reintentar.
 */
export class NetworkError extends Error {
  override readonly name = 'NetworkError';

  constructor(cause: unknown) {
    super('No fue posible comunicarse con el servidor.', { cause });
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isInvalidParam(value: unknown): value is InvalidParam {
  return isRecord(value) && typeof value.name === 'string' && typeof value.reason === 'string';
}

/** Normaliza un cuerpo de error cualquiera a ProblemDetail, sin confiar en su forma. */
export function toProblemDetail(body: unknown, status: number, statusText: string): ProblemDetail {
  const source = isRecord(body) ? body : {};
  const invalidParams = Array.isArray(source.invalidParams)
    ? source.invalidParams.filter(isInvalidParam)
    : undefined;
  return {
    ...source,
    type: typeof source.type === 'string' ? source.type : 'about:blank',
    title: typeof source.title === 'string' ? source.title : statusText || `HTTP ${String(status)}`,
    status: typeof source.status === 'number' ? source.status : status,
    ...(typeof source.code === 'string' && { code: source.code }),
    ...(typeof source.traceId === 'string' && { traceId: source.traceId }),
    ...(invalidParams && { invalidParams }),
  };
}
