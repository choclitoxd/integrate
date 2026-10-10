/** Datos de catálogo para las pruebas, con la forma de `openapi.yaml`. */

export const EXPERIENCE_ID = '8f8b3c1c-3b98-4c8d-b94d-7bc432a5e912';

export const summary = {
  id: EXPERIENCE_ID,
  title: 'Sendero de la Memoria – Vereda La Esperanza',
  physicalIntensity: 'MEDIUM',
  durationMinutes: 270,
  currency: 'COP',
  unitPriceCop: 80000,
  maxCapacityPerSlot: 12,
  municipality: { code: '05649', name: 'SAN CARLOS' },
  department: { code: '05', name: 'ANTIOQUIA' },
  associationName: 'Asociación Campesina La Esperanza',
  categoryCodes: ['MEMORIA_HISTORICA'],
} as const;

export function page(items: readonly unknown[], pageNumber = 0, totalPages = 1) {
  return { page: pageNumber, size: 10, totalElements: items.length, totalPages, items };
}

export const categories = [
  { code: 'MEMORIA_HISTORICA', name: 'Memoria histórica', description: null },
  { code: 'AGROTURISMO', name: 'Agroturismo', description: null },
];

export const departments = [
  { code: '05', name: 'ANTIOQUIA' },
  { code: '13', name: 'BOLÍVAR' },
];

export const municipalities = [
  { code: '05649', name: 'SAN CARLOS' },
  { code: '05001', name: 'MEDELLÍN' },
];

export const detail = {
  summary,
  description: 'Recorrido por la memoria del retorno campesino.',
  safetyGuidelines: 'Usa botas y lleva agua.',
  vereda: 'La Esperanza',
  meetingPoint: 'Parque principal de San Carlos',
  latitude: 6.187,
  longitude: -74.995,
  timezone: 'America/Bogota',
};

export function schedule(id: string, localDate: string, hour: string, available: number) {
  return {
    scheduleId: id,
    localDate,
    startTime: `${localDate}T12:00:00Z`,
    endTime: `${localDate}T17:00:00Z`,
    localStartTime: `${localDate}T${hour}:00`,
    localEndTime: `${localDate}T${hour.replace(/^0?(\d+)/, (h) => String(Number(h) + 5).padStart(2, '0'))}:00`,
    totalSlots: 12,
    availableSlots: available,
    status: 'OPEN',
  };
}

export function availability(dateFrom: string, dateTo: string, schedules: readonly unknown[]) {
  return {
    experienceId: EXPERIENCE_ID,
    title: summary.title,
    timezone: 'America/Bogota',
    currency: 'COP',
    unitPriceCop: 80000,
    maxCapacityPerSlot: 12,
    dateFrom,
    dateTo,
    schedules,
  };
}

/** Respuestas comunes del catálogo público. */
export const catalogRoutes = {
  '/v1/experiences': page([summary]),
  '/v1/catalog/categories': categories,
  '/v1/catalog/departments': departments,
  '/v1/catalog/departments/05/municipalities': municipalities,
  [`/v1/experiences/${EXPERIENCE_ID}`]: detail,
};

/**
 * JWT con la forma y longitud de un voucher real (~650 caracteres, RS256): header, claims y una firma
 * de 342 caracteres base64url. No es una credencial válida; solo sirve para dibujar y leer el QR.
 */
export const VOUCHER_TOKEN_SAMPLE = [
  'eyJhbGciOiJSUzI1NiIsImtpZCI6InZvdWNoZXItMjAyNi0xMCIsInR5cCI6InZvdWNoZXIrand0In0',
  'eyJpc3MiOiJpbnRlZ3JhdGUiLCJqdGkiOiIwMDAwMDAwMC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDAiLCJiaWQiOiJiay0xIiwic2lkIjoiYiIsInhpZCI6IjhmOGIzYzFjIiwidmQiOiIyMDI2LTEwLTI5IiwiZG9jIjoiQ0M6MTA5NDAwMDExMSIsInNsb3RzIjoyLCJpYXQiOjE3OTE1OTAwMDAsImV4cCI6MTc5MjY4NDAwMH0',
  'x'.repeat(342),
].join('.');
