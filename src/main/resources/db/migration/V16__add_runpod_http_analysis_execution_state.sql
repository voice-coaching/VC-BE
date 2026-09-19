ALTER TABLE analysis_results
    ADD COLUMN IF NOT EXISTS active_execution_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS worker_instance_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_result_event_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS last_result_payload_sha256 VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_analysis_results_active_execution_id
    ON analysis_results (active_execution_id);

CREATE INDEX IF NOT EXISTS idx_analysis_results_claim_expires_at
    ON analysis_results (claim_expires_at);

ALTER TABLE analysis_request_outbox
    ADD COLUMN IF NOT EXISTS transport VARCHAR(20) NOT NULL DEFAULT 'REDIS_STREAM',
    ADD COLUMN IF NOT EXISTS execution_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS delivery_reference VARCHAR(128);

ALTER TABLE analysis_request_outbox
    ADD CONSTRAINT chk_analysis_request_outbox_transport
        CHECK (transport IN ('REDIS_STREAM', 'RUNPOD_HTTP'));

ALTER TABLE analysis_request_outbox
    ADD CONSTRAINT chk_analysis_request_outbox_execution_id
        CHECK (execution_id IS NULL OR execution_id ~ '^[0-9a-fA-F-]{36}$');

ALTER TABLE analysis_results
    ADD CONSTRAINT chk_analysis_results_active_execution_id
        CHECK (active_execution_id IS NULL OR active_execution_id ~ '^[0-9a-fA-F-]{36}$'),
    ADD CONSTRAINT chk_analysis_results_last_result_event_id
        CHECK (last_result_event_id IS NULL OR last_result_event_id ~ '^[0-9a-fA-F-]{36}$'),
    ADD CONSTRAINT chk_analysis_results_last_result_payload_sha256
        CHECK (last_result_payload_sha256 IS NULL OR last_result_payload_sha256 ~ '^[0-9a-f]{64}$');

CREATE INDEX IF NOT EXISTS idx_analysis_request_outbox_transport_dispatch
    ON analysis_request_outbox (transport, status, next_attempt_at, id);
