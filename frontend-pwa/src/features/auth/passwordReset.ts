import { z } from 'zod/mini';

import type { HttpClient } from '@/shared/api';

/** Recuperación de contraseña con enlace de un solo uso (ADR-0014 §1, §3 y §5). */

export interface ResetPasswordRequest {
  readonly token: string;
  readonly newPassword: string;
}

/** Siempre 202 sin cuerpo: no revela si la cuenta existe. */
export function requestPasswordReset(http: HttpClient, email: string): Promise<void> {
  return http.request<undefined>('/v1/auth/forgot-password', { method: 'POST', body: { email } });
}

export function resetPassword(http: HttpClient, body: ResetPasswordRequest): Promise<void> {
  return http.request<undefined>('/v1/auth/reset-password', { method: 'POST', body });
}

/**
 * Token del fragmento (`#token=…`). El fragmento nunca viaja al servidor ni a la cabecera `Referer`;
 * la pantalla lo borra de la barra de direcciones en cuanto lo lee.
 */
export function resetTokenFromHash(hash: string): string | null {
  const token = new URLSearchParams(hash.replace(/^#/, '')).get('token')?.trim();
  // Un `#token=` vacío cuenta como ausente.
  return token === undefined || token === '' ? null : token;
}

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

export const forgotPasswordSchema = z.object({
  email: z
    .string()
    .check(
      z.trim(),
      z.minLength(1, 'Escribe tu correo.'),
      z.maxLength(254, 'El correo es demasiado largo.'),
      z.regex(EMAIL, 'Revisa el correo: debe tener la forma nombre@dominio.co.'),
    ),
});

export const resetPasswordSchema = z
  .object({
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
