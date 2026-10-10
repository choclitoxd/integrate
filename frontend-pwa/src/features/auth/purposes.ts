import type { ConsentPurpose } from '@/shared/session';

import type { ConsentState } from './api';

/** Finalidades del tratamiento explicadas en lenguaje llano (Ley 1581, autorización informada). */
export const PURPOSE_COPY: Readonly<Record<ConsentPurpose, { title: string; body: string }>> = {
  PERSONAL_DATA: {
    title: 'Mis datos personales',
    body: 'Nombre, documento, correo y celular, para crear tu cuenta, gestionar tus reservas y enviarte el voucher.',
  },
  HEALTH_EMERGENCY: {
    title: 'Datos de salud y contacto de emergencia',
    body: 'EPS y contacto de emergencia de quienes viajan, solo para la póliza de turismo y para atender una emergencia en el recorrido. Es necesario para reservar.',
  },
  SENSITIVE_VICTIM: {
    title: 'Condición de víctima del conflicto armado',
    body: 'Dato sensible (Ley 1448) que solo usan las asociaciones comunitarias para su registro como anfitriones.',
  },
};

export const CONSENT_STATE_LABEL: Readonly<Record<ConsentState, string>> = {
  VALID: 'Autorizado',
  OUTDATED: 'Debe renovarse',
  REVOKED: 'Revocado',
  MISSING: 'Sin autorizar',
};
