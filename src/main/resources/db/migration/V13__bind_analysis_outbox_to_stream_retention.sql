ALTER TABLE analysis_request_outbox
    ADD COLUMN IF NOT EXISTS request_stream_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS retention_protocol_version INTEGER;

CREATE INDEX IF NOT EXISTS idx_analysis_request_outbox_retention
    ON analysis_request_outbox (retention_protocol_version, created_at, id);
