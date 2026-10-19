ALTER TABLE practice_examples
    DROP CONSTRAINT practice_examples_example_order_check;

ALTER TABLE practice_examples
    ADD CONSTRAINT ck_practice_example_order
        CHECK (example_order BETWEEN 1 AND 10);

CREATE OR REPLACE FUNCTION validate_practice_example_set() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target BIGINT;
    item_count INTEGER;
    first_order INTEGER;
    last_order INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'practice_example_sets' THEN
        target := NEW.id;
    ELSE
        target := NEW.set_id;
    END IF;

    SELECT count(*), min(example_order), max(example_order)
    INTO item_count, first_order, last_order
    FROM practice_examples
    WHERE set_id = target;

    IF item_count NOT BETWEEN 5 AND 10 THEN
        RAISE EXCEPTION 'A published example revision requires between five and ten items' USING ERRCODE = '23514';
    END IF;
    IF first_order <> 1 OR last_order <> item_count THEN
        RAISE EXCEPTION 'Published example orders must be contiguous from one' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM practice_example_sets s
        JOIN course_step_revisions r ON r.id = s.education_revision_id AND r.step_id = s.step_id
        WHERE s.id = target
    ) THEN
        RAISE EXCEPTION 'Example and education step mismatch' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM practice_examples e
        JOIN practice_contents c ON c.id = e.content_id
        WHERE e.set_id = target
          AND (c.owner_id IS NOT NULL OR c.content_type <> 'CLASS_PRACTICE' OR c.status <> 'PUBLISHED'
            OR length(trim(c.script_text)) = 0 OR octet_length(c.script_text) > 4500)
    ) THEN
        RAISE EXCEPTION 'Invalid published example content' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
