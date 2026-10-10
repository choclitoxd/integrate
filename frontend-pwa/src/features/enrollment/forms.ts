import { z } from 'zod/mini';

import {
  fieldErrorsOf,
  normalizePhone,
  NUMERIC_DOC_TYPES,
  type FieldErrors,
} from '@/features/auth';
import type { InvalidParam } from '@/shared/api';

import type { AssistedRegistrationRequest } from './api';

/**
 * Formulario de enrolamiento asistido (ADR-0018 §2), escrito desde las restricciones de
 * `AssistedHostRegistrationRequest`. Mismas reglas de documento y celular que el registro (auth).
 */

const DOC_NUMBER = /^[A-Za-z0-9]{4,20}$/;
const E164 = /^\+[1-9][0-9]{7,14}$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

export const MAX_PDF_BYTES = 5 * 1024 * 1024;
export const VICTIMIZATION_OTHER = 'OTRO';

/** Hechos victimizantes del Registro Único de Víctimas; se envían como texto (≤ 80, sin catálogo). */
export const VICTIMIZATION_TYPES = [
  { value: 'ABANDONO_DESPOJO_TIERRAS', label: 'Abandono o despojo forzado de tierras' },
  { value: 'ACTO_TERRORISTA', label: 'Acto terrorista, atentado, combate u hostigamiento' },
  { value: 'AMENAZA', label: 'Amenaza' },
  { value: 'CONFINAMIENTO', label: 'Confinamiento' },
  {
    value: 'DELITOS_LIBERTAD_INTEGRIDAD_SEXUAL',
    label: 'Delitos contra la libertad y la integridad sexual',
  },
  { value: 'DESAPARICION_FORZADA', label: 'Desaparición forzada' },
  { value: 'DESPLAZAMIENTO_FORZADO', label: 'Desplazamiento forzado' },
  { value: 'HOMICIDIO', label: 'Homicidio' },
  { value: 'LESIONES_FISICAS', label: 'Lesiones personales físicas' },
  { value: 'LESIONES_PSICOLOGICAS', label: 'Lesiones personales psicológicas' },
  { value: 'MINAS_MUNICION_SIN_EXPLOTAR', label: 'Minas antipersonal o munición sin explotar' },
  { value: 'PERDIDA_BIENES', label: 'Pérdida de bienes muebles o inmuebles' },
  { value: 'SECUESTRO', label: 'Secuestro' },
  { value: 'TORTURA', label: 'Tortura' },
  {
    value: 'VINCULACION_NNA',
    label: 'Vinculación de niños, niñas o adolescentes a grupos armados',
  },
] as const;

/** Archivo ya subido a S3: solo su llave y lo que se muestra; el contenido no se guarda. */
export interface UploadedDocument {
  readonly s3Key: string;
  readonly name: string;
  readonly size: number;
}

/** Estado de un PDF del formulario. */
export type DocumentState =
  | { readonly status: 'empty' }
  | { readonly status: 'uploading'; readonly name: string; readonly size: number }
  | ({ readonly status: 'ready' } & UploadedDocument);

export const EMPTY_DOCUMENT: DocumentState = { status: 'empty' };

export interface EnrollmentDraft {
  readonly fullName: string;
  readonly docType: string;
  readonly docNumber: string;
  readonly phone: string;
  readonly email: string;
  readonly victim: '' | 'yes' | 'no';
  readonly ruvRegistrationNumber: string;
  readonly victimizationType: string;
  readonly victimizationOther: string;
  readonly supportDoc: DocumentState;
  readonly personalData: boolean;
  readonly sensitiveVictim: boolean;
  readonly consentForm: DocumentState;
}

export type EnrollmentField = keyof EnrollmentDraft;

/** Orden de los campos en pantalla: el foco va al primero con error. */
export const ENROLLMENT_FIELDS: readonly EnrollmentField[] = [
  'fullName',
  'docType',
  'docNumber',
  'phone',
  'email',
  'victim',
  'ruvRegistrationNumber',
  'victimizationType',
  'victimizationOther',
  'supportDoc',
  'personalData',
  'sensitiveVictim',
  'consentForm',
];

export function emptyDraft(): EnrollmentDraft {
  return {
    fullName: '',
    docType: 'CC',
    docNumber: '',
    phone: '+57 ',
    email: '',
    victim: '',
    ruvRegistrationNumber: '',
    victimizationType: '',
    victimizationOther: '',
    supportDoc: EMPTY_DOCUMENT,
    personalData: false,
    sensitiveVictim: false,
    consentForm: EMPTY_DOCUMENT,
  };
}

/** Con "No" se vacían los datos de víctima: el servidor los rechaza sin la condición (ADR-0018 §2). */
export function withVictimAnswer(draft: EnrollmentDraft, answer: 'yes' | 'no'): EnrollmentDraft {
  if (answer === 'yes') return { ...draft, victim: 'yes' };
  return {
    ...draft,
    victim: 'no',
    ruvRegistrationNumber: '',
    victimizationType: '',
    victimizationOther: '',
    supportDoc: EMPTY_DOCUMENT,
  };
}

const STILL_UPLOADING = 'Espera a que el archivo termine de subir.';
const optionalDocument = z
  .custom<DocumentState>()
  .check(z.refine((doc) => doc.status !== 'uploading', STILL_UPLOADING));
const requiredDocument = (missing: string) =>
  z.custom<DocumentState>().check(
    z.refine((doc) => doc.status !== 'uploading', STILL_UPLOADING),
    z.refine((doc) => doc.status !== 'empty', missing),
  );

