-- Additive, dormant until analysis.independent flags are explicitly enabled.
CREATE TABLE independent_analysis_attempts (
    client_request_id UUID PRIMARY KEY,
    backend_execution_id UUID NOT NULL UNIQUE,
    analysis_id BIGINT NOT NULL REFERENCES analysis_results(id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES voice_recordings(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id BIGINT NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    content_id BIGINT NOT NULL CHECK (content_id>0),
    audio_object_key TEXT NOT NULL,
    audio_sha256 VARCHAR(64) NOT NULL CHECK (audio_sha256 ~ '^[a-f0-9]{64}$'),
    audio_size BIGINT NOT NULL CHECK (audio_size BETWEEN 1 AND 20971520),
    script_ciphertext TEXT NOT NULL,
    script_sha256 VARCHAR(64) NOT NULL CHECK (script_sha256 ~ '^[a-f0-9]{64}$'),
    stage VARCHAR(32) NOT NULL DEFAULT 'SUBMIT_PENDING'
        CHECK (stage IN ('SUBMIT_PENDING','LOOKUP_PENDING','COLLECTING','COLLECTING_FAILED',
                        'ACK_PENDING','ACKED','SUBMISSION_REJECTED','CANCELLED','FINISHED')),
    ai_job_id UUID UNIQUE,
    ai_execution_id UUID UNIQUE,
    ai_status VARCHAR(16) CHECK (ai_status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    ai_sequence BIGINT NOT NULL DEFAULT 0,
    result_sha256 VARCHAR(64) CHECK (result_sha256 ~ '^[a-f0-9]{64}$'),
    result_bytes BYTEA CHECK (octet_length(result_bytes) <= 2097152),
    collected_at TIMESTAMPTZ,
    committed_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_http_status INTEGER,
    last_error_code VARCHAR(100),
    claim_id UUID,
    claim_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((ai_job_id IS NULL) = (ai_execution_id IS NULL)),
    CHECK ((result_bytes IS NULL) = (result_sha256 IS NULL)),
    CHECK (acknowledged_at IS NULL OR committed_at IS NOT NULL)
);
CREATE INDEX independent_analysis_due_idx ON independent_analysis_attempts(stage,next_attempt_at)
    WHERE stage NOT IN ('ACKED','SUBMISSION_REJECTED','CANCELLED','FINISHED');
CREATE INDEX independent_analysis_owner_idx ON independent_analysis_attempts(analysis_id,owner_id);
