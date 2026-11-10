import type { AssociationType, AuditSeverity, ExperienceStatus, MeetingPointState } from './api';

/** Textos y tonos de la consola de administración (ADR-0042). El tono nunca es el único indicador. */

export type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

interface Label {
  readonly text: string;
  readonly tone: Tone;
}

export const EXPERIENCE_STATUS: Readonly<Record<ExperienceStatus, Label>> = {
  DRAFT: { text: 'Borrador', tone: 'neutral' },
  PENDING_REVIEW: { text: 'Por revisar', tone: 'warning' },
  REJECTED: { text: 'Rechazada', tone: 'danger' },
  APPROVED: { text: 'Publicada', tone: 'success' },
  SUSPENDED: { text: 'Suspendida', tone: 'danger' },
  RETIRED: { text: 'Retirada', tone: 'neutral' },
};

/** Orden de los filtros de la cola de moderación; `null` = todas. */
export const STATUS_FILTERS: readonly { value: ExperienceStatus | null; text: string }[] = [
  { value: 'PENDING_REVIEW', text: 'Por revisar' },
  { value: 'APPROVED', text: 'Publicadas' },
  { value: 'SUSPENDED', text: 'Suspendidas' },
  { value: 'REJECTED', text: 'Rechazadas' },
  { value: 'RETIRED', text: 'Retiradas' },
  { value: null, text: 'Todas' },
];

export const MEETING_POINT: Readonly<Record<MeetingPointState, Label>> = {
  PENDIENTE: { text: 'Sin verificar aún', tone: 'neutral' },
  VERIFICANDO: { text: 'Verificando', tone: 'neutral' },
  MATCH: { text: 'Coincide', tone: 'success' },
  REVIEW: { text: 'Por confirmar', tone: 'warning' },
  MISMATCH: { text: 'No coincide', tone: 'danger' },
  UNVERIFIED: { text: 'No se pudo verificar', tone: 'neutral' },
  CONFIRMADO: { text: 'Confirmado', tone: 'success' },
  CORREGIDO: { text: 'Corregido', tone: 'info' },
};

/** Estados dudosos que el administrador puede confirmar (ADR-0030 §4). */
export function isDoubtfulMeetingPoint(state: MeetingPointState | undefined): boolean {
  return state === 'REVIEW' || state === 'MISMATCH';
}

export const ASSOCIATION_TYPES: readonly { value: AssociationType; label: string }[] = [
  { value: 'ASOCIACION_VICTIMAS', label: 'Asociación de víctimas' },
  { value: 'COOPERATIVA_CAMPESINA', label: 'Cooperativa campesina' },
  { value: 'JUNTA_ACCION_COMUNAL', label: 'Junta de acción comunal' },
  { value: 'OTRA', label: 'Otra' },
];

export function associationTypeLabel(type: AssociationType): string {
  return ASSOCIATION_TYPES.find((option) => option.value === type)?.label ?? type;
}

export const SEVERITY: Readonly<Record<AuditSeverity, Label>> = {
  INFO: { text: 'Informativa', tone: 'neutral' },
  WARNING: { text: 'Advertencia', tone: 'warning' },
  HIGH: { text: 'Alta', tone: 'danger' },
  CRITICAL: { text: 'Crítica', tone: 'danger' },
};

export const INTENSITY: Readonly<Record<'LOW' | 'MEDIUM' | 'HIGH', string>> = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
};

const INSTANT = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'short',
  timeStyle: 'medium',
  timeZone: 'America/Bogota',
});

/** Instante del contrato en hora de Bogotá: "9/11/2026, 2:05:33 p. m.". */
export function formatInstant(iso: string): string {
  const time = Date.parse(iso);
  return Number.isNaN(time) ? iso : INSTANT.format(new Date(time));
}

/** Identificador abreviado para tablas; el completo va en el atributo `title`. */
export function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}
