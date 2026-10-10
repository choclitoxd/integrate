// zod/mini: API funcional y tree-shakeable (≈ 10 kB frente a ≈ 110 kB de la API clásica).
import { z } from 'zod/mini';

/** Roles del contrato (`RoleClaim`). Un rol desconocido se descarta, no rompe la sesión. */
export const ROLES = ['ROLE_TOURIST', 'ROLE_HOST', 'ROLE_LINK_MANAGER', 'ROLE_ADMIN'] as const;
export type Role = (typeof ROLES)[number];

export const CONSENT_PURPOSES = ['PERSONAL_DATA', 'SENSITIVE_VICTIM', 'HEALTH_EMERGENCY'] as const;
export const consentPurposeSchema = z.enum(CONSENT_PURPOSES);
export type ConsentPurpose = z.infer<typeof consentPurposeSchema>;

const roleSchema = z.pipe(
  z.array(z.string()),
  z.transform((roles: string[]) =>
    roles.filter((role): role is Role => (ROLES as readonly string[]).includes(role)),
  ),
);

/**
 * `AuthTokenResponse` de login y refresh (ADR-0013 §4): se valida en tiempo de ejecución para no
 * aceptar una sesión malformada.
 */
export const authTokenResponseSchema = z.object({
  accessToken: z.string().check(z.minLength(1)),
  tokenType: z.literal('Bearer'),
  expiresIn: z.int().check(z.positive()),
  user: z.object({
    id: z.string().check(z.minLength(1)),
    email: z.string().check(z.minLength(1)),
    fullName: z.string(),
    roles: roleSchema,
  }),
  requiresConsentUpdate: z.boolean(),
  pendingPolicyVersion: z.nullable(z.string()),
  pendingPurposes: z.array(consentPurposeSchema),
});

export type AuthTokenResponse = z.infer<typeof authTokenResponseSchema>;
export type SessionUser = AuthTokenResponse['user'];
