-- =============================================================================
-- Intégrate · Datos de prueba para desarrollo local (NO usar en producción)
-- -----------------------------------------------------------------------------
-- Vacía los datos transaccionales y crea un usuario por cada rol, dos
-- asociaciones, anfitriones, experiencias y franjas horarias.
--
-- Conserva los catálogos gobernados por migraciones: auth.roles,
-- auth.privacy_policies, catalog.categories/departments/municipalities y
-- payments.commission_policies.
--
-- SEC-DIR-004: este archivo NO contiene contraseñas. El hash Argon2id se pasa
-- como variable de psql (el texto en claro vive en infra/env/.env.test-users,
-- ignorado por Git):
--   docker exec -i integrate-postgres psql -U postgres -d integrate \
--     -v ON_ERROR_STOP=1 -v pwhash='<hash argon2id>' < infra/postgres/seed/dev_test_data.sql
-- =============================================================================
\set ON_ERROR_STOP on
BEGIN;

-- Con replica se omiten los triggers de solo-inserción (trg_forbid_truncate) y las FK.
SET LOCAL session_replication_role = replica;

-- Todas las tablas de negocio salvo los catálogos conservados y auth.users
-- (esta última la referencian los catálogos por created_by, así que se vacía con DELETE).
DO $$
DECLARE
    v_tables text;
BEGIN
    SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ')
      INTO v_tables
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind IN ('r', 'p')
       AND NOT c.relispartition
       AND n.nspname IN ('audit', 'auth', 'booking', 'catalog', 'notification', 'offline', 'payments', 'voucher')
       AND (n.nspname || '.' || c.relname) NOT IN (
            'auth.users', 'auth.roles', 'auth.privacy_policies',
            'catalog.categories', 'catalog.departments', 'catalog.municipalities',
            'payments.commission_policies', 'audit.chain_head');
    EXECUTE 'TRUNCATE TABLE ' || v_tables || ' RESTART IDENTITY';
END
$$;

-- auth.users la referencian los catálogos conservados (created_by), por eso no admite TRUNCATE.
DELETE FROM auth.users;

UPDATE audit.chain_head SET last_id = 0, last_hash = repeat('0', 64), updated_at = now() WHERE id = 1;

SET LOCAL session_replication_role = origin;

-- -----------------------------------------------------------------------------
-- Usuarios (una contraseña común de prueba; ver .env.test-users)
-- -----------------------------------------------------------------------------
INSERT INTO auth.users (id, email, password_hash, full_name, doc_type, doc_number, phone) VALUES
 ('00000000-0000-4000-8000-000000000001', 'admin@ejemplo.com',      :'pwhash', 'Ana María Administradora', 'CC', '1000000001', '+573000000001'),
 ('00000000-0000-4000-8000-000000000002', 'gestor1@ejemplo.com',    :'pwhash', 'Gabriel Gestor Enlace',    'CC', '1000000002', '+573000000002'),
 ('00000000-0000-4000-8000-000000000003', 'gestor2@ejemplo.com',    :'pwhash', 'Gloria Gestora Enlace',    'CC', '1000000003', '+573000000003'),
 ('00000000-0000-4000-8000-000000000004', 'anfitrion1@ejemplo.com', :'pwhash', 'Hernando Anfitrión Rivera','CC', '1000000004', '+573000000004'),
 ('00000000-0000-4000-8000-000000000005', 'anfitrion2@ejemplo.com', :'pwhash', 'Helena Anfitriona Pardo',  'CC', '1000000005', '+573000000005'),
 ('00000000-0000-4000-8000-000000000006', 'anfitrion3@ejemplo.com', :'pwhash', 'Hugo Anfitrión Mena',      'CC', '1000000006', '+573000000006'),
 ('00000000-0000-4000-8000-000000000007', 'turista1@ejemplo.com',   :'pwhash', 'Tomás Turista Gómez',      'CC', '1000000007', '+573000000007'),
 ('00000000-0000-4000-8000-000000000008', 'turista2@ejemplo.com',   :'pwhash', 'Tatiana Turista Ruiz',     'CE', '1000000008', '+573000000008'),
 ('00000000-0000-4000-8000-000000000009', 'turista3@ejemplo.com',   :'pwhash', 'Thomas Visitor Smith',     'PA', 'AB1234567', '+14155550123');

INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM (VALUES
        ('00000000-0000-4000-8000-000000000001'::uuid, 'ADMIN'),
        ('00000000-0000-4000-8000-000000000001'::uuid, 'TOURIST'),
        ('00000000-0000-4000-8000-000000000002'::uuid, 'LINK_MANAGER'),
        ('00000000-0000-4000-8000-000000000002'::uuid, 'TOURIST'),
        ('00000000-0000-4000-8000-000000000003'::uuid, 'LINK_MANAGER'),
        ('00000000-0000-4000-8000-000000000003'::uuid, 'TOURIST'),
        ('00000000-0000-4000-8000-000000000004'::uuid, 'HOST'),
        ('00000000-0000-4000-8000-000000000005'::uuid, 'HOST'),
        ('00000000-0000-4000-8000-000000000006'::uuid, 'HOST'),
        ('00000000-0000-4000-8000-000000000007'::uuid, 'TOURIST'),
        ('00000000-0000-4000-8000-000000000008'::uuid, 'TOURIST'),
        ('00000000-0000-4000-8000-000000000009'::uuid, 'TOURIST')
       ) AS u(id, role_code)
  JOIN auth.roles r ON r.code = u.role_code;

