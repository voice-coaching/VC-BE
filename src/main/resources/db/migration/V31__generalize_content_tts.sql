ALTER TABLE practice_contents ADD COLUMN tts_revision INTEGER NOT NULL DEFAULT 1 CHECK(tts_revision>0);
ALTER TABLE example_tts_jobs ADD COLUMN content_id BIGINT REFERENCES practice_contents(id);
ALTER TABLE example_tts_jobs ADD COLUMN text_revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE example_tts_jobs ADD COLUMN object_key VARCHAR(512);
UPDATE example_tts_jobs j SET content_id=e.content_id FROM practice_examples e WHERE e.id=j.example_id;
ALTER TABLE example_tts_jobs ALTER COLUMN content_id SET NOT NULL;
ALTER TABLE example_tts_jobs ALTER COLUMN example_id DROP NOT NULL;
ALTER TABLE example_tts_jobs DROP CONSTRAINT example_tts_jobs_example_id_profile_revision_key;
ALTER TABLE example_tts_jobs ADD CONSTRAINT uq_content_tts_revision UNIQUE(content_id,text_revision,profile_revision);
ALTER TABLE reference_audios ADD COLUMN tts_job_id BIGINT UNIQUE REFERENCES example_tts_jobs(id);
CREATE TABLE content_tts_outbox(content_id BIGINT PRIMARY KEY REFERENCES practice_contents(id),text_revision INTEGER NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),processed_at TIMESTAMPTZ);
CREATE FUNCTION revise_content_tts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.script_text IS DISTINCT FROM OLD.script_text OR NEW.owner_id IS DISTINCT FROM OLD.owner_id THEN NEW.tts_revision:=OLD.tts_revision+1;
 ELSE NEW.tts_revision:=OLD.tts_revision; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER revise_content_tts BEFORE UPDATE ON practice_contents FOR EACH ROW EXECUTE FUNCTION revise_content_tts();
CREATE FUNCTION enqueue_content_tts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.owner_id IS NULL AND NEW.status='PUBLISHED' AND length(trim(NEW.script_text))>0 THEN
  INSERT INTO content_tts_outbox(content_id,text_revision) VALUES(NEW.id,NEW.tts_revision)
  ON CONFLICT(content_id) DO UPDATE SET text_revision=EXCLUDED.text_revision,processed_at=NULL;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER enqueue_content_tts AFTER INSERT OR UPDATE OF script_text,status,owner_id ON practice_contents FOR EACH ROW EXECUTE FUNCTION enqueue_content_tts();
INSERT INTO content_tts_outbox(content_id,text_revision) SELECT id,tts_revision FROM practice_contents WHERE owner_id IS NULL AND status='PUBLISHED' AND length(trim(script_text))>0;
