ALTER TABLE analysis_request_outbox ADD COLUMN busy_count INTEGER NOT NULL DEFAULT 0 CHECK (busy_count >= 0);
CREATE TABLE analysis_dispatch_gates (
 endpoint_sha256 CHAR(64) PRIMARY KEY,
 claim_id UUID,
 claim_until TIMESTAMPTZ,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
