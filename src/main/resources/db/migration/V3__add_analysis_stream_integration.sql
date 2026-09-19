ALTER TABLE analysis_results
    ADD COLUMN IF NOT EXISTS active_request_event_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS analysis_outcome VARCHAR(40),
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS worker_revision VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pipeline_revision VARCHAR(100),
    ADD COLUMN IF NOT EXISTS audio_sha256 VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_analysis_results_active_request_event_id
    ON analysis_results (active_request_event_id);

CREATE TABLE IF NOT EXISTS analysis_request_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    analysis_id BIGINT NOT NULL REFERENCES analysis_results (id),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_analysis_request_outbox_dispatch
    ON analysis_request_outbox (status, next_attempt_at, id);
