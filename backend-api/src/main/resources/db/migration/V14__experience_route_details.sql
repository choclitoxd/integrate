-- =============================================================================
-- V14 · Datos de recorrido de la experiencia (ADR-0022).
--
-- Distancia, tipo de recorrido, tiempo total de ida y vuelta y paradas. Todo es
-- opcional para no invalidar las experiencias ya publicadas.
-- =============================================================================

ALTER TABLE catalog.experiences
    ADD COLUMN IF NOT EXISTS route_type          VARCHAR(12),
    ADD COLUMN IF NOT EXISTS distance_km         NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS round_trip_minutes  INTEGER;

ALTER TABLE catalog.experiences
    ADD CONSTRAINT ck_experiences_route_type CHECK (route_type IN ('LOOP', 'OUT_AND_BACK', 'ONE_WAY')),
    ADD CONSTRAINT ck_experiences_distance CHECK (distance_km > 0 AND distance_km <= 500),
    ADD CONSTRAINT ck_experiences_round_trip CHECK (round_trip_minutes BETWEEN 15 AND 4320);

COMMENT ON COLUMN catalog.experiences.route_type IS 'LOOP (circular), OUT_AND_BACK (ida y vuelta por el mismo camino) u ONE_WAY. Nulo si no se informó (ADR-0022).';
COMMENT ON COLUMN catalog.experiences.distance_km IS 'Distancia total del recorrido en km, ida y vuelta incluida cuando aplica (ADR-0022).';
COMMENT ON COLUMN catalog.experiences.round_trip_minutes IS 'Tiempo total de ida y vuelta en minutos. Nulo: duration_minutes ya lo cubre (ADR-0022).';

-- -----------------------------------------------------------------------------
-- catalog.experience_stops (1:N, máx. 10 por experiencia; ADR-0022)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.experience_stops (
    experience_id  UUID          NOT NULL,
    position       SMALLINT      NOT NULL,
    name           VARCHAR(120)  NOT NULL,
    description    VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     UUID          DEFAULT common.current_actor(),
    CONSTRAINT pk_experience_stops PRIMARY KEY (experience_id, position),
    CONSTRAINT ck_experience_stops_position CHECK (position BETWEEN 1 AND 10),
    CONSTRAINT ck_experience_stops_name CHECK (length(btrim(name)) >= 2),
    CONSTRAINT fk_experience_stops_experience FOREIGN KEY (experience_id)
        REFERENCES catalog.experiences (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_experience_stops_created_by FOREIGN KEY (created_by)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- Como experience_categories: se reemplazan al editar, sin valor histórico (el cambio queda en la bitácora).
GRANT DELETE ON catalog.experience_stops TO integrate_app;

COMMENT ON TABLE catalog.experience_stops IS 'Paradas ordenadas del recorrido de una experiencia (ADR-0022). Se reemplazan al editar: DELETE físico permitido.';
