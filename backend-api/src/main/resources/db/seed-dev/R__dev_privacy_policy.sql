-- =============================================================================
-- Seed de desarrollo y pruebas (ADR-0005 §8) · SOLO perfiles dev y test.
-- Publica la política v0.1 (borrador, no apta para producción) para que el
-- autorregistro y /me/consents funcionen en local. La v1.0 aprobada por el
-- equipo llegará como migración versionada en db/migration antes de la Fase 4.
-- Idempotente: no duplica la versión ni desplaza otra política vigente.
-- =============================================================================
INSERT INTO auth.privacy_policies (version, title, document_url, content_sha256, effective_from, is_current)
SELECT 'v0.1',
       'Política de tratamiento de datos (borrador de desarrollo, no apta para producción)',
       'https://integrate.localhost/legal/politica-tratamiento-datos/v0.1',
       encode(sha256(convert_to(
           'Borrador de desarrollo de la política de tratamiento de datos personales de Intégrate. '
           || 'Texto de prueba sin validez legal; no apto para producción.', 'UTF8')), 'hex'),
       TIMESTAMPTZ '2026-10-01 00:00:00+00',
       NOT EXISTS (SELECT 1 FROM auth.privacy_policies WHERE is_current)
ON CONFLICT (version) DO NOTHING;