-- Consentimiento Habeas Data vigente (política actual): datos personales a todos,
-- datos de salud a turistas y datos sensibles a anfitriones.
INSERT INTO auth.habeas_data_consents (user_id, policy_id, purpose, action, ip_address, user_agent)
SELECT u.id, p.id, c.purpose, 'GRANTED', '127.0.0.1', 'seed-dev'
  FROM auth.users u
  CROSS JOIN (SELECT id FROM auth.privacy_policies WHERE is_current) p
  JOIN auth.user_roles ur ON ur.user_id = u.id
  JOIN auth.roles r ON r.id = ur.role_id
  JOIN LATERAL (VALUES ('PERSONAL_DATA'),
                       (CASE WHEN r.code = 'TOURIST' THEN 'HEALTH_EMERGENCY' END),
                       (CASE WHEN r.code = 'HOST' THEN 'SENSITIVE_VICTIM' END)) AS c(purpose) ON c.purpose IS NOT NULL
 GROUP BY u.id, p.id, c.purpose;

-- -----------------------------------------------------------------------------
-- Asociaciones, anfitriones y gestores
-- -----------------------------------------------------------------------------
INSERT INTO catalog.community_associations
    (id, nit, rnt_number, legal_name, association_type, municipality_code, address, contact_email, contact_phone) VALUES
 ('10000000-0000-4000-8000-000000000001', '900100200-1', '100001', 'Asociación Caminos de Memoria San Carlos',
  'ASOCIACION_VICTIMAS', '05649', 'Vereda El Chocó, San Carlos', 'contacto.sancarlos@ejemplo.com', '+573100000001'),
 ('10000000-0000-4000-8000-000000000002', '900300400-2', '100002', 'Cooperativa Campesina Montes de María',
  'COOPERATIVA_CAMPESINA', '13244', 'Corregimiento San Isidro, El Carmen de Bolívar', 'contacto.montes@ejemplo.com', '+573100000002');

INSERT INTO catalog.host_profiles (id, user_id, association_id, is_verified, verified_at, verified_by) VALUES
 ('20000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', TRUE,  now(), '00000000-0000-4000-8000-000000000001'),
 ('20000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', TRUE,  now(), '00000000-0000-4000-8000-000000000001'),
 ('20000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000002', TRUE,  now(), '00000000-0000-4000-8000-000000000001');

INSERT INTO catalog.link_manager_profiles (id, user_id, association_id, accredited_on, accredited_by) VALUES
 ('30000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', CURRENT_DATE, '00000000-0000-4000-8000-000000000001'),
 ('30000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', CURRENT_DATE, '00000000-0000-4000-8000-000000000001');

INSERT INTO catalog.host_manager_assignments (host_id, manager_id, association_id) VALUES
 ('20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001'),
 ('20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001'),
 ('20000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002');

