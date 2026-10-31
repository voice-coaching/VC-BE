-- Preserve step IDs, immutable revisions and session references for historical reads.
ALTER TABLE course_steps ADD COLUMN retired BOOLEAN NOT NULL DEFAULT FALSE;

-- Only normalize courses with one practice step and an existing five-sentence set.
WITH single_practice AS (
    SELECT course_id, min(id) AS step_id
    FROM course_steps
    WHERE step_type = 'PRACTICE'
    GROUP BY course_id
    HAVING count(*) = 1
), ready_courses AS (
    SELECT practice.course_id
    FROM single_practice practice
    JOIN LATERAL (
        SELECT id FROM practice_example_sets
        WHERE step_id = practice.step_id AND published_at <= CURRENT_TIMESTAMP
        ORDER BY revision DESC LIMIT 1
    ) latest ON TRUE
    JOIN practice_examples example ON example.set_id = latest.id
    GROUP BY practice.course_id
    HAVING count(*) = 5
)
UPDATE course_steps step
SET retired = TRUE, required = FALSE
WHERE step.course_id IN (SELECT course_id FROM ready_courses)
  AND step.step_type IN ('THEORY', 'AUDIO_EXAMPLE', 'RESULT_REVIEW');