const enrollmentSchema = z
  .object({
    fullName: z
      .string()
      .check(
        z.trim(),
        z.minLength(3, 'Escribe el nombre completo del anfitrión.'),
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
    phone: z.pipe(
      z.pipe(z.string(), z.transform(normalizePhone)),
      z.string().check(z.regex(E164, 'Escribe un celular válido, por ejemplo +57 300 123 4567.')),
    ),
    email: z.string().check(
      z.trim(),
      z.maxLength(254, 'El correo es demasiado largo.'),
      z.refine(
        (value) => value === '' || EMAIL.test(value),
        'Revisa el correo: debe tener la forma nombre@dominio.co, o déjalo vacío.',
      ),
    ),
    victim: z.enum(['yes', 'no'], { error: 'Indica si la persona es víctima del conflicto.' }),
    ruvRegistrationNumber: z
      .string()
      .check(z.trim(), z.maxLength(40, 'El número RUV lleva máximo 40 caracteres.')),
    victimizationType: z.string(),
    victimizationOther: z
      .string()
      .check(z.trim(), z.maxLength(80, 'Describe el hecho en máximo 80 caracteres.')),
    supportDoc: optionalDocument,
    personalData: z.literal(true, {
      error: 'Sin esta autorización no se puede registrar al anfitrión.',
    }),
    sensitiveVictim: z.literal(true, {
      error: 'Sin esta autorización no se puede registrar al anfitrión.',
    }),
    consentForm: requiredDocument('Sube el formato de consentimiento firmado, en PDF.'),
  })
  .check(
    z.refine((form) => !NUMERIC_DOC_TYPES.includes(form.docType) || /^\d+$/.test(form.docNumber), {
      path: ['docNumber'],
      error: 'Este tipo de documento solo lleva números.',
    }),
    z.refine(
      (form) =>
        form.victimizationType !== VICTIMIZATION_OTHER || form.victimizationOther.length > 0,
      { path: ['victimizationOther'], error: 'Describe el hecho victimizante.' },
    ),
  );

export type ValidationResult =
  | { readonly ok: true; readonly body: AssistedRegistrationRequest }
  | { readonly ok: false; readonly errors: FieldErrors<EnrollmentField> };

/** Valida el borrador y arma el cuerpo de `register-assisted` sin los campos vacíos. */
export function validateEnrollment(
  draft: EnrollmentDraft,
  policyVersion: string,
): ValidationResult {
  const parsed = enrollmentSchema.safeParse(draft);
  if (!parsed.success) {
    return { ok: false, errors: fieldErrorsOf<EnrollmentField>(parsed.error.issues) };
  }
  const form = parsed.data;
  const victim = form.victim === 'yes';
  const victimizationType =
    form.victimizationType === VICTIMIZATION_OTHER
      ? form.victimizationOther
      : form.victimizationType;
  const support = form.supportDoc.status === 'ready' ? form.supportDoc.s3Key : '';
  const consentKey = form.consentForm.status === 'ready' ? form.consentForm.s3Key : '';
  return {
    ok: true,
    body: {
      fullName: form.fullName,
      docType: form.docType,
      docNumber: form.docNumber,
      phone: form.phone,
      ...(form.email !== '' && { email: form.email }),
      victimData: victim
        ? {
            victimCondition: true,
            ...(form.ruvRegistrationNumber !== '' && {
              ruvRegistrationNumber: form.ruvRegistrationNumber,
            }),
            ...(victimizationType !== '' && { victimizationType }),
            ...(support !== '' && { supportDocS3Key: support }),
          }
        : { victimCondition: false },
      consent: {
        policyVersion,
        purposes: ['PERSONAL_DATA', 'SENSITIVE_VICTIM'],
        physicalConsentFormS3Key: consentKey,
      },
    },
  };
}

const SIZE = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });

/** 1_258_291 → "1,2 MB"; 350_000 → "342 kB". */
export function formatFileSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${SIZE.format(bytes / (1024 * 1024))} MB`;
  return `${SIZE.format(Math.max(1, Math.round(bytes / 1024)))} kB`;
}

/** Validación local del PDF antes de pedir la URL de subida. */
export function pdfProblem(file: File): string | null {
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  if (!isPdf) return 'Ese archivo no es un PDF. Escanéalo como PDF y vuelve a intentarlo.';
  if (file.size === 0) return 'El archivo está vacío. Elige otro.';
  if (file.size > MAX_PDF_BYTES) return 'El PDF pesa más de 5 MB. Escanéalo en menor calidad.';
  return null;
}

const SERVER_ALIASES: Readonly<Record<string, EnrollmentField>> = {
  'victimData.ruvRegistrationNumber': 'ruvRegistrationNumber',
  'victimData.victimizationType': 'victimizationType',
  'victimData.supportDocS3Key': 'supportDoc',
  'consent.physicalConsentFormS3Key': 'consentForm',
  'consent.purposes': 'personalData',
};

const DOCUMENT_REUPLOAD = 'El servidor no aceptó este archivo. Súbelo de nuevo.';

/**
 * `invalidParams` de un 422 por campo, en español: el motivo del servidor es técnico. Un PDF
 * rechazado (vencido, ya usado, no PDF) pide subirlo otra vez.
 */
export function serverErrorsOf(params: readonly InvalidParam[]): {
  readonly errors: FieldErrors<EnrollmentField>;
  readonly unmatched: number;
} {
  const errors: Partial<Record<EnrollmentField, string>> = {};
  let unmatched = 0;
  for (const param of params) {
    const field =
      SERVER_ALIASES[param.name] ?? ENROLLMENT_FIELDS.find((name) => name === param.name);
    if (!field) {
      unmatched += 1;
      continue;
    }
    errors[field] ??=
      field === 'supportDoc' || field === 'consentForm'
        ? DOCUMENT_REUPLOAD
        : 'Revisa este dato: el servidor no lo aceptó.';
  }
  return { errors, unmatched };
}
