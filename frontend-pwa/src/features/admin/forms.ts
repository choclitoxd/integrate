import { z } from 'zod/mini';

import { fieldErrorsOf, phoneSchema, type FieldErrors } from '@/features/auth';
import type { InvalidParam } from '@/shared/api';

import type { AssociationRequest } from './api';

/** Formularios de la consola, escritos desde `AssociationRequest` y `ReasonRequest` del backend. */

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

export interface AssociationDraft {
  readonly legalName: string;
  readonly nit: string;
  readonly rntNumber: string;
  readonly associationType: string;
  readonly municipalityCode: string;
  readonly address: string;
  readonly contactEmail: string;
  readonly contactPhone: string;
}

export type AssociationField = keyof AssociationDraft;

/** Orden en pantalla: el foco va al primer campo con error. */
export const ASSOCIATION_FIELDS: readonly AssociationField[] = [
  'legalName',
  'nit',
  'rntNumber',
  'associationType',
  'municipalityCode',
  'address',
  'contactEmail',
  'contactPhone',
];

export function emptyAssociation(): AssociationDraft {
  return {
    legalName: '',
    nit: '',
    rntNumber: '',
    associationType: 'ASOCIACION_VICTIMAS',
    municipalityCode: '',
    address: '',
    contactEmail: '',
    contactPhone: '+57 ',
  };
}

const associationSchema = z.object({
  legalName: z
    .string()
    .check(
      z.trim(),
      z.minLength(3, 'Escribe la razón social (mínimo 3 letras).'),
      z.maxLength(200, 'La razón social es demasiado larga.'),
    ),
  nit: z
    .string()
    .check(
      z.trim(),
      z.regex(/^[0-9]{6,12}-[0-9]$/, 'El NIT lleva el dígito de verificación: 900123456-7.'),
    ),
  rntNumber: z
    .string()
    .check(z.trim(), z.regex(/^[0-9]{3,20}$/, 'El RNT lleva solo números (entre 3 y 20).')),
  associationType: z.enum(
    ['JUNTA_ACCION_COMUNAL', 'COOPERATIVA_CAMPESINA', 'ASOCIACION_VICTIMAS', 'OTRA'],
    { error: 'Elige el tipo de asociación.' },
  ),
  municipalityCode: z
    .string()
    .check(
      z.trim(),
      z.regex(/^[0-9]{5}$/, 'El código DANE del municipio tiene 5 dígitos, por ejemplo 05001.'),
    ),
  address: z.string().check(z.trim(), z.maxLength(250, 'La dirección es demasiado larga.')),
  contactEmail: z
    .string()
    .check(
      z.trim(),
      z.maxLength(254, 'El correo es demasiado largo.'),
      z.regex(EMAIL, 'Revisa el correo: debe tener la forma nombre@dominio.co.'),
    ),
  contactPhone: phoneSchema,
});

export type AssociationValidation =
  | { readonly ok: true; readonly body: AssociationRequest }
  | { readonly ok: false; readonly errors: FieldErrors<AssociationField> };

export function validateAssociation(draft: AssociationDraft): AssociationValidation {
  const parsed = associationSchema.safeParse(draft);
  if (!parsed.success) {
    return { ok: false, errors: fieldErrorsOf<AssociationField>(parsed.error.issues) };
  }
  const { address, ...rest } = parsed.data;
  return { ok: true, body: { ...rest, ...(address !== '' && { address }) } };
}

/** `invalidParams` de un 422 por campo, en español: el motivo del servidor es técnico. */
export function associationServerErrors(
  params: readonly InvalidParam[],
): FieldErrors<AssociationField> {
  const errors: Partial<Record<AssociationField, string>> = {};
  for (const param of params) {
    const field = ASSOCIATION_FIELDS.find((name) => name === param.name);
    if (field) errors[field] ??= 'Revisa este dato: el servidor no lo aceptó.';
  }
  return errors;
}

export const REASON_MAX = 1000;

/** Motivo obligatorio de rechazo, suspensión o retiro: `null` si es válido. */
export function reasonProblem(reason: string): string | null {
  const text = reason.trim();
  if (text.length === 0) return 'Escribe el motivo: queda en el historial y lo lee el anfitrión.';
  if (text.length > REASON_MAX) return 'El motivo es demasiado largo (máximo 1000 caracteres).';
  return null;
}
