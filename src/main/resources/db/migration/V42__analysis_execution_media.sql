-- Additive media snapshot. Historic executions deliberately remain without one.
CREATE TABLE analysis_execution_media (
    execution_id UUID PRIMARY KEY REFERENCES analysis_canonical_executions(execution_id),
    recording_id BIGINT NOT NULL,
    media_type VARCHAR(16) NOT NULL CHECK(media_type IN ('AUDIO_ONLY','AUDIO_VISUAL')),
    audio_sha256 VARCHAR(64) NOT NULL CHECK(audio_sha256 ~ '^[0-9a-f]{64}$'),
    video_sha256 VARCHAR(64) CHECK(video_sha256 ~ '^[0-9a-f]{64}$'),
    receipt_sha256 VARCHAR(64) NOT NULL CHECK(receipt_sha256 ~ '^[0-9a-f]{64}$'),
    receipt_bytes BYTEA NOT NULL CHECK(octet_length(receipt_bytes) BETWEEN 1 AND 16384),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK((media_type='AUDIO_ONLY' AND video_sha256 IS NULL)
       OR (media_type='AUDIO_VISUAL' AND video_sha256 IS NOT NULL))
);
CREATE TRIGGER execution_media_immutable BEFORE UPDATE OR DELETE ON analysis_execution_media
    FOR EACH ROW EXECUTE FUNCTION reject_canonical_execution_mutation();
CREATE TRIGGER execution_media_no_truncate BEFORE TRUNCATE ON analysis_execution_media
    FOR EACH STATEMENT EXECUTE FUNCTION reject_canonical_execution_mutation();
