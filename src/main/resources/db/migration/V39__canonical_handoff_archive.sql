ALTER TABLE analysis_results ADD COLUMN handoff_received_at TIMESTAMPTZ;
-- Additive v5 ownership transfer. v4 receipt semantics remain mandatory.
ALTER TABLE analysis_canonical_executions DROP CONSTRAINT analysis_canonical_executions_analysis_profile_check;
ALTER TABLE analysis_canonical_executions DROP CONSTRAINT analysis_canonical_executions_request_schema_version_check;
ALTER TABLE analysis_canonical_executions DROP CONSTRAINT analysis_canonical_executions_result_schema_version_check;
ALTER TABLE analysis_canonical_executions ADD CONSTRAINT canonical_execution_profile_tuple CHECK (
 (analysis_profile='CANONICAL_FROZEN_20260928_V4' AND request_schema_version='voice-coaching.runpod-analysis-request.v2' AND result_schema_version='voice-coaching.runpod-analysis-result.v4') OR
 (analysis_profile='CANONICAL_HANDOFF_20261004_V5' AND request_schema_version='voice-coaching.runpod-analysis-request.v3' AND result_schema_version='voice-coaching.runpod-analysis-result.v5'));

CREATE TABLE analysis_canonical_handoffs (
 handoff_id UUID PRIMARY KEY, execution_id UUID NOT NULL UNIQUE REFERENCES analysis_canonical_executions(execution_id),
 event_id UUID NOT NULL UNIQUE, request_id UUID NOT NULL, analysis_id BIGINT NOT NULL, worker_instance_id UUID NOT NULL,
 metadata_bytes BYTEA NOT NULL CHECK(octet_length(metadata_bytes) BETWEEN 1 AND 65536),
 projection_bytes BYTEA NOT NULL CHECK(octet_length(projection_bytes) BETWEEN 1 AND 1048576),
 handoff_sha256 VARCHAR(64) NOT NULL CHECK(handoff_sha256 ~ '^[0-9a-f]{64}$'),
 reserved_bytes BIGINT NOT NULL CHECK(reserved_bytes BETWEEN 1 AND 85065728),
 state VARCHAR(20) NOT NULL DEFAULT 'STAGING' CHECK(state IN ('STAGING','RECEIVED','VERIFYING','COMMITTED','REJECTED','REVOKED')),
 received_at TIMESTAMPTZ, verified_at TIMESTAMPTZ, committed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 claim_id UUID, claim_until TIMESTAMPTZ, attempts INT NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, reason_code VARCHAR(60),
 CHECK((state='VERIFYING')=(claim_id IS NOT NULL AND claim_until IS NOT NULL)),
 CHECK(state NOT IN ('RECEIVED','VERIFYING','COMMITTED') OR received_at IS NOT NULL),
 CHECK(state<>'COMMITTED' OR (verified_at IS NOT NULL AND committed_at IS NOT NULL))
);
CREATE INDEX canonical_handoff_pending ON analysis_canonical_handoffs(next_attempt_at) WHERE state IN ('RECEIVED','VERIFYING');
CREATE TABLE analysis_canonical_handoff_artifacts (
 handoff_id UUID NOT NULL REFERENCES analysis_canonical_handoffs(handoff_id),
 kind VARCHAR(40) NOT NULL CHECK(kind IN ('CORE','BRIDGE_RESULT','SELECTION_PROJECTION','BINDING','ASSOCIATION')),
 schema_version VARCHAR(100) NOT NULL, sha256 VARCHAR(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
 byte_size INT NOT NULL CHECK(byte_size BETWEEN 1 AND 16777216), raw_bytes BYTEA,
 PRIMARY KEY(handoff_id,kind), CHECK(raw_bytes IS NULL OR octet_length(raw_bytes)=byte_size)
);
CREATE FUNCTION protect_canonical_handoff() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION 'handoff retention is indefinite' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.handoff_id,NEW.execution_id,NEW.event_id,NEW.request_id,NEW.analysis_id,NEW.worker_instance_id,NEW.metadata_bytes,NEW.projection_bytes,NEW.handoff_sha256,NEW.reserved_bytes)
 IS DISTINCT FROM ROW(OLD.handoff_id,OLD.execution_id,OLD.event_id,OLD.request_id,OLD.analysis_id,OLD.worker_instance_id,OLD.metadata_bytes,OLD.projection_bytes,OLD.handoff_sha256,OLD.reserved_bytes)
 OR OLD.state IN ('COMMITTED','REJECTED','REVOKED')
 OR (OLD.state<>'STAGING' AND NEW.state='STAGING') THEN
 RAISE EXCEPTION 'immutable handoff' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER canonical_handoff_immutable BEFORE UPDATE OR DELETE ON analysis_canonical_handoffs FOR EACH ROW EXECUTE FUNCTION protect_canonical_handoff();
