/** DTO públicos del catálogo, escritos desde `docs/api/openapi.yaml` (ADR-0006, ADR-0012 §5). */

export type PhysicalIntensity = 'LOW' | 'MEDIUM' | 'HIGH';

/** Forma del recorrido (ADR-0022): circular, ida y vuelta por el mismo camino o solo ida. */
export type RouteType = 'LOOP' | 'OUT_AND_BACK' | 'ONE_WAY';

export interface Place {
  readonly code: string;
  readonly name: string;
}

export interface Category {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
}

export interface PageMeta {
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

/** Tarjeta pública: sin datos del anfitrión (R-05). */
export interface ExperienceSummary {
  readonly id: string;
  readonly title: string;
  readonly physicalIntensity: PhysicalIntensity;
  readonly durationMinutes: number;
  /** Datos de recorrido opcionales (ADR-0022): `null` si el anfitrión no los informó. */
  readonly routeType: RouteType | null;
  readonly distanceKm: number | null;
  readonly roundTripMinutes: number | null;
  readonly stopsCount: number;
  readonly currency: 'COP';
  readonly unitPriceCop: number;
  readonly maxCapacityPerSlot: number;
  readonly municipality: Place;
  readonly department: Place;
  readonly associationName: string;
  readonly categoryCodes: readonly string[];
}

export interface ExperiencePage extends PageMeta {
  readonly items: readonly ExperienceSummary[];
}

export interface ExperienceDetail {
  readonly summary: ExperienceSummary;
  readonly description: string;
  readonly safetyGuidelines: string;
  readonly vereda: string | null;
  readonly meetingPoint: string;
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly timezone: 'America/Bogota';
  readonly stops: readonly RouteStop[];
  /**
   * `false` si la verificación del punto de encuentro quedó en REVIEW o MISMATCH (ADR-0030): las coordenadas no
   * coinciden con la dirección escrita y el mapa se centra por la dirección. Ausente en servidores anteriores.
   */
  readonly meetingPointCoordinatesTrusted?: boolean;
}

/** Parada del recorrido, en orden de visita. */
export interface RouteStop {
  readonly name: string;
  readonly description: string | null;
}

export interface ScheduleAvailability {
  readonly scheduleId: string;
  /** Fecha local de Bogotá (YYYY-MM-DD). */
  readonly localDate: string;
  readonly startTime: string;
  readonly endTime: string;
  /** Hora local descriptiva sin offset (YYYY-MM-DDTHH:mm:ss). */
  readonly localStartTime: string;
  readonly localEndTime: string;
  readonly totalSlots: number;
  /** Lectura sin bloqueo: solo POST /bookings/lock garantiza el cupo. */
  readonly availableSlots: number;
  readonly status: 'OPEN';
}

export interface ExperienceAvailability {
  readonly experienceId: string;
  readonly title: string;
  readonly timezone: 'America/Bogota';
  readonly currency: 'COP';
  readonly unitPriceCop: number;
  readonly maxCapacityPerSlot: number;
  readonly dateFrom: string;
  readonly dateTo: string;
  readonly schedules: readonly ScheduleAvailability[];
}
