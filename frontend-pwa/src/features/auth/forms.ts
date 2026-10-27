import { z } from 'zod/mini';
import type { $ZodIssue } from 'zod/v4/core';

import type { InvalidParam } from '@/shared/api';
import { GENERIC_PHONE_MESSAGE, phoneProblem } from '@/shared/lib';

/**
 * Esquemas de formulario escritos desde las restricciones de `openapi.yaml` (ADR-0013 §4).
 * Los mensajes dicen qué corregir, en español y sin jerga.
 */

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const DOC_NUMBER = /^[A-Za-z0-9]{4,20}$/;

const email = z
  .string()
  .check(
    z.trim(),
    z.minLength(1, 'Escribe tu correo.'),
    z.maxLength(254, 'El correo es demasiado largo.'),
    z.regex(EMAIL, 'Revisa el correo: debe tener la forma nombre@dominio.co.'),
  );

export const loginSchema = z.object({
  email,
  password: z
    .string()
    .check(
      z.minLength(1, 'Escribe tu contraseña.'),
      z.maxLength(128, 'La contraseña es demasiado larga.'),
    ),
});

export const DOC_TYPES = [
  { value: 'CC', label: 'Cédula de ciudadanía' },
  { value: 'CE', label: 'Cédula de extranjería' },
  { value: 'PA', label: 'Pasaporte' },
  { value: 'PPT', label: 'Permiso por protección temporal (PPT)' },
  { value: 'TI', label: 'Tarjeta de identidad' },
] as const;

/** Solo dígitos en CC y TI: así abre el teclado numérico y se evita un error del servidor. */
export const NUMERIC_DOC_TYPES: readonly string[] = ['CC', 'TI'];

/** "300 123 4567" o "(+57) 300-123-4567" → "+573001234567". Un celular de 10 dígitos asume +57. */
export function normalizePhone(raw: string): string {
  const compact = raw.replace(/[\s()-]/g, '');
  if (/^3\d{9}$/.test(compact)) return `+57${compact}`;
  return compact;
}

/**
 * Celular del registro, del enrolamiento y del contacto de emergencia: se normaliza a E.164 y se
 * exige el largo del país elegido (10 dígitos en Colombia, 9 en Ecuador…); un indicativo que no está
 * en la lista solo debe cumplir el formato E.164 del backend.
 */
export const phoneSchema = z.pipe(
  z.pipe(z.string(), z.transform(normalizePhone)),
  z.string().check(
    z.refine((value) => phoneProblem(value) === null, {
      error: (issue) => phoneProblem(String(issue.input)) ?? GENERIC_PHONE_MESSAGE,
    }),
  ),
);

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .check(
        z.trim(),
        z.minLength(3, 'Escribe tu nombre completo.'),
        z.maxLength(150, 'El nombre es demasiado largo.'),
      ),
    docType: z.enum(['CC', 'CE', 'PA', 'PPT', 'TI'], { error: 'Elige el tipo de documento.' }),
    docNumber: z
      .string()
      .check(
        z.trim(),
        z.regex(
          DOC_NUMBER,
          'El número de documento lleva entre 4 y 20 letras o dígitos, sin espacios.',
        ),
      ),
    phone: phoneSchema,
    email,
    password: z
      .string()
      .check(
        z.minLength(
          12,
          'Usa al menos 12 caracteres. Una frase que recuerdes con facilidad funciona bien.',
        ),
        z.maxLength(128, 'La contraseña es demasiado larga.'),
      ),
    personalData: z.literal(true, {
      error: 'Para crear la cuenta debes autorizar el tratamiento de tus datos personales.',
    }),
    healthEmergency: z.boolean(),
  })
  .check(
    z.refine((form) => !NUMERIC_DOC_TYPES.includes(form.docType) || /^\d+$/.test(form.docNumber), {
      path: ['docNumber'],
      error: 'Este tipo de documento solo lleva números.',
    }),
  );

export type RegisterForm = z.input<typeof registerSchema>;
export type FieldErrors<K extends string> = Partial<Record<K, string>>;

/** Primer error de cada campo de un safeParse fallido. */
export function fieldErrorsOf<K extends string>(issues: readonly $ZodIssue[]): FieldErrors<K> {
  const errors: Partial<Record<string, string>> = {};
  for (const issue of issues) {
    const key = String(issue.path[0] ?? '');
    errors[key] ??= issue.message;
  }
  return errors;
}

/** Asigna los `invalidParams` de un 422 a los campos del formulario; el resto queda general. */
export function serverFieldErrors<K extends string>(
  params: readonly InvalidParam[],
  fields: readonly K[],
  aliases: Readonly<Record<string, K>> = {},
): { readonly fields: FieldErrors<K>; readonly unmatched: readonly InvalidParam[] } {
  const matched: Partial<Record<K, string>> = {};
  const unmatched: InvalidParam[] = [];
  for (const param of params) {
    const field = aliases[param.name] ?? fields.find((name) => name === param.name);
    if (field) matched[field] ??= param.reason;
    else unmatched.push(param);
  }
  return { fields: matched, unmatched };
}
