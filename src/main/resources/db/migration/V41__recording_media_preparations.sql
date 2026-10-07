-- Server-produced identity only; no inferred receipts for historic recordings.
CREATE TABLE recording_media_preparations (
    recording_id BIGINT PRIMARY KEY REFERENCES voice_recordings(id),
    schema_version VARCHAR(100) NOT NULL CHECK(schema_version='voice-coaching.media-preparation.v1'),
    receipt_bytes BYTEA NOT NULL CHECK(octet_length(receipt_bytes) BETWEEN 1 AND 16384),
    receipt_sha256 VARCHAR(64) NOT NULL CHECK(receipt_sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- A recording's media identity cannot be replaced in place. Normal recording
-- removal may remove its preparation row explicitly; no cascading retention change.
CREATE FUNCTION reject_media_preparation_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'media preparations are immutable' USING ERRCODE='23514';
END; $$;
CREATE TRIGGER media_preparation_immutable BEFORE UPDATE ON recording_media_preparations
    FOR EACH ROW EXECUTE FUNCTION reject_media_preparation_update();
