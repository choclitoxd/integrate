import { z } from 'zod/mini';

import type { HttpClient } from '@/shared/api';

/** Activación de la cuenta de un anfitrión enrolado por un gestor (ADR-0007 §6, ADR-0018 §7). */

export interface ActivationRequest {
  readonly email: string;
  readonly code: string;
  readonly newPassword: string;
}

export function activateAccount(http: HttpClient, body: ActivationRequest): Promise<void> {
  return http.request<undefined>('/v1/auth/activate', { method: 'POST', body });
}

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** "7k3qd m9x2p" → "7K3QD-M9X2P". El servidor tolera ambas formas; se envía la canónica. */
export function normalizeActivationCode(raw: string): string {
  const compact = raw.replace(/[\s-]/g, '').toUpperCase();
  return compact.length === 10 ? `${compact.slice(0, 5)}-${compact.slice(5)}` : compact;
}

export const activationSchema = z
  .object({
    email: z
      .string()
      .check(
        z.trim(),
        z.minLength(1, 'Escribe el correo que te indicó tu gestor.'),
        z.maxLength(254, 'El correo es demasiado largo.'),
        z.regex(EMAIL, 'Revisa el correo: debe tener la forma nombre@dominio.co.'),
      ),
    code: z.pipe(
      z.pipe(z.string(), z.transform(normalizeActivationCode)),
      z
        .string()
        .check(
          z.regex(
            /^[0-9A-Z]{5}-[0-9A-Z]{5}$/,
            'El código tiene 10 letras y números, por ejemplo 7K3QD-M9X2P.',
          ),
        ),
    ),
    newPassword: z
      .string()
      .check(
        z.minLength(
          12,
          'Usa al menos 12 caracteres. Una frase que recuerdes con facilidad funciona bien.',
        ),
        z.maxLength(128, 'La contraseña es demasiado larga.'),
      ),
    confirmation: z.string(),
  })
  .check(
    z.refine((form) => form.confirmation === form.newPassword, {
      path: ['confirmation'],
      error: 'Las contraseñas no coinciden. Escríbela igual en los dos campos.',
    }),
  );
