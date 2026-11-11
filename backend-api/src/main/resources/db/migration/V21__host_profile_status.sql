-- =============================================================================
-- V21 · ADR-0070: estado del anfitrión (ACTIVE, SUSPENDED, REVOKED), igual que la acreditación del gestor.
--   Hasta ahora un anfitrión no podía darse de baja: conservaba el rol y su panel aunque saliera de la plataforma.
--   Todos los anfitriones existentes quedan ACTIVE. Solo DDL aditivo; no cambia datos.
-- Reversible: ALTER TABLE catalog.host_profiles DROP COLUMN status.
-- =============================================================================
ALTER TABLE catalog.host_profiles
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE catalog.host_profiles
    ADD CONSTRAINT ck_host_profiles_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'));

COMMENT ON COLUMN catalog.host_profiles.status IS
    'ACTIVE | SUSPENDED | REVOKED (ADR-0070). Solo un anfitrión ACTIVE opera y aparece en el catálogo público.';
