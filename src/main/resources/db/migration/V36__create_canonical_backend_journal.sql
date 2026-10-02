-- V36: follows canonical callbacks at V35; previous numbering was an unapplied draft.
-- Inactive, additive durable handoff. No admission change, backfill or deletion.
CREATE TABLE analysis_canonical_journals (
    execution_id UUID PRIMARY KEY REFERENCES analysis_canonical_executions(execution_id),
    request_id UUID NOT NULL UNIQUE,
    analysis_id BIGINT NOT NULL,
    worker_instance_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    worker_revision VARCHAR(100) NOT NULL,
    pipeline_revision VARCHAR(100) NOT NULL,
    request_bytes BYTEA NOT NULL CHECK (octet_length(request_bytes) BETWEEN 1 AND 65536),
    request_raw_sha256 VARCHAR(64) NOT NULL CHECK (request_raw_sha256 ~ '^[0-9a-f]{64}$'),
    prepare_bytes BYTEA CHECK (octet_length(prepare_bytes) BETWEEN 1 AND 65536),
    manifest_bytes BYTEA CHECK (octet_length(manifest_bytes) BETWEEN 1 AND 65536),
    producer_started_at TIMESTAMPTZ,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE analysis_canonical_upload_journal (
    execution_id UUID NOT NULL REFERENCES analysis_canonical_journals(execution_id),
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('CORE','BRIDGE_RESULT','SELECTION_PROJECTION','BINDING','ASSOCIATION')),
    metadata_bytes BYTEA NOT NULL CHECK (octet_length(metadata_bytes) BETWEEN 1 AND 4096),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    byte_size INTEGER NOT NULL CHECK (byte_size BETWEEN 1 AND 16777216),
    -- Private handoff spool, not the public result or primary long-term evidence store.
    -- No automatic cleanup: B2 exact-version handoff/reconciliation precedes any future cleanup policy.
    staged_bytes BYTEA,
    state VARCHAR(20) NOT NULL DEFAULT 'DECLARED'
        CHECK (state IN ('DECLARED','PREPARED','UPLOADING','UPLOADED','VERIFIED')),
    reference_bytes BYTEA CHECK (octet_length(reference_bytes) BETWEEN 1 AND 8192),
    PRIMARY KEY (execution_id,kind),
    CHECK ((state='DECLARED' AND staged_bytes IS NULL)
        OR (state<>'DECLARED' AND staged_bytes IS NOT NULL AND octet_length(staged_bytes)=byte_size)),
    CHECK ((state IN ('UPLOADED','VERIFIED')) = (reference_bytes IS NOT NULL))
);
CREATE FUNCTION protect_canonical_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RAISE EXCEPTION 'canonical journal retention is indefinite' USING ERRCODE='23514';
    END IF;
    IF TG_TABLE_NAME='analysis_canonical_journals' THEN
        IF ROW(NEW.execution_id,NEW.request_id,NEW.analysis_id,NEW.worker_instance_id,NEW.event_id,
               NEW.worker_revision,NEW.pipeline_revision,NEW.request_bytes,NEW.request_raw_sha256,NEW.registered_at)
           IS DISTINCT FROM ROW(OLD.execution_id,OLD.request_id,OLD.analysis_id,OLD.worker_instance_id,OLD.event_id,
               OLD.worker_revision,OLD.pipeline_revision,OLD.request_bytes,OLD.request_raw_sha256,OLD.registered_at)
           OR (OLD.prepare_bytes IS NOT NULL AND NEW.prepare_bytes IS DISTINCT FROM OLD.prepare_bytes)
           OR (OLD.manifest_bytes IS NOT NULL AND NEW.manifest_bytes IS DISTINCT FROM OLD.manifest_bytes)
           OR (OLD.producer_started_at IS NOT NULL AND NEW.producer_started_at IS DISTINCT FROM OLD.producer_started_at) THEN
            RAISE EXCEPTION 'canonical journal identity immutable' USING ERRCODE='23514';
        END IF;
    ELSE
        IF ROW(NEW.execution_id,NEW.kind,NEW.metadata_bytes,NEW.sha256,NEW.byte_size)
           IS DISTINCT FROM ROW(OLD.execution_id,OLD.kind,OLD.metadata_bytes,OLD.sha256,OLD.byte_size)
           OR (OLD.staged_bytes IS NOT NULL AND NEW.staged_bytes IS DISTINCT FROM OLD.staged_bytes)
           OR (OLD.reference_bytes IS NOT NULL AND NEW.reference_bytes IS DISTINCT FROM OLD.reference_bytes)
           OR NOT ((OLD.state='DECLARED' AND NEW.state='PREPARED')
                OR (OLD.state='PREPARED' AND NEW.state='UPLOADING')
                OR (OLD.state='UPLOADING' AND NEW.state='UPLOADED')
                OR (OLD.state='UPLOADED' AND NEW.state='VERIFIED')) THEN
            RAISE EXCEPTION 'canonical upload transition invalid' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER canonical_journal_guard BEFORE UPDATE OR DELETE ON analysis_canonical_journals
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_journal();
CREATE TRIGGER canonical_journal_no_truncate BEFORE TRUNCATE ON analysis_canonical_journals
    FOR EACH STATEMENT EXECUTE FUNCTION protect_canonical_journal();
CREATE TRIGGER canonical_upload_guard BEFORE UPDATE OR DELETE ON analysis_canonical_upload_journal
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_journal();
CREATE TRIGGER canonical_upload_no_truncate BEFORE TRUNCATE ON analysis_canonical_upload_journal
    FOR EACH STATEMENT EXECUTE FUNCTION protect_canonical_journal();

CREATE TABLE analysis_canonical_ack_journal (
    event_id UUID NOT NULL REFERENCES analysis_canonical_results(event_id),
    disposition VARCHAR(20) NOT NULL CHECK (disposition IN ('APPLIED','DUPLICATE')),
    ack_bytes BYTEA NOT NULL CHECK (octet_length(ack_bytes) BETWEEN 1 AND 65536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id,disposition)
);
CREATE TRIGGER canonical_ack_immutable BEFORE UPDATE OR DELETE ON analysis_canonical_ack_journal
    FOR EACH ROW EXECUTE FUNCTION reject_canonical_execution_mutation();
CREATE TRIGGER canonical_ack_no_truncate BEFORE TRUNCATE ON analysis_canonical_ack_journal
    FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();

CREATE TABLE analysis_canonical_callback_apply (
    event_id UUID PRIMARY KEY REFERENCES analysis_canonical_callback_inbox(event_id),
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','APPLYING','DONE','STOPPED')),
    claim_id UUID,
    claim_until TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts>=0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((state='APPLYING')=(claim_id IS NOT NULL AND claim_until IS NOT NULL)),
    CHECK (state='APPLYING' OR (claim_id IS NULL AND claim_until IS NULL))
);
