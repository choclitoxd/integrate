/**
 * Formato de cifras, fechas y nombres para la PWA (es-CO, ADR-0012 §10).
 * Las fechas del contrato son locales de America/Bogota ("YYYY-MM-DD" y "YYYY-MM-DDTHH:mm:ss"):
 * se formatean como fechas civiles en UTC para que la zona del teléfono nunca las desplace.
 */

const LOCALE = 'es-CO';
export const BOGOTA_TIME_ZONE = 'America/Bogota';

const COP = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

/** 80000 → "$ 80.000". */
export function formatCop(amount: number): string {
  return COP.format(amount);
}

/** 45 → "45 min"; 270 → "4 h 30 min"; 2880 → "2 días". */
export function formatDuration(minutes: number): string {
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  const parts: string[] = [];
  if (days > 0) parts.push(days === 1 ? '1 día' : `${String(days)} días`);
  if (hours > 0) parts.push(`${String(hours)} h`);
  if (mins > 0 && days === 0) parts.push(`${String(mins)} min`);
  return parts.length > 0 ? parts.join(' ') : '0 min';
}

const KM = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 2 });

/** 12.5 → "12,5 km"; 3 → "3 km". */
export function formatDistance(km: number): string {
  return `${KM.format(km)} km`;
}

const SMALL_WORDS = new Set(['de', 'del', 'la', 'las', 'los', 'el', 'y', 'e', 'en']);

/** Nombres DANE en mayúsculas → formato título: "SAN JUAN DE NEPOMUCENO" → "San Juan de Nepomuceno". */
export function toTitleCase(name: string): string {
  return name
    .toLocaleLowerCase(LOCALE)
    .split(/(\s+|-)/)
    .map((word, index) => {
      if (/^(\p{L}\.)+$/u.test(word)) return word.toLocaleUpperCase(LOCALE); // siglas: "D.C."
      if (index > 0 && SMALL_WORDS.has(word)) return word;
      return word.charAt(0).toLocaleUpperCase(LOCALE) + word.slice(1);
    })
    .join('');
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

function civilDate(isoDate: string): Date {
  const match = ISO_DATE.exec(isoDate);
  if (!match) throw new RangeError(`Fecha inválida: ${isoDate}`);
  return new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Fecha civil de hoy en America/Bogota ("YYYY-MM-DD"), independiente de la zona del teléfono. */
export function todayInBogota(now: Date = new Date()): string {
  // en-CA produce YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: BOGOTA_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export function addDays(isoDate: string, days: number): string {
  const date = civilDate(isoDate);
  date.setUTCDate(date.getUTCDate() + days);
  return toIsoDate(date);
}

/** Lunes de la semana que contiene la fecha (semana colombiana de lunes a domingo). */
export function startOfWeek(isoDate: string): string {
  const weekday = civilDate(isoDate).getUTCDay(); // 0 = domingo
  return addDays(isoDate, -((weekday + 6) % 7));
}

function formatCivil(isoDate: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(LOCALE, { ...options, timeZone: 'UTC' }).format(
    civilDate(isoDate),
  );
}

/** "jueves, 15 de octubre". */
export function formatLongDate(isoDate: string): string {
  return formatCivil(isoDate, { weekday: 'long', day: 'numeric', month: 'long' });
}

/** "octubre de 2026". */
export function formatMonthYear(isoDate: string): string {
  return formatCivil(isoDate, { month: 'long', year: 'numeric' });
}

/** Inicial del día para la rejilla semanal: "L", "M", "M", "J", "V", "S", "D". */
export function weekdayInitial(isoDate: string): string {
  return 'DLMMJVS'.charAt(civilDate(isoDate).getUTCDay());
}

export function dayOfMonth(isoDate: string): number {
  return civilDate(isoDate).getUTCDate();
}

const LOCAL_DATE_TIME = /^\d{4}-\d{2}-\d{2}T(\d{2}):(\d{2})/;

/** "2026-10-15T07:00:00" (hora local descriptiva) → "7:00 a. m.", sin conversión de zona. */
export function formatLocalTime(localDateTime: string): string {
  const match = LOCAL_DATE_TIME.exec(localDateTime);
  if (!match) throw new RangeError(`Hora inválida: ${localDateTime}`);
  const date = new Date(Date.UTC(2000, 0, 1, Number(match[1]), Number(match[2])));
  return new Intl.DateTimeFormat(LOCALE, {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: 'UTC',
  }).format(date);
}

/** Hora del reloj del teléfono en Bogotá, para "Cupos actualizados a las …". */
export function formatClockTime(epochMillis: number): string {
  return new Intl.DateTimeFormat(LOCALE, {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: BOGOTA_TIME_ZONE,
  }).format(new Date(epochMillis));
}

/** Instante del contrato en Bogotá: "martes, 13 de octubre, 2:05 p. m." (vencimientos). */
export function formatDateTime(epochMillis: number): string {
  return new Intl.DateTimeFormat(LOCALE, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: BOGOTA_TIME_ZONE,
  }).format(new Date(epochMillis));
}

/** "1 persona" / "3 personas". */
export function pluralize(count: number, singular: string, plural: string): string {
  return `${String(count)} ${count === 1 ? singular : plural}`;
}
