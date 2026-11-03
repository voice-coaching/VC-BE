-- Preserve history and media while removing deleted content from new practice.
ALTER TABLE practice_contents ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE reference_audios ADD COLUMN retired_at TIMESTAMPTZ;
ALTER TABLE training_sessions ADD COLUMN content_script_snapshot TEXT;
ALTER TABLE training_sessions ADD COLUMN content_updated_at_snapshot TIMESTAMPTZ;
-- Existing public sessions pin the current pre-upgrade text. Private text remains encrypted.
UPDATE training_sessions s SET content_script_snapshot=c.script_text,
    content_updated_at_snapshot=c.updated_at
FROM practice_contents c WHERE s.content_id=c.id AND c.owner_id IS NULL;
CREATE INDEX idx_practice_contents_live_admin ON practice_contents(created_at DESC,id DESC)
    WHERE owner_id IS NULL AND deleted_at IS NULL;
