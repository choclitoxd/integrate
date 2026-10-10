import { describe, expect, it } from 'vitest';

import {
  fieldErrorsOf,
  loginSchema,
  normalizePhone,
  registerSchema,
  serverFieldErrors,
} from './forms';
import { safeReturnPath } from './returnPath';

const valid = {
  fullName: 'Carlos Restrepo',
  docType: 'CC',
  docNumber: '1094857483',
  phone: '300 123 4567',
  email: 'turista@ejemplo.com',
  password: 'una frase larga',
  personalData: true,
  healthEmergency: false,
} as const;

describe('formularios de autenticación', () => {
  it('acepta un registro válido y normaliza el celular a E.164', () => {
    const result = registerSchema.safeParse(valid);
    expect(result.success && result.data.phone).toBe('+573001234567');
    expect(normalizePhone('(+57) 300-123-4567')).toBe('+573001234567');
  });

  it('aplica las restricciones del contrato con mensajes por campo', () => {
    const result = registerSchema.safeParse({
      ...valid,
      docNumber: '12a',
      password: 'corta',
      email: 'sin-arroba',
      personalData: false,
    });
    expect(result.success).toBe(false);
    const errors = result.success ? {} : fieldErrorsOf(result.error.issues);
    expect(Object.keys(errors).sort()).toEqual(['docNumber', 'email', 'password', 'personalData']);
    expect(errors.password).toMatch(/12 caracteres/);
  });

  it('exige solo dígitos en cédula y tarjeta de identidad, pero no en pasaporte', () => {
    expect(registerSchema.safeParse({ ...valid, docNumber: 'AB12345' }).success).toBe(false);
    expect(
      registerSchema.safeParse({ ...valid, docType: 'PA', docNumber: 'AB12345' }).success,
    ).toBe(true);
  });

  it('valida el login sin imponer reglas de composición', () => {
    expect(loginSchema.safeParse({ email: ' a@b.co ', password: 'x' }).success).toBe(true);
    expect(loginSchema.safeParse({ email: '', password: '' }).success).toBe(false);
  });

  it('asigna los invalidParams del servidor a los campos', () => {
    const { fields, unmatched } = serverFieldErrors(
      [
        { name: 'email', reason: 'formato inválido' },
        { name: 'consent.purposes', reason: 'falta PERSONAL_DATA' },
        { name: 'otro', reason: 'x' },
      ],
      ['email', 'personalData'],
      { 'consent.purposes': 'personalData' },
    );
    expect(fields).toEqual({ email: 'formato inválido', personalData: 'falta PERSONAL_DATA' });
    expect(unmatched).toEqual([{ name: 'otro', reason: 'x' }]);
  });

  it('solo vuelve a rutas internas', () => {
    expect(safeReturnPath('/experiencias/1/franjas?x=1')).toBe('/experiencias/1/franjas?x=1');
    expect(safeReturnPath('https://malicioso.co')).toBe('/');
    expect(safeReturnPath('//malicioso.co')).toBe('/');
    expect(safeReturnPath('/\\malicioso.co')).toBe('/');
    expect(safeReturnPath('/ingresar?volver=/cuenta')).toBe('/');
    expect(safeReturnPath(null)).toBe('/');
  });
});
