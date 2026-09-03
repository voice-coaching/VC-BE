ALTER TABLE processing_consents
    ADD CONSTRAINT ck_processing_consents_positive_subject_ids
        CHECK (user_id > 0 AND training_session_id > 0) NOT VALID,
    ADD CONSTRAINT ck_processing_consents_policy_revision
        CHECK (policy_revision ~ '^[A-Za-z0-9._-]{1,100}$') NOT VALID,
    ADD CONSTRAINT ck_processing_consents_scope_binding
        CHECK (
            (scope = 'VOICE_ANALYSIS' AND recording_id > 0 AND request_event_id IS NOT NULL)
            OR
            (scope = 'FACE_VIDEO_PROCESSING' AND recording_id IS NULL AND request_event_id IS NULL)
        ) NOT VALID,
    ADD CONSTRAINT ck_processing_consents_request_event_uuid
        CHECK (
            request_event_id IS NULL
            OR request_event_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        ) NOT VALID,
    ADD CONSTRAINT ck_processing_consents_revocation_order
        CHECK (revoked_at IS NULL OR revoked_at >= granted_at) NOT VALID;

ALTER TABLE processing_consents
    VALIDATE CONSTRAINT ck_processing_consents_positive_subject_ids;
ALTER TABLE processing_consents
    VALIDATE CONSTRAINT ck_processing_consents_policy_revision;
ALTER TABLE processing_consents
    VALIDATE CONSTRAINT ck_processing_consents_scope_binding;
ALTER TABLE processing_consents
    VALIDATE CONSTRAINT ck_processing_consents_request_event_uuid;
ALTER TABLE processing_consents
    VALIDATE CONSTRAINT ck_processing_consents_revocation_order;

CREATE UNIQUE INDEX IF NOT EXISTS uq_processing_consents_request_event
    ON processing_consents (request_event_id)
    WHERE request_event_id IS NOT NULL;
