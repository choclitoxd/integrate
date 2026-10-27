-- =============================================================================
-- V15 · Verificación de la dirección del punto de encuentro (ADR-0030).
--
-- El estado lo gobierna el autómata MeetingPointAutomaton; la base solo lo guarda y
-- limita sus valores. Las experiencias que ya estaban en revisión o publicadas quedan
-- en UNVERIFIED (aprobables, sin resultado): no se les inventa una verificación.
-- =============================================================================

ALTER TABLE catalog.experiences
    ADD COLUMN IF NOT EXISTS meeting_point_check_status      VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN IF NOT EXISTS meeting_point_check_distance_m  INTEGER,
    ADD COLUMN IF NOT EXISTS meeting_point_suggested_lat     NUMERIC(8, 6),
    ADD COLUMN IF NOT EXISTS meeting_point_suggested_lng     NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS meeting_point_checked_at        TIMESTAMPTZ;

ALTER TABLE catalog.experiences
    ADD CONSTRAINT ck_experiences_meeting_point_check_status CHECK (meeting_point_check_status IN
        ('PENDIENTE', 'VERIFICANDO', 'MATCH', 'REVIEW', 'MISMATCH', 'UNVERIFIED', 'CONFIRMADO', 'CORREGIDO')),
    ADD CONSTRAINT ck_experiences_meeting_point_check_distance CHECK (meeting_point_check_distance_m >= 0),
    ADD CONSTRAINT ck_experiences_meeting_point_suggested CHECK (
        (meeting_point_suggested_lat IS NULL AND meeting_point_suggested_lng IS NULL) OR
        (meeting_point_suggested_lat BETWEEN -90 AND 90 AND meeting_point_suggested_lng BETWEEN -180 AND 180));

UPDATE catalog.experiences
   SET meeting_point_check_status = 'UNVERIFIED'
 WHERE status IN ('PENDING_REVIEW', 'APPROVED', 'SUSPENDED', 'RETIRED')
   AND meeting_point_check_status = 'PENDIENTE';

COMMENT ON COLUMN catalog.experiences.meeting_point_check_status IS 'Estado del autómata de verificación del punto de encuentro (ADR-0030). Solo MATCH, REVIEW, UNVERIFIED y CONFIRMADO permiten aprobar.';
COMMENT ON COLUMN catalog.experiences.meeting_point_check_distance_m IS 'Distancia en metros entre las coordenadas declaradas y la dirección geocodificada; nula si no hubo resultado.';
COMMENT ON COLUMN catalog.experiences.meeting_point_suggested_lat IS 'Latitud sugerida por el geocodificador. Solo la ven el anfitrión y el administrador; nunca se aplica sola.';
COMMENT ON COLUMN catalog.experiences.meeting_point_suggested_lng IS 'Longitud sugerida por el geocodificador (ver meeting_point_suggested_lat).';

-- -----------------------------------------------------------------------------
-- catalog.meeting_point_check_history · una fila por transición del autómata (auditoría)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.meeting_point_check_history (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    experience_id  UUID         NOT NULL,
    from_state     VARCHAR(12)  NOT NULL,
    event          VARCHAR(40)  NOT NULL,
    to_state       VARCHAR(12)  NOT NULL,
    distance_m     INTEGER,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_id       UUID         DEFAULT common.current_actor(),
    CONSTRAINT pk_meeting_point_check_history PRIMARY KEY (id),
    CONSTRAINT fk_meeting_point_check_history_experience FOREIGN KEY (experience_id)
        REFERENCES catalog.experiences (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_meeting_point_check_history_actor FOREIGN KEY (actor_id)
        REFERENCES auth.users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_meeting_point_check_history_distance CHECK (distance_m >= 0)
);

CREATE INDEX IF NOT EXISTS ix_meeting_point_check_history_experience
    ON catalog.meeting_point_check_history (experience_id, occurred_at);

-- Solo se agrega: la bitácora no se corrige ni se borra (mismo criterio que el historial de estados).
REVOKE UPDATE, DELETE ON catalog.meeting_point_check_history FROM integrate_app;

COMMENT ON TABLE catalog.meeting_point_check_history IS 'Transiciones del autómata de verificación del punto de encuentro (ADR-0030): estado previo, evento, estado nuevo, distancia y actor.';
