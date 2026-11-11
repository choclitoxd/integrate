import { z } from 'zod/mini';

import { fieldErrorsOf, phoneSchema, type DocType, type FieldErrors } from '@/features/auth';
import type { InvalidParam } from '@/shared/api';

import type {
  AdminExperience,
  AdminUserUpdateRequest,
  Association,
  AssociationRequest,
  ExperienceUpdateRequest,
  UserCreateRequest,
  UserCriterion,
} from './api';

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

export function fromAssociation(association: Association): AssociationDraft {
  return {
    legalName: association.legalName,
    nit: association.nit,
    rntNumber: association.rntNumber,
    associationType: association.associationType,
    municipalityCode: association.municipalityCode,
    address: association.address ?? '',
    contactEmail: association.contactEmail,
    contactPhone: association.contactPhone,
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

const DOCUMENT = /^[A-Za-z0-9]{4,20}$/;

/** Correo exacto para la búsqueda de personas: `null` si es válido (ADR-0043 §1.1). */
export function emailSearchProblem(email: string): string | null {
  const text = email.trim();
  if (text.length === 0) return 'Escribe el correo completo de la persona.';
  if (text.length > 254 || !EMAIL.test(text)) {
    return 'Revisa el correo: debe tener la forma nombre@dominio.co.';
  }
  return null;
}

/** Número de documento exacto para la búsqueda de personas: `null` si es válido. */
export function documentSearchProblem(document: string): string | null {
  const text = document.trim();
  if (text.length === 0) return 'Escribe el número de documento completo.';
  if (!DOCUMENT.test(text)) {
    return 'El número de documento lleva entre 4 y 20 letras o dígitos, sin espacios.';
  }
  return null;
}

/**
 * Interpreta una búsqueda unificada de persona (correo o documento sin obligar a cambiar de filtro):
 * si contiene dígitos y no lleva `@`, se valida como documento; en caso contrario, como correo.
 */
export function parsePersonSearchQuery(
  raw: string,
  docType: DocType = 'CC',
):
  | { readonly ok: true; readonly criterion: UserCriterion }
  | { readonly ok: false; readonly error: string } {
  const text = raw.trim();
  if (text.length === 0) {
    return { ok: false, error: 'Escribe el correo o el número de documento completo.' };
  }
  const isDocument = !text.includes('@') && /\d/.test(text);
  if (isDocument) {
    const problem = documentSearchProblem(text);
    if (problem) return { ok: false, error: problem };
    return { ok: true, criterion: { kind: 'document', docType, document: text } };
  }
  const problem = emailSearchProblem(text);
  if (problem) return { ok: false, error: problem };
  return { ok: true, criterion: { kind: 'email', email: text } };
}

/** Fecha de acreditación: obligatoria y no futura (`today` es el día civil de Bogotá). */
export function accreditedOnProblem(date: string, today: string): string | null {
  if (date === '') return 'Elige la fecha de acreditación.';
  if (date > today) return 'La fecha de acreditación no puede ser futura.';
  return null;
}

export const REASON_MAX = 1000;

/** Motivo obligatorio de rechazo, suspensión o retiro: `null` si es válido. */
export function reasonProblem(reason: string): string | null {
  const text = reason.trim();
  if (text.length === 0) return 'Escribe el motivo: queda en el historial y lo lee el anfitrión.';
  if (text.length > REASON_MAX) return 'El motivo es demasiado largo (máximo 1000 caracteres).';
  return null;
}

/* ---------------------------------------------------------------- edición administrativa de experiencias (ADR-0047 §1) */

export interface ExperienceDraft {
  readonly title: string;
  readonly description: string;
  readonly safetyGuidelines: string;
  readonly physicalIntensity: 'LOW' | 'MEDIUM' | 'HIGH';
  readonly durationMinutes: string;
  readonly municipalityCode: string;
  readonly vereda: string;
  readonly meetingPoint: string;
  readonly latitude: string;
  readonly longitude: string;
  readonly maxCapacityPerSlot: string;
  readonly unitPriceCop: string;
}

export type ExperienceField = keyof ExperienceDraft;

export const EXPERIENCE_FIELDS: readonly ExperienceField[] = [
  'title',
  'unitPriceCop',
  'maxCapacityPerSlot',
  'durationMinutes',
  'physicalIntensity',
  'municipalityCode',
  'vereda',
  'meetingPoint',
  'latitude',
  'longitude',
  'description',
  'safetyGuidelines',
];

export function fromAdminExperience(experience: AdminExperience): ExperienceDraft {
  return {
    title: experience.title,
    description: experience.description,
    safetyGuidelines: experience.safetyGuidelines ?? '',
    physicalIntensity: experience.physicalIntensity,
    durationMinutes: String(experience.durationMinutes),
    municipalityCode: experience.municipalityCode,
    vereda: experience.vereda ?? '',
    meetingPoint: experience.meetingPoint ?? '',
    latitude: experience.latitude !== null ? String(experience.latitude) : '',
    longitude: experience.longitude !== null ? String(experience.longitude) : '',
    maxCapacityPerSlot: String(experience.maxCapacityPerSlot),
    unitPriceCop: String(experience.unitPriceCop),
  };
}

export function emptyExperienceDraft(): ExperienceDraft {
  return {
    title: '',
    description: '',
    safetyGuidelines: '',
    physicalIntensity: 'MEDIUM',
    durationMinutes: '240',
    municipalityCode: '',
    vereda: '',
    meetingPoint: '',
    latitude: '',
    longitude: '',
    maxCapacityPerSlot: '10',
    unitPriceCop: '',
  };
}

/** Datos que la edición conserva tal cual de la experiencia original (no tienen campo en el formulario). */
export type ExperienceBase = Pick<
  AdminExperience,
  'categoryCodes' | 'routeType' | 'distanceKm' | 'roundTripMinutes' | 'stops'
>;

export type ExperienceValidation =
  | { readonly ok: true; readonly body: ExperienceUpdateRequest }
  | { readonly ok: false; readonly errors: FieldErrors<ExperienceField> };

export function validateAdminExperience(
  draft: ExperienceDraft,
  base: ExperienceBase | null,
): ExperienceValidation {
  const errors: Partial<Record<ExperienceField, string>> = {};
  const title = draft.title.trim();
  if (title.length < 5) errors.title = 'Escribe el título (mínimo 5 letras).';
  else if (title.length > 200) errors.title = 'El título es demasiado largo (máximo 200 letras).';

  const description = draft.description.trim();
  if (description.length === 0) errors.description = 'Escribe la descripción de la experiencia.';
  else if (description.length > 10_000) errors.description = 'La descripción es demasiado larga.';

  const safetyGuidelines = draft.safetyGuidelines.trim();
  if (safetyGuidelines.length === 0) {
    errors.safetyGuidelines = 'Escribe las recomendaciones de seguridad.';
  } else if (safetyGuidelines.length > 5_000) {
    errors.safetyGuidelines = 'Las recomendaciones son demasiado largas.';
  }

  const durationMinutes = Number.parseInt(draft.durationMinutes.trim(), 10);
  if (!Number.isInteger(durationMinutes) || durationMinutes < 15 || durationMinutes > 4320) {
    errors.durationMinutes = 'La duración debe estar entre 15 y 4320 minutos.';
  }

  const municipalityCode = draft.municipalityCode.trim();
  if (!/^[0-9]{5}$/.test(municipalityCode)) {
    errors.municipalityCode = 'Elige el municipio.';
  }

  const vereda = draft.vereda.trim();
  if (vereda.length > 150) errors.vereda = 'La vereda es demasiado larga.';

  const meetingPoint = draft.meetingPoint.trim();
  if (meetingPoint.length === 0) errors.meetingPoint = 'Escribe el punto de encuentro.';
  else if (meetingPoint.length > 500) {
    errors.meetingPoint = 'El punto de encuentro es demasiado largo.';
  }

  const latRaw = draft.latitude.trim();
  const lngRaw = draft.longitude.trim();
  let latitude: number | null = null;
  let longitude: number | null = null;
  if ((latRaw === '') !== (lngRaw === '')) {
    errors.latitude = 'Indica latitud y longitud juntas, o deja ambas vacías.';
  } else if (latRaw !== '' && lngRaw !== '') {
    const parsedLat = Number(latRaw);
    const parsedLng = Number(lngRaw);
    if (!Number.isFinite(parsedLat) || parsedLat < -90 || parsedLat > 90) {
      errors.latitude = 'La latitud debe estar entre -90 y 90.';
    } else {
      latitude = parsedLat;
    }
    if (!Number.isFinite(parsedLng) || parsedLng < -180 || parsedLng > 180) {
      errors.longitude = 'La longitud debe estar entre -180 y 180.';
    } else {
      longitude = parsedLng;
    }
  }

  const maxCapacityPerSlot = Number.parseInt(draft.maxCapacityPerSlot.trim(), 10);
  if (!Number.isInteger(maxCapacityPerSlot) || maxCapacityPerSlot < 1 || maxCapacityPerSlot > 30) {
    errors.maxCapacityPerSlot = 'El cupo debe estar entre 1 y 30 personas.';
  }

  const unitPriceCop = Number.parseInt(draft.unitPriceCop.trim(), 10);
  if (!Number.isInteger(unitPriceCop) || unitPriceCop <= 0) {
    errors.unitPriceCop = 'Escribe un precio por persona mayor a cero.';
  }

  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }

  return {
    ok: true,
    body: {
      title,
      description,
      safetyGuidelines,
      physicalIntensity: draft.physicalIntensity,
      durationMinutes,
      municipalityCode,
      ...(vereda !== '' && { vereda }),
      meetingPoint,
      ...(latitude !== null && longitude !== null && { latitude, longitude }),
      maxCapacityPerSlot,
      unitPriceCop,
      ...(base !== null && {
        categoryCodes: base.categoryCodes,
        ...(base.routeType !== undefined && { routeType: base.routeType }),
        ...(base.distanceKm !== undefined && { distanceKm: base.distanceKm }),
        ...(base.roundTripMinutes !== undefined && { roundTripMinutes: base.roundTripMinutes }),
        stops: base.stops,
      }),
    },
  };
}

export function experienceServerErrors(
  params: readonly InvalidParam[],
): FieldErrors<ExperienceField> {
  const errors: Partial<Record<ExperienceField, string>> = {};
  for (const param of params) {
    const field = EXPERIENCE_FIELDS.find((name) => name === param.name);
    if (field) errors[field] ??= 'Revisa este dato: el servidor no lo aceptó.';
  }
  return errors;
}

/* ------------------------------------------------------------------------ alta de usuario (ADR-0048 §2) */

export interface UserDraft {
  readonly fullName: string;
  readonly docType: DocType;
  readonly docNumber: string;
  readonly phone: string;
  readonly email: string;
  readonly password: string;
}

export type UserField = keyof UserDraft;

/** Orden en pantalla: el foco va al primer campo con error. */
export const USER_FIELDS: readonly UserField[] = [
  'fullName',
  'docType',
  'docNumber',
  'phone',
  'email',
  'password',
];

export function emptyUserDraft(): UserDraft {
  return { fullName: '', docType: 'CC', docNumber: '', phone: '+57 ', email: '', password: '' };
}

export type UserValidation =
  | { readonly ok: true; readonly body: UserCreateRequest }
  | { readonly ok: false; readonly errors: FieldErrors<UserField> };

export function validateUserDraft(draft: UserDraft): UserValidation {
  const errors: Partial<Record<UserField, string>> = {};
  const fullName = draft.fullName.trim();
  if (fullName.length < 3 || fullName.length > 150) {
    errors.fullName = 'Escribe un nombre de 3 a 150 caracteres.';
  }
  const docNumber = draft.docNumber.trim();
  if (!DOCUMENT.test(docNumber)) {
    errors.docNumber = 'El número de documento lleva entre 4 y 20 letras o dígitos, sin espacios.';
  }
  const phone = phoneSchema.safeParse(draft.phone);
  if (!phone.success) {
    errors.phone = phone.error.issues[0]?.message ?? 'Revisa el celular.';
  }
  const email = draft.email.trim();
  if (email.length > 254 || !EMAIL.test(email)) {
    errors.email = 'Revisa el correo: debe tener la forma nombre@dominio.co.';
  }
  const passwordLength = Array.from(draft.password).length;
  if (passwordLength < 12 || passwordLength > 128) {
    errors.password = 'La contraseña lleva entre 12 y 128 caracteres.';
  }
  if (Object.keys(errors).length > 0 || !phone.success) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    body: {
      email,
      password: draft.password,
      fullName,
      docType: draft.docType,
      docNumber,
      phone: phone.data,
    },
  };
}