-- -----------------------------------------------------------------------------
-- Experiencias (6 aprobadas, 1 en revisión, 1 borrador) y sus categorías
-- -----------------------------------------------------------------------------
INSERT INTO catalog.experiences
    (id, host_id, title, description, safety_guidelines, physical_intensity, duration_minutes,
     municipality_code, vereda, meeting_point, latitude, longitude, max_capacity_per_slot,
     unit_price_cop, status, created_by) VALUES
 ('40000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
  'Sendero de la memoria del río Calderas',
  'Recorrido guiado por quienes vivieron el conflicto, con relatos de reconstrucción del territorio.',
  'Calzado cómodo, agua y protector solar. No apto para movilidad reducida severa.',
  'MEDIUM', 180, '05649', 'El Chocó', 'Parque principal de San Carlos', 6.187000, -74.994000, 12, 45000, 'DRAFT',
  '00000000-0000-4000-8000-000000000004'),
 ('40000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
  'Taller de memoria y tejido comunitario',
  'Espacio de diálogo y arte donde se teje la memoria colectiva de la comunidad.',
  'Actividad en espacio cubierto. Se recomienda llegar 10 minutos antes.',
  'LOW', 120, '05649', NULL, 'Casa de la Cultura de San Carlos', 6.186000, -74.993000, 15, 30000, 'DRAFT',
  '00000000-0000-4000-8000-000000000004'),
 ('40000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002',
  'Cocina tradicional de la montaña antioqueña',
  'Aprenda recetas ancestrales con productoras locales y comparta la mesa.',
  'Informe alergias alimentarias al reservar. Uso de delantal y manos limpias.',
  'LOW', 150, '05649', 'La Tebaida', 'Finca La Esperanza, vereda La Tebaida', 6.190000, -74.990000, 10, 55000, 'DRAFT',
  '00000000-0000-4000-8000-000000000005'),
 ('40000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000002',
  'Agroturismo cafetero con familias retornadas',
  'Recorrido por el cultivo de café, beneficio y catación con familias que retornaron a su tierra.',
  'Terreno irregular. Botas de caucho recomendadas. Menores acompañados.',
  'MEDIUM', 240, '05649', 'La Tebaida', 'Finca La Esperanza, vereda La Tebaida', 6.191000, -74.989000, 8, 70000, 'DRAFT',
  '00000000-0000-4000-8000-000000000005'),
 ('40000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000003',
  'Caminata por los Montes de María',
  'Ascenso guiado con miradores y relatos sobre la resistencia campesina.',
  'Alta exigencia física. Llevar 2 litros de agua. Salida a las 6:00 a. m.',
  'HIGH', 300, '13244', 'San Isidro', 'Plaza de San Isidro', 9.718000, -75.122000, 10, 65000, 'DRAFT',
  '00000000-0000-4000-8000-000000000006'),
 ('40000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000003',
  'Avistamiento de aves y conservación de bosque seco',
  'Observación de aves en la reserva comunitaria con guía local.',
  'Silencio durante el recorrido. Binoculares disponibles. Repelente recomendado.',
  'LOW', 180, '13244', 'San Isidro', 'Entrada reserva comunitaria San Isidro', 9.720000, -75.120000, 8, 40000, 'DRAFT',
  '00000000-0000-4000-8000-000000000006'),
 ('40000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000003',
  'Ruta del cacao y la memoria',
  'Experiencia pendiente de revisión por el administrador.',
  'Calzado cerrado. Actividad al aire libre.',
  'MEDIUM', 200, '13244', NULL, 'Plaza de San Isidro', NULL, NULL, 10, 50000, 'PENDING_REVIEW',
  '00000000-0000-4000-8000-000000000006'),
 ('40000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000001',
  'Encuentro de narración oral (borrador)',
  'Borrador en edición por el anfitrión.',
  'Por definir.',
  'LOW', 90, '05649', NULL, 'Por definir en San Carlos', NULL, NULL, 20, 20000, 'DRAFT',
  '00000000-0000-4000-8000-000000000004');

-- La guarda de experiencias exige nacer en DRAFT/PENDING_REVIEW: se aprueban siguiendo el flujo de revisión.
UPDATE catalog.experiences SET status = 'PENDING_REVIEW'
 WHERE id IN ('40000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000002',
              '40000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000004',
              '40000000-0000-4000-8000-000000000005', '40000000-0000-4000-8000-000000000006');
UPDATE catalog.experiences SET status = 'APPROVED' WHERE status = 'PENDING_REVIEW'
   AND id <> '40000000-0000-4000-8000-000000000007';

INSERT INTO catalog.experience_categories (experience_id, category_id)
SELECT e.id, c.id
  FROM (VALUES
        ('40000000-0000-4000-8000-000000000001'::uuid, 'SENDERISMO_MEMORIA'),
        ('40000000-0000-4000-8000-000000000002'::uuid, 'TALLER_MEMORIA'),
        ('40000000-0000-4000-8000-000000000003'::uuid, 'GASTRONOMIA_TRADICIONAL'),
        ('40000000-0000-4000-8000-000000000004'::uuid, 'AGROTURISMO'),
        ('40000000-0000-4000-8000-000000000005'::uuid, 'SENDERISMO_MEMORIA'),
        ('40000000-0000-4000-8000-000000000006'::uuid, 'NATURALEZA_CONSERVACION'),
        ('40000000-0000-4000-8000-000000000007'::uuid, 'AGROTURISMO'),
        ('40000000-0000-4000-8000-000000000008'::uuid, 'TALLER_MEMORIA')
       ) AS e(id, category_code)
  JOIN catalog.categories c ON c.code = e.category_code;

-- Franjas abiertas: 3 por experiencia aprobada, en los próximos 7, 14 y 21 días (hora de Colombia).
INSERT INTO catalog.schedules (experience_id, start_time, end_time, total_slots, available_slots)
SELECT e.id,
       d.start_ts,
       d.start_ts + make_interval(mins => e.duration_minutes),
       e.max_capacity_per_slot,
       e.max_capacity_per_slot
  FROM catalog.experiences e
  CROSS JOIN LATERAL (
        SELECT ((date_trunc('day', now() AT TIME ZONE 'America/Bogota') + make_interval(days => n) + interval '9 hours')
                AT TIME ZONE 'America/Bogota') AS start_ts
          FROM unnest(ARRAY[7, 14, 21]) AS n
  ) d
 WHERE e.status = 'APPROVED';

COMMIT;
