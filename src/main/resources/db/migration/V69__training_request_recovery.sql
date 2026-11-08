CREATE TABLE training_request_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation VARCHAR(40) NOT NULL,
    key_sha256 VARCHAR(64) NOT NULL,
    request_sha256 VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_training_request_receipt UNIQUE(user_id, operation, key_sha256)
);

ALTER TABLE recording_upload_intents
    ADD COLUMN recording_id BIGINT REFERENCES voice_recordings(id) ON DELETE SET NULL,
    ADD COLUMN registration_sha256 VARCHAR(64);
CREATE INDEX ix_upload_intent_recovery ON recording_upload_intents(training_session_id, user_id, id DESC);
