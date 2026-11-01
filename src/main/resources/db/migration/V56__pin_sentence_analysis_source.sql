ALTER TABLE training_sessions
    ADD COLUMN sentence_index integer,
    ADD COLUMN sentence_text_sha256 varchar(64),
    ADD COLUMN sentence_prompt_revision varchar(128);
ALTER TABLE training_sessions ADD CONSTRAINT ck_training_sentence_source
    CHECK ((sentence_index IS NULL AND sentence_text_sha256 IS NULL AND sentence_prompt_revision IS NULL)
        OR (sentence_index IS NOT NULL AND sentence_index >= 0 AND sentence_text_sha256 IS NOT NULL AND sentence_prompt_revision IS NOT NULL));
