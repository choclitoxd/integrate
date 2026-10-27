import { z } from 'zod/mini';
import type { $ZodIssue } from 'zod/v4/core';

import { NUMERIC_DOC_TYPES, phoneSchema } from '@/features/auth';
import type { InvalidParam } from '@/shared/api';

import type { BookingLockRequest } from './api';

/**
 * Esquema del formulario de reserva desde `openapi.yaml` y ADR-0008 §8 (ADR-0015 §4). Los nombres
 * se recortan antes de medir porque el backend valida con `btrim`.
 */

const DOC_NUMBER = /^[A-Za-z0-9]{4,20}$/;

const fullName = z
  .string()
  .check(
    z.trim(),
    z.minLength(3, 'Escribe el nombre completo.'),
    z.maxLength(150, 'El nombre es demasiado largo.'),
  );

const participant = z
  .object({
    fullName,
    docType: z.enum(['CC', 'CE', 'PA', 'PPT', 'TI'], { error: 'Elige el tipo de documento.' }),
    docNumber: z
      .string()
      .check(z.trim(), z.regex(DOC_NUMBER, 'Entre 4 y 20 letras o dígitos, sin espacios.')),
    medicalEps: z
      .string()
      .check(
        z.trim(),
        z.minLength(2, 'Escribe la EPS o el régimen de salud.'),
        z.maxLength(120, 'El nombre de la EPS es demasiado largo.'),
      ),
  })
  .check(
    z.refine((p) => !NUMERIC_DOC_TYPES.includes(p.docType) || /^\d+$/.test(p.docNumber), {
      path: ['docNumber'],
      error: 'Este tipo de documento solo lleva números.',
    }),
  );

export const bookingFormSchema = z
  .object({
    participants: z.array(participant),
    emergencyName: fullName,
    emergencyPhone: phoneSchema,
  })
  .check(
    z.superRefine((form, ctx) => {
      // (docType, docNumber) único en el grupo, sin distinguir mayúsculas (ADR-0008 §8).
      const seen = new Set<string>();
      form.participants.forEach((p, index) => {
        const key = `${p.docType}:${p.docNumber.toUpperCase()}`;
        if (seen.has(key)) {
          ctx.addIssue({
            code: 'custom',
            path: ['participants', index, 'docNumber'],
            message: 'Este documento ya está en otro viajero del grupo.',
            input: p.docNumber,
          });
        }
        seen.add(key);
      });
    }),
  );

export type BookingFormInput = z.input<typeof bookingFormSchema>;
export type BookingForm = z.output<typeof bookingFormSchema>;
export type TravelerDraft = BookingFormInput['participants'][number];

export function emptyTraveler(fullNameValue = ''): TravelerDraft {
  return { fullName: fullNameValue, docType: 'CC', docNumber: '', medicalEps: '' };
}

/** Cuerpo del lock: el primer viajero es el titular. */
export function toLockRequest(scheduleId: string, form: BookingForm): BookingLockRequest {
  return {
    scheduleId,
    requestedSlots: form.participants.length,
    emergencyContact: { fullName: form.emergencyName, phone: form.emergencyPhone },
    participants: form.participants.map((p, index) => ({ ...p, isHolder: index === 0 })),
  };
}

/** Errores por ruta: "participants.0.docNumber", "emergencyPhone"… (el primero de cada campo). */
export function errorsByPath(issues: readonly $ZodIssue[]): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const issue of issues) {
    const key = issue.path.map(String).join('.');
    errors[key] ??= issue.message;
  }
  return errors;
}

const SERVER_ALIASES: Readonly<Record<string, string>> = {
  'emergencyContact.fullName': 'emergencyName',
  'emergencyContact.phone': 'emergencyPhone',
};

/** `participants[1].docNumber` (422 del servidor) → `participants.1.docNumber`. */
export function serverErrorsByPath(params: readonly InvalidParam[]): {
  readonly fields: Record<string, string>;
  readonly unmatched: readonly InvalidParam[];
} {
  const fields: Record<string, string> = {};
  const unmatched: InvalidParam[] = [];
  for (const param of params) {
    const normalized = param.name.replace(/\[(\d+)\]/g, '.$1');
    const key = SERVER_ALIASES[normalized] ?? normalized;
    if (
      /^participants\.\d+\.\w+$/.test(key) ||
      key === 'emergencyName' ||
      key === 'emergencyPhone'
    ) {
      fields[key] ??= param.reason;
    } else {
      unmatched.push(param);
    }
  }
  return { fields, unmatched };
}
