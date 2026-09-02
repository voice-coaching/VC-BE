CREATE TABLE IF NOT EXISTS processing_consents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    training_session_id BIGINT NOT NULL,
    recording_id BIGINT,
    scope VARCHAR(40) NOT NULL,
    policy_revision VARCHAR(100) NOT NULL,
    subject_sha256 VARCHAR(64) NOT NULL,
    request_event_id VARCHAR(36),
    receipt_sha256 VARCHAR(64) NOT NULL UNIQUE,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_processing_consents_subject_sha256
        CHECK (subject_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_processing_consents_receipt_sha256
        CHECK (receipt_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_processing_consents_scope
        CHECK (scope IN ('VOICE_ANALYSIS', 'FACE_VIDEO_PROCESSING'))
);

CREATE INDEX IF NOT EXISTS idx_processing_consents_user_active
    ON processing_consents (user_id, revoked_at);

CREATE INDEX IF NOT EXISTS idx_processing_consents_session_active
    ON processing_consents (training_session_id, revoked_at);

CREATE INDEX IF NOT EXISTS idx_processing_consents_request_event
    ON processing_consents (request_event_id);
