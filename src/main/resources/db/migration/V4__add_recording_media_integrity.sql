ALTER TABLE voice_recordings
    ADD COLUMN audio_sha256 VARCHAR(64);

ALTER TABLE voice_recordings
    ADD CONSTRAINT ck_voice_recordings_audio_sha256
        CHECK (audio_sha256 IS NULL OR audio_sha256 ~ '^[0-9a-f]{64}$');
