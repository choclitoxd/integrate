-- =============================================================================
-- Intégrate · Política de privacidad para el despliegue de DEMOSTRACIÓN (ADR-0024)
-- -----------------------------------------------------------------------------
-- El perfil dev carga db/seed-dev; el despliegue de demo no, y sin una política vigente el
-- autorregistro no tiene qué consentir (GET /api/v1/privacy-policies/current responde 404).
-- Ejecutar UNA vez, conectado a la base `integrate` con el rol integrate_owner:
--   psql "postgresql://integrate_owner:<pwd>@<host-directo-neon>/integrate?sslmode=require" \
--        -f infra/postgres/seed/demo_privacy_policy.sql
-- Borrador de demostración, sin validez legal: NO usar con datos reales (ADR-0005 §8).
-- Idempotente: no duplica la versión ni desplaza otra política vigente.
-- =============================================================================
INSERT INTO auth.privacy_policies (version, title, document_url, content_sha256, effective_from, is_current)
SELECT 'v0.1',
       'Política de tratamiento de datos (borrador de demostración, sin validez legal)',
       'https://integrate.invalid/legal/politica-tratamiento-datos/v0.1',
       encode(sha256(convert_to(
           'Borrador de demostración de la política de tratamiento de datos personales de Intégrate. '
           || 'Texto de prueba sin validez legal; no apto para producción.', 'UTF8')), 'hex'),
       now(),
       NOT EXISTS (SELECT 1 FROM auth.privacy_policies WHERE is_current)
ON CONFLICT (version) DO NOTHING;
