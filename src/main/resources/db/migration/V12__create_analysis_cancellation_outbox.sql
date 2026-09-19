CREATE TABLE IF NOT EXISTS analysis_cancellation_outbox (
    id BIGSERIAL PRIMARY KEY,
    request_event_id VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_analysis_cancellation_outbox_dispatch
    ON analysis_cancellation_outbox (status, next_attempt_at, id);
