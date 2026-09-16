ALTER TABLE recording_deletion_outbox
    DROP CONSTRAINT ck_recording_deletion_outbox_reason;

ALTER TABLE recording_deletion_outbox
    ADD CONSTRAINT ck_recording_deletion_outbox_reason CHECK (
        reason IN ('RECORDING_DELETED', 'SESSION_CANCELED', 'HISTORY_DELETED',
                   'USER_WITHDRAWN', 'UPLOAD_EXPIRED', 'ANALYSIS_COMPLETED')
    );
