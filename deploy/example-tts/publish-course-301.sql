-- Authored FE final-consonant examples, mapped to existing published course 301,
-- practice step 303 and immutable education revision 3. No existing text is changed.
BEGIN;
SET LOCAL lock_timeout='3s';
SET LOCAL statement_timeout='15s';
SELECT pg_advisory_xact_lock(hashtext('publish-course-301-step-303-examples-r1'));
DO $$
DECLARE example_set BIGINT; content BIGINT; item RECORD;
BEGIN
    IF NOT EXISTS(SELECT 1 FROM courses c JOIN course_steps s ON s.course_id=c.id
        JOIN course_step_revisions r ON r.step_id=s.id
        WHERE c.id=301 AND c.status='PUBLISHED' AND c.course_type='PRONUNCIATION'
        AND c.title='받침 발음 기초' AND s.id=303 AND s.step_type='PRACTICE' AND r.id=3 AND r.revision=1)
    THEN RAISE EXCEPTION 'Course mapping changed'; END IF;
    IF EXISTS(SELECT 1 FROM practice_example_sets WHERE step_id=303) THEN
        RAISE EXCEPTION 'An example set already exists; inspect rather than overwrite';
    END IF;
    INSERT INTO practice_example_sets(step_id,revision,education_revision_id,published_at)
        VALUES(303,1,3,now()) RETURNING id INTO example_set;
    FOR item IN SELECT * FROM (VALUES
        (1,'따뜻한 국밥 한 그릇을 맛있게 먹었습니다.','받침 ㄱ과 ㅂ을 짧게 닫아 읽어요.'),
        (2,'꽃밭 끝에 햇빛이 밝게 비칩니다.','받침 ㅊ·ㅌ·ㅅ이 대표음으로 나는 것을 익혀요.'),
        (3,'산 너머 넓은 들판에 바람이 붑니다.','ㄴ·ㅁ·ㅇ 받침의 울림을 유지해요.'),
        (4,'맑은 물결이 잔잔하게 흘러갑니다.','겹받침과 받침 ㄹ을 또렷하게 읽어요.'),
        (5,'옷을 입고 밝은 아침길을 걸었습니다.','받침 뒤에 모음이 올 때 연음에 집중해요.')
    ) AS authored(position,text,hint) LOOP
        INSERT INTO practice_contents(content_type,learning_focus,title,script_text,difficulty,status,published_at,created_at,updated_at)
            VALUES('CLASS_PRACTICE','PRONUNCIATION','받침 발음 예문 '||item.position,item.text,'BEGINNER','PUBLISHED',now(),now(),now())
            RETURNING id INTO content;
        INSERT INTO practice_examples(id,set_id,content_id,example_order,hint,focus,locale)
            VALUES('course301-step303-r1-'||item.position,example_set,content,item.position,item.hint,'PRONUNCIATION','ko-KR');
    END LOOP;
END;
$$;
COMMIT;
SELECT e.id,e.content_id,s.step_id,s.revision FROM practice_examples e
JOIN practice_example_sets s ON s.id=e.set_id WHERE s.step_id=303 ORDER BY e.example_order;
