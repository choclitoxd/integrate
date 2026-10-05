-- =============================================================================
-- Intégrate · Bootstrap de clúster (se ejecuta UNA vez, como superusuario)
-- -----------------------------------------------------------------------------
-- Crea los roles de login y la base de datos. NO es una migración Flyway:
-- los roles son objetos de clúster y las contraseñas no deben versionarse.
--
-- Uso local (Docker):  montar en /docker-entrypoint-initdb.d/ y definir
--   INTEGRATE_OWNER_PASSWORD, INTEGRATE_API_PASSWORD, INTEGRATE_REPORTING_PASSWORD
-- Uso manual:          psql -U postgres -f 00_bootstrap.sql   (psql >= 15)
-- =============================================================================
\set ON_ERROR_STOP on

\getenv owner_password     INTEGRATE_OWNER_PASSWORD
\getenv api_password       INTEGRATE_API_PASSWORD
\getenv reporting_password INTEGRATE_REPORTING_PASSWORD

-- Roles de grupo (sin login): los privilegios se otorgan a estos roles.
SELECT 'CREATE ROLE integrate_app NOLOGIN'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_app') \gexec
SELECT 'CREATE ROLE integrate_readonly NOLOGIN'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_readonly') \gexec

-- Dueño de los objetos: único usuario que ejecuta Flyway (DDL).
SELECT 'CREATE ROLE integrate_owner LOGIN'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_owner') \gexec
ALTER ROLE integrate_owner PASSWORD :'owner_password';

-- Usuario del backend Spring Boot (solo DML, hereda de integrate_app).
SELECT 'CREATE ROLE integrate_api LOGIN IN ROLE integrate_app'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_api') \gexec
ALTER ROLE integrate_api PASSWORD :'api_password';

-- Usuario de reportes / BI (solo lectura, hereda de integrate_readonly).
SELECT 'CREATE ROLE integrate_reporting LOGIN IN ROLE integrate_readonly'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integrate_reporting') \gexec
ALTER ROLE integrate_reporting PASSWORD :'reporting_password';

SELECT 'CREATE DATABASE integrate OWNER integrate_owner ENCODING ''UTF8'' TEMPLATE template0'
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'integrate') \gexec

\connect integrate

REVOKE ALL ON DATABASE integrate FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE integrate TO integrate_app, integrate_readonly;
GRANT ALL ON DATABASE integrate TO integrate_owner;

-- Toda marca de tiempo se interpreta y presenta en UTC (E7).
ALTER DATABASE integrate SET timezone = 'UTC';

-- R-01: contención acotada del bloqueo pesimista (SELECT ... FOR UPDATE).
ALTER ROLE integrate_api SET lock_timeout = '2s';
ALTER ROLE integrate_api SET statement_timeout = '15s';
ALTER ROLE integrate_api SET idle_in_transaction_session_timeout = '30s';
ALTER ROLE integrate_api SET default_transaction_isolation = 'read committed';

ALTER ROLE integrate_reporting SET default_transaction_read_only = on;
ALTER ROLE integrate_reporting SET statement_timeout = '120s';
