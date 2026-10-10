import { describe, expect, it } from 'vitest';

import {
  bookingFormSchema,
  errorsByPath,
  serverErrorsByPath,
  toLockRequest,
  type BookingFormInput,
} from './forms';
import { createIntentKeys } from './intentKey';

const form: BookingFormInput = {
  participants: [
    { fullName: ' Ana Gómez ', docType: 'CC', docNumber: '1094000111', medicalEps: 'Sura' },
    { fullName: 'Luis Gómez', docType: 'TI', docNumber: '1094000222', medicalEps: 'Sanitas' },
  ],
  emergencyName: 'Carmen Ruiz',
  emergencyPhone: '300 765 4321',
};

describe('formulario de reserva', () => {
  it('arma el cuerpo del lock con el primer viajero como titular', () => {
    const parsed = bookingFormSchema.safeParse(form);
    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(toLockRequest('sch-1', parsed.data)).toEqual({
      scheduleId: 'sch-1',
      requestedSlots: 2,
      emergencyContact: { fullName: 'Carmen Ruiz', phone: '+573007654321' },
      participants: [
        {
          fullName: 'Ana Gómez',
          docType: 'CC',
          docNumber: '1094000111',
          medicalEps: 'Sura',
          isHolder: true,
        },
        {
          fullName: 'Luis Gómez',
          docType: 'TI',
          docNumber: '1094000222',
          medicalEps: 'Sanitas',
          isHolder: false,
        },
      ],
    });
  });

  it('rechaza documentos repetidos en el grupo sin distinguir mayúsculas', () => {
    const parsed = bookingFormSchema.safeParse({
      ...form,
      participants: [
        { fullName: 'Ana', docType: 'PA', docNumber: 'ab1234', medicalEps: 'Sura' },
        { fullName: 'Luis', docType: 'PA', docNumber: 'AB1234', medicalEps: 'Sura' },
      ],
    });
    expect(parsed.success).toBe(false);
    if (parsed.success) return;
    expect(errorsByPath(parsed.error.issues)).toMatchObject({
      'participants.1.docNumber': 'Este documento ya está en otro viajero del grupo.',
    });
  });

  it('exige nombre de 3 caracteres sin espacios externos, EPS y celular válidos', () => {
    const parsed = bookingFormSchema.safeParse({
      participants: [{ fullName: '  Al  ', docType: 'CC', docNumber: '12ab', medicalEps: 'S' }],
      emergencyName: 'Carmen',
      emergencyPhone: '123',
    });
    expect(parsed.success).toBe(false);
    if (parsed.success) return;
    expect(Object.keys(errorsByPath(parsed.error.issues)).sort()).toEqual([
      'emergencyPhone',
      'participants.0.docNumber',
      'participants.0.fullName',
      'participants.0.medicalEps',
    ]);
  });

  it('traduce los invalidParams del servidor a las rutas del formulario', () => {
    expect(
      serverErrorsByPath([
        { name: 'participants[1].medicalEps', reason: 'muy corta' },
        { name: 'emergencyContact.phone', reason: 'no es E.164' },
        { name: 'requestedSlots', reason: 'x' },
      ]),
    ).toEqual({
      fields: { 'participants.1.medicalEps': 'muy corta', emergencyPhone: 'no es E.164' },
      unmatched: [{ name: 'requestedSlots', reason: 'x' }],
    });
  });
});

describe('clave de idempotencia por intención', () => {
  it('repite la clave con el mismo cuerpo y la cambia si el cuerpo cambia', () => {
    let n = 0;
    const keys = createIntentKeys(() => `k-${String(++n)}`);
    expect(keys.keyFor({ a: 1 })).toBe('k-1');
    expect(keys.keyFor({ a: 1 })).toBe('k-1');
    expect(keys.keyFor({ a: 2 })).toBe('k-2');
    keys.reset();
    expect(keys.keyFor({ a: 2 })).toBe('k-3');
  });
});