/** `invalidParams` de un 422 por campo, en español: el motivo del servidor es técnico. */
export function userServerErrors(params: readonly InvalidParam[]): FieldErrors<UserField> {
  const errors: Partial<Record<UserField, string>> = {};
  for (const param of params) {
    const field = USER_FIELDS.find((name) => name === param.name);
    if (field) errors[field] ??= 'Revisa este dato: el servidor no lo aceptó.';
  }
  return errors;
}

/* ------------------------------------------------------------------------ edición de usuario (ADR-0057 §1) */

export interface UserEditDraft {
  readonly fullName: string;
  readonly docType: DocType;
  readonly docNumber: string;
  readonly phone: string;
}

export type UserEditField = keyof UserEditDraft;

export const USER_EDIT_FIELDS: readonly UserEditField[] = [
  'fullName',
  'docType',
  'docNumber',
  'phone',
];

export type UserEditValidation =
  | { readonly ok: true; readonly body: AdminUserUpdateRequest }
  | { readonly ok: false; readonly errors: FieldErrors<UserEditField> };

export function validateUserEditDraft(draft: UserEditDraft): UserEditValidation {
  const errors: Partial<Record<UserEditField, string>> = {};
  const fullName = draft.fullName.trim();
  if (fullName.length < 3 || fullName.length > 150) {
    errors.fullName = 'Escribe un nombre de 3 a 150 caracteres.';
  }
  const docNumber = draft.docNumber.trim();
  if (!DOCUMENT.test(docNumber)) {
    errors.docNumber = 'El número de documento lleva entre 4 y 20 letras o dígitos, sin espacios.';
  }
  const phone = phoneSchema.safeParse(draft.phone);
  if (!phone.success) {
    errors.phone = phone.error.issues[0]?.message ?? 'Revisa el celular.';
  }
  if (Object.keys(errors).length > 0 || !phone.success) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    body: {
      fullName,
      docType: draft.docType,
      docNumber,
      phone: phone.data,
    },
  };
}

export function userEditServerErrors(params: readonly InvalidParam[]): FieldErrors<UserEditField> {
  const errors: Partial<Record<UserEditField, string>> = {};
  for (const param of params) {
    const field = USER_EDIT_FIELDS.find((name) => name === param.name);
    if (field) errors[field] ??= 'Revisa este dato: el servidor no lo aceptó.';
  }
  return errors;
}
