-- =============================================================================
-- V20 · ADR-0064: todo anfitrión y gestor de enlace es también turista.
--   Las cuentas de anfitrión creadas por enrolamiento asistido nacieron solo con HOST, y algún gestor solo con
--   LINK_MANAGER, así que no podían ver «Mis experiencias» ni reservar (el backend exige TOURIST).
--   Se otorga TOURIST a todo usuario activo con HOST o LINK_MANAGER vigente que no lo tenga.
-- Idempotente: el índice único parcial uq_user_roles_active y el NOT EXISTS evitan duplicados. Solo DML.
-- Reversible: borrar de auth.user_roles las filas TOURIST con created_by NULL y created_at de esta migración.
-- created_by queda nulo (default common.current_actor() sin actor): lo hace el sistema, no una persona.
-- =============================================================================
INSERT INTO auth.user_roles (user_id, role_id)
SELECT DISTINCT u.id, tourist.id
  FROM auth.users u
  JOIN auth.user_roles ur ON ur.user_id = u.id AND ur.revoked_at IS NULL
  JOIN auth.roles r ON r.id = ur.role_id AND r.code IN ('HOST', 'LINK_MANAGER')
 CROSS JOIN (SELECT id FROM auth.roles WHERE code = 'TOURIST') tourist
 WHERE u.status = 'ACTIVE'
   AND u.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1
                     FROM auth.user_roles t
                    WHERE t.user_id = u.id AND t.role_id = tourist.id AND t.revoked_at IS NULL);
