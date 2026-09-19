CREATE TABLE IF NOT EXISTS recording_upload_intents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    training_session_id BIGINT NOT NULL,
    object_key VARCHAR(1000) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_recording_upload_intents_status
        CHECK (status IN ('ISSUED', 'CONSUMED', 'EXPIRED')),
    CONSTRAINT ck_recording_upload_intents_size
        CHECK (file_size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_recording_upload_intents_expiry
    ON recording_upload_intents (status, expires_at, id);

CREATE INDEX IF NOT EXISTS idx_recording_upload_intents_user_session
    ON recording_upload_intents (user_id, training_session_id, status);
