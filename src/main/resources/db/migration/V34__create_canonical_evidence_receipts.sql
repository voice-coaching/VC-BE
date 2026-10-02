-- V34: follows the canonical registry at V33; previous numbering was an unapplied draft.
-- Expand-only private receipt/job store. No existing result or media lifecycle changes.
-- The execution FK targets the immutable registry, never mutable user/recording rows.
CREATE TABLE analysis_evidence_receipts (
    receipt_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL UNIQUE REFERENCES analysis_canonical_executions(execution_id),
    request_id UUID NOT NULL,
    analysis_id BIGINT NOT NULL,
    worker_instance_id UUID NOT NULL,
    manifest_sha256 VARCHAR(64) NOT NULL CHECK (manifest_sha256 ~ '^[0-9a-f]{64}$'),
    manifest_bytes BYTEA NOT NULL CHECK (octet_length(manifest_bytes) BETWEEN 1 AND 65536),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VERIFYING','VERIFIED','REJECTED','EXPIRED','CANCELED')),
    reason_code VARCHAR(60),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verification_attempts INTEGER NOT NULL DEFAULT 0 CHECK (verification_attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verifier_claim_id UUID,
    verifier_claim_until TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    CHECK ((status IN ('PENDING','VERIFYING','VERIFIED') AND reason_code IS NULL)
        OR (status = 'REJECTED' AND reason_code IS NOT NULL AND reason_code IN ('EVIDENCE_INVALID','EVIDENCE_STORAGE_UNAVAILABLE'))
        OR (status = 'EXPIRED' AND reason_code IS NOT NULL AND reason_code = 'DEADLINE_EXCEEDED')
        OR (status = 'CANCELED' AND reason_code IS NOT NULL AND reason_code = 'EXECUTION_INACTIVE')),
    CHECK ((status = 'VERIFYING' AND verifier_claim_id IS NOT NULL AND verifier_claim_until IS NOT NULL)
        OR (status <> 'VERIFYING' AND verifier_claim_id IS NULL AND verifier_claim_until IS NULL)),
    CHECK ((status = 'VERIFIED') = (verified_at IS NOT NULL))
);

CREATE INDEX evidence_receipt_verification_queue
    ON analysis_evidence_receipts(next_attempt_at, registered_at)
    WHERE status IN ('PENDING','VERIFYING');

CREATE FUNCTION protect_canonical_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RAISE EXCEPTION 'canonical receipt retention is indefinite' USING ERRCODE = '23514';
    END IF;
    IF OLD.status IN ('VERIFIED','REJECTED','EXPIRED','CANCELED')
       OR ROW(NEW.receipt_id,NEW.execution_id,NEW.request_id,NEW.analysis_id,
              NEW.worker_instance_id,NEW.manifest_sha256,NEW.manifest_bytes,NEW.registered_at)
          IS DISTINCT FROM
          ROW(OLD.receipt_id,OLD.execution_id,OLD.request_id,OLD.analysis_id,
              OLD.worker_instance_id,OLD.manifest_sha256,OLD.manifest_bytes,OLD.registered_at) THEN
        RAISE EXCEPTION 'canonical receipt identity or terminal state is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER canonical_receipt_immutable_identity
    BEFORE UPDATE OR DELETE ON analysis_evidence_receipts
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_receipt();
CREATE TRIGGER canonical_receipt_no_truncate
    BEFORE TRUNCATE ON analysis_evidence_receipts
    FOR EACH STATEMENT EXECUTE FUNCTION protect_canonical_receipt();