CREATE TRIGGER canonical_handoff_no_truncate BEFORE TRUNCATE ON analysis_canonical_handoffs FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();
CREATE FUNCTION protect_handoff_artifact() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION 'artifact retention is indefinite' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.handoff_id,NEW.kind,NEW.schema_version,NEW.sha256,NEW.byte_size) IS DISTINCT FROM ROW(OLD.handoff_id,OLD.kind,OLD.schema_version,OLD.sha256,OLD.byte_size)
 OR OLD.raw_bytes IS NOT NULL THEN RAISE EXCEPTION 'immutable original' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER handoff_artifact_immutable BEFORE UPDATE OR DELETE ON analysis_canonical_handoff_artifacts FOR EACH ROW EXECUTE FUNCTION protect_handoff_artifact();
CREATE TRIGGER handoff_artifact_no_truncate BEFORE TRUNCATE ON analysis_canonical_handoff_artifacts FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();

ALTER TABLE analysis_canonical_results ADD COLUMN handoff_id UUID REFERENCES analysis_canonical_handoffs(handoff_id);
ALTER TABLE analysis_canonical_results DROP CONSTRAINT analysis_canonical_results_event_id_fkey;
ALTER TABLE analysis_canonical_results DROP CONSTRAINT analysis_canonical_results_schema_version_check;
ALTER TABLE analysis_canonical_results DROP CONSTRAINT analysis_canonical_results_analysis_profile_check;
ALTER TABLE analysis_canonical_results DROP CONSTRAINT analysis_canonical_results_check;
ALTER TABLE analysis_canonical_results ADD CONSTRAINT canonical_result_transport CHECK (
 (schema_version='voice-coaching.runpod-analysis-result.v4' AND analysis_profile='CANONICAL_FROZEN_20260928_V4' AND handoff_id IS NULL AND ((core_sha256 IS NULL)=(evidence_receipt_id IS NULL))) OR
 (schema_version='voice-coaching.runpod-analysis-result.v5' AND analysis_profile='CANONICAL_HANDOFF_20261004_V5' AND handoff_id IS NOT NULL AND evidence_receipt_id IS NULL));
CREATE FUNCTION require_canonical_result_origin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.handoff_id IS NULL THEN
  IF NOT EXISTS(SELECT 1 FROM analysis_canonical_callback_inbox WHERE event_id=NEW.event_id AND status='VERIFIED') THEN RAISE EXCEPTION 'verified callback required'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM analysis_canonical_handoffs WHERE handoff_id=NEW.handoff_id AND event_id=NEW.event_id AND execution_id=NEW.execution_id AND state='VERIFYING') THEN RAISE EXCEPTION 'claimed handoff required'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER canonical_result_origin BEFORE INSERT ON analysis_canonical_results FOR EACH ROW EXECUTE FUNCTION require_canonical_result_origin();

CREATE TABLE analysis_canonical_archive_jobs (
 handoff_id UUID PRIMARY KEY REFERENCES analysis_canonical_handoffs(handoff_id),
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','UPLOADING','VERIFYING','ARCHIVED','RETRY_WAIT','RECONCILE_REQUIRED')),
 claim_id UUID, claim_until TIMESTAMPTZ, attempts INT NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, reason_code VARCHAR(60), archived_at TIMESTAMPTZ
);
CREATE TABLE analysis_canonical_archive_artifacts (
 handoff_id UUID NOT NULL, kind VARCHAR(40) NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','UPLOADING','VERIFYING','ARCHIVED','RETRY_WAIT','RECONCILE_REQUIRED')),
 object_key VARCHAR(1000), version_id VARCHAR(1024), definitive_rejection BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(handoff_id,kind), FOREIGN KEY(handoff_id,kind) REFERENCES analysis_canonical_handoff_artifacts(handoff_id,kind),
 CHECK(state NOT IN ('VERIFYING','ARCHIVED') OR (object_key IS NOT NULL AND version_id IS NOT NULL AND version_id<>'null'))
);
CREATE FUNCTION protect_archive_checkpoint() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION 'archive retention is indefinite'; END IF;
 IF OLD.handoff_id<>NEW.handoff_id OR OLD.kind<>NEW.kind OR OLD.state='ARCHIVED'
 OR (OLD.version_id IS NOT NULL AND ROW(NEW.object_key,NEW.version_id) IS DISTINCT FROM ROW(OLD.object_key,OLD.version_id))
 OR (OLD.state='RECONCILE_REQUIRED' AND NEW.state IN ('PENDING','RETRY_WAIT'))
 OR (OLD.state='UPLOADING' AND NEW.state IN ('PENDING','RETRY_WAIT') AND NOT (NEW.state='RETRY_WAIT' AND NEW.definitive_rejection AND NEW.version_id IS NULL)) THEN RAISE EXCEPTION 'immutable archive checkpoint'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER archive_checkpoint_immutable BEFORE UPDATE OR DELETE ON analysis_canonical_archive_artifacts FOR EACH ROW EXECUTE FUNCTION protect_archive_checkpoint();
CREATE TRIGGER archive_checkpoint_no_truncate BEFORE TRUNCATE ON analysis_canonical_archive_artifacts FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();
