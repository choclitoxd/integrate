-- =============================================================================
-- V16 · ADR-0038 (hallazgos E-03 y E-02 de la prueba de ciclo de vida)
--   1. Correo sintético opaco para los anfitriones aún sin activar.
--   2. Rol LINK_MANAGER de los gestores cuya acreditación ya está REVOCADA.
-- Idempotente: se puede volver a ejecutar sin efecto. Solo DML.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. El correo sintético {tipo}{documento}@comunidad.integrate.co dejaba el número de documento a la vista en la
--    lista del gestor. Las cuentas todavía PENDING_RESET nunca han iniciado sesión con él, así que se renombran a
--    h-<10 caracteres>@comunidad.integrate.co. Las ACTIVAS conservan su correo (es su usuario de ingreso); la API lo
--    enmascara al mostrarlo (ADR-0038 §1.5).
--    Alfabeto de 32 símbolos (sin I, O, 0 ni 1): 256 es múltiplo de 32, el sesgo es nulo. Una fila a la vez:
--    gen_random_uuid() dentro de una sola sentencia se evaluaría una vez y repetiría el correo en todas.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    pending   RECORD;
    candidate TEXT;
BEGIN
    FOR pending IN
        SELECT id
          FROM auth.users
         WHERE credential_status = 'PENDING_RESET'
           AND email LIKE '%@comunidad.integrate.co'
           AND email NOT LIKE 'h-%'
    LOOP
        LOOP
            candidate := 'h-' || (
                SELECT string_agg(substr('abcdefghjklmnpqrstuvwxyz23456789', 1 + (get_byte(b.bytes, g.i) % 32), 1),
                                  '' ORDER BY g.i)
                  FROM (SELECT decode(replace(gen_random_uuid()::text, '-', ''), 'hex') AS bytes) b
                       CROSS JOIN generate_series(0, 9) AS g(i)
            ) || '@comunidad.integrate.co';
            EXIT WHEN NOT EXISTS (SELECT 1 FROM auth.users WHERE lower(email) = candidate);
        END LOOP;
        UPDATE auth.users SET email = candidate WHERE id = pending.id;
    END LOOP;
END
$$;

-- -----------------------------------------------------------------------------
-- 2. Revocar la acreditación (estado terminal) no quitaba ROLE_LINK_MANAGER: la persona seguía viendo el portal del
--    gestor sin poder operar. Se cierra la asignación de quien solo tiene acreditaciones revocadas; quien conserva
--    una ACTIVE o SUSPENDED no se toca. revoked_by queda nulo: lo hace el sistema, no una persona.
-- -----------------------------------------------------------------------------
UPDATE auth.user_roles ur
   SET revoked_at = now()
  FROM auth.roles r
 WHERE r.id = ur.role_id
   AND r.code = 'LINK_MANAGER'
   AND ur.revoked_at IS NULL
   AND EXISTS (SELECT 1 FROM catalog.link_manager_profiles p
                WHERE p.user_id = ur.user_id AND p.deleted_at IS NULL AND p.accreditation_status = 'REVOKED')
   AND NOT EXISTS (SELECT 1 FROM catalog.link_manager_profiles p
                    WHERE p.user_id = ur.user_id AND p.deleted_at IS NULL
                      AND p.accreditation_status IN ('ACTIVE', 'SUSPENDED'));
