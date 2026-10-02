-- V35: follows canonical receipts at V34; previous numbering was an unapplied draft.
-- Expand only. No admission switch, backfill to v4, media deletion or original-data deletion.
CREATE TABLE analysis_canonical_callback_inbox (
    event_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL UNIQUE REFERENCES analysis_canonical_executions(execution_id),
    request_id UUID NOT NULL,
    analysis_id BIGINT NOT NULL,
    worker_instance_id UUID NOT NULL,
    evidence_receipt_id UUID REFERENCES analysis_evidence_receipts(receipt_id),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    raw_sha256 VARCHAR(64) NOT NULL CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    callback_bytes BYTEA NOT NULL CHECK (octet_length(callback_bytes) BETWEEN 1 AND 1048576),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VERIFYING','VERIFIED','APPLIED','REJECTED','CANCELED','EXPIRED')),
    reason_code VARCHAR(60),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    claim_id UUID,
    claim_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMPTZ,
    CHECK ((status='VERIFYING' AND claim_id IS NOT NULL AND claim_until IS NOT NULL)
        OR (status<>'VERIFYING' AND claim_id IS NULL AND claim_until IS NULL)),
    CHECK ((status IN ('VERIFIED','APPLIED')) = (verified_at IS NOT NULL)),
    CHECK ((status IN ('PENDING','VERIFYING','VERIFIED','APPLIED') AND reason_code IS NULL)
        OR (status='REJECTED' AND reason_code IN ('EVIDENCE_INVALID','EVIDENCE_STORAGE_UNAVAILABLE') AND reason_code IS NOT NULL)
        OR (status='CANCELED' AND reason_code='EXECUTION_INACTIVE' AND reason_code IS NOT NULL)
        OR (status='EXPIRED' AND reason_code='DEADLINE_EXCEEDED' AND reason_code IS NOT NULL))
);
CREATE INDEX canonical_callback_pending ON analysis_canonical_callback_inbox(next_attempt_at,registered_at)
    WHERE status IN ('PENDING','VERIFYING');

CREATE FUNCTION protect_canonical_callback_inbox() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RAISE EXCEPTION 'canonical callback retention is indefinite' USING ERRCODE='23514';
    END IF;
    IF OLD.status IN ('APPLIED','REJECTED','CANCELED','EXPIRED')
       OR ROW(NEW.event_id,NEW.execution_id,NEW.request_id,NEW.analysis_id,NEW.worker_instance_id,
              NEW.evidence_receipt_id,NEW.payload_sha256,NEW.raw_sha256,NEW.callback_bytes,NEW.registered_at)
          IS DISTINCT FROM
          ROW(OLD.event_id,OLD.execution_id,OLD.request_id,OLD.analysis_id,OLD.worker_instance_id,
              OLD.evidence_receipt_id,OLD.payload_sha256,OLD.raw_sha256,OLD.callback_bytes,OLD.registered_at)
       OR (OLD.status='VERIFIED' AND NEW.status NOT IN ('APPLIED','CANCELED','EXPIRED')) THEN
        RAISE EXCEPTION 'canonical callback identity or terminal state is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER canonical_callback_immutable BEFORE UPDATE OR DELETE ON analysis_canonical_callback_inbox
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_callback_inbox();
CREATE TRIGGER canonical_callback_no_truncate BEFORE TRUNCATE ON analysis_canonical_callback_inbox
    FOR EACH STATEMENT EXECUTE FUNCTION protect_canonical_callback_inbox();

CREATE TABLE analysis_canonical_results (
    event_id UUID PRIMARY KEY REFERENCES analysis_canonical_callback_inbox(event_id),
    execution_id UUID NOT NULL UNIQUE REFERENCES analysis_canonical_executions(execution_id),
    request_id UUID NOT NULL,
    analysis_id BIGINT NOT NULL,
    recording_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    worker_instance_id UUID NOT NULL,
    schema_version VARCHAR(100) NOT NULL CHECK (schema_version='voice-coaching.runpod-analysis-result.v4'),
    analysis_profile VARCHAR(100) NOT NULL CHECK (analysis_profile='CANONICAL_FROZEN_20260928_V4'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED','FAILED')),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    raw_sha256 VARCHAR(64) NOT NULL CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    callback_bytes BYTEA NOT NULL CHECK (octet_length(callback_bytes) BETWEEN 1 AND 1048576),
    -- JSON (not JSONB) avoids rejecting frozen diagnostic Unicode escapes such as \u0000.
    -- No SQL extraction from diagnostics; typed columns drive operations, BYTEA is the hash authority.
    callback_document JSON NOT NULL,
    evidence_receipt_id UUID REFERENCES analysis_evidence_receipts(receipt_id),
    core_sha256 VARCHAR(64),
    canonical_analysis_id VARCHAR(200),
    representation VARCHAR(20),
    decision_status VARCHAR(20) CHECK (decision_status IN ('ACCEPT','REJECT','INCONCLUSIVE','SYSTEM_FAILURE')),
    decision_reason_code VARCHAR(60),
    decision_stage VARCHAR(30),
    core_status VARCHAR(20),
    adapter_status VARCHAR(60),
    generation_status VARCHAR(60),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((core_sha256 IS NULL) = (evidence_receipt_id IS NULL)),
    CHECK ((core_sha256 IS NULL) = (decision_status IS NULL))
);
-- Reuse the immutable registry trigger: result originals never follow user-row cascades.
CREATE TRIGGER canonical_result_immutable BEFORE UPDATE OR DELETE ON analysis_canonical_results
    FOR EACH ROW EXECUTE FUNCTION reject_canonical_execution_mutation();
CREATE TRIGGER canonical_result_no_truncate BEFORE TRUNCATE ON analysis_canonical_results
    FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();

ALTER TABLE analysis_results
    ADD COLUMN analysis_profile VARCHAR(100) NOT NULL DEFAULT 'LEGACY_SEUNGUN_V3',
    ADD COLUMN expected_result_schema_version VARCHAR(100),
    ADD COLUMN canonical_result_event_id UUID,
    ADD COLUMN committed_result_schema_version VARCHAR(100);
-- Pointer has no cascade/FK into immutable originals; current visibility is checked via live joins.

CREATE TABLE analysis_canonical_result_effects (
    event_id UUID PRIMARY KEY REFERENCES analysis_canonical_results(event_id),
    analysis_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    completed_at TIMESTAMPTZ,
    claim_id UUID,
    claim_until TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
