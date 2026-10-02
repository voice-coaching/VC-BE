-- V33: V32 is the existing production example-question seed. Canonical draft was never applied.
-- Expand-only, inactive registry for validated request-v2 execution identity.
-- No backfill, callback result shapes, VERIFIED receipts, or existing-row changes.
CREATE TABLE analysis_canonical_executions (
    execution_id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    analysis_id BIGINT NOT NULL CHECK (analysis_id BETWEEN 1 AND 9007199254740991),
    recording_id BIGINT NOT NULL CHECK (recording_id BETWEEN 1 AND 9007199254740991),
    content_id BIGINT NOT NULL CHECK (content_id BETWEEN 1 AND 9007199254740991),
    analysis_profile VARCHAR(100) NOT NULL
        CHECK (analysis_profile = 'CANONICAL_FROZEN_20260928_V4'),
    request_schema_version VARCHAR(100) NOT NULL
        CHECK (request_schema_version = 'voice-coaching.runpod-analysis-request.v2'),
    result_schema_version VARCHAR(100) NOT NULL
        CHECK (result_schema_version = 'voice-coaching.runpod-analysis-result.v4'),
    request_payload_sha256 VARCHAR(64) NOT NULL CHECK (request_payload_sha256 ~ '^[0-9a-f]{64}$'),
    script_sha256 VARCHAR(64) NOT NULL CHECK (script_sha256 ~ '^[0-9a-f]{64}$'),
    audio_sha256 VARCHAR(64) NOT NULL CHECK (audio_sha256 ~ '^[0-9a-f]{64}$'),
    deadline_at TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (analysis_id, execution_id)
);

-- Deliberately no FK to mutable application rows or outboxes: indefinite retention
-- must neither cascade-delete snapshots nor block the existing recording/user lifecycle.
-- Registration verifies the live relation under the analysis lock; reads rejoin it.
CREATE FUNCTION reject_canonical_execution_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'canonical execution snapshots are immutable'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER canonical_execution_immutable
    BEFORE UPDATE OR DELETE ON analysis_canonical_executions
    FOR EACH ROW EXECUTE FUNCTION reject_canonical_execution_mutation();
CREATE TRIGGER canonical_execution_no_truncate
    BEFORE TRUNCATE ON analysis_canonical_executions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();
