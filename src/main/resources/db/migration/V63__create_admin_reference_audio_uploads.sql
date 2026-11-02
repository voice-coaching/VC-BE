-- Follows V62 administrator audit/version support.
ALTER TABLE reference_audios ADD CONSTRAINT uq_reference_audio_content UNIQUE(id,content_id);

CREATE TABLE admin_reference_audio_uploads (
    id UUID PRIMARY KEY,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    content_id BIGINT NOT NULL REFERENCES practice_contents(id),
    extension VARCHAR(5) NOT NULL CHECK(extension IN ('.mp3','.m4a')),
    content_type VARCHAR(30) NOT NULL CHECK(content_type IN ('audio/mpeg','audio/mp4')),
    size_bytes BIGINT NOT NULL CHECK(size_bytes BETWEEN 1 AND 20971520),
    sha256 VARCHAR(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
    script_sha256 VARCHAR(64) NOT NULL CHECK(script_sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at > created_at),
    state VARCHAR(20) NOT NULL CHECK(state IN ('PENDING','COMPLETED')),
    reference_audio_id BIGINT UNIQUE,
    completion_digest VARCHAR(64),
    CHECK ((state='COMPLETED')=(reference_audio_id IS NOT NULL AND completion_digest IS NOT NULL)),
    CHECK (state<>'PENDING' OR (reference_audio_id IS NULL AND completion_digest IS NULL)),
    CHECK (completion_digest IS NULL OR completion_digest ~ '^[0-9a-f]{64}$'),
    CHECK ((extension='.mp3' AND content_type='audio/mpeg') OR (extension='.m4a' AND content_type='audio/mp4')),
    CONSTRAINT fk_admin_upload_audio_content FOREIGN KEY(reference_audio_id,content_id) REFERENCES reference_audios(id,content_id)
);
CREATE INDEX idx_admin_audio_upload_owner ON admin_reference_audio_uploads(actor_user_id,content_id,created_at DESC);
CREATE INDEX idx_admin_audio_upload_expiry ON admin_reference_audio_uploads(expires_at) WHERE state='PENDING';
