CREATE TABLE IF NOT EXISTS recording_deletion_outbox (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    training_session_id BIGINT NOT NULL,
    object_key VARCHAR(1000) NOT NULL UNIQUE,
    reason VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_recording_deletion_outbox_reason CHECK (
        reason IN ('RECORDING_DELETED', 'SESSION_CANCELED', 'HISTORY_DELETED', 'USER_WITHDRAWN', 'UPLOAD_EXPIRED')
    ),
    CONSTRAINT ck_recording_deletion_outbox_status CHECK (
        status IN ('PENDING', 'DELETED', 'FAILED')
    ),
    CONSTRAINT ck_recording_deletion_outbox_attempt_count CHECK (attempt_count BETWEEN 0 AND 10)
);

CREATE INDEX IF NOT EXISTS idx_recording_deletion_outbox_dispatch
    ON recording_deletion_outbox (status, next_attempt_at, id);

CREATE INDEX IF NOT EXISTS idx_recording_deletion_outbox_user
    ON recording_deletion_outbox (user_id, status);
