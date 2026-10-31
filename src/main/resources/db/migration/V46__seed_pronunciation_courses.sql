INSERT INTO content_categories(content_type, code, label, sort_order)
VALUES ('CLASS_PRACTICE', 'PRONUNCIATION_CLASS', '발음 클래스', 20)
ON CONFLICT(content_type, code) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order;

WITH pronunciation_contents(difficulty, title, description, script_text, target_pronunciations, estimated_seconds) AS (
    VALUES
        ('BEGINNER', '발음 초급 연습 1 - 모음 또렷하게', '짧은 안내 문장에서 기본 모음을 또렷하게 구분합니다.', '아이는 아침 일찍 일어나 우유를 마셨습니다.', '["아침", "일찍", "우유"]'::jsonb, 10),
        ('BEGINNER', '발음 초급 연습 2 - 자음 정확하게', '자음이 겹치는 문장을 천천히 읽으며 소리를 분명히 냅니다.', '작은 책상 위에 색색의 책을 올려 두었습니다.', '["작은", "책상", "색색"]'::jsonb, 11),
        ('BEGINNER', '발음 초급 연습 3 - 받침 구분하기', '받침 소리를 생략하지 않고 문장 끝까지 정확하게 전달합니다.', '오늘 준비한 자료를 먼저 확인해 주세요.', '["준비한", "자료를", "확인"]'::jsonb, 11),
        ('BEGINNER', '발음 초급 연습 4 - 된소리 안정적으로', '된소리가 포함된 단어를 힘을 조절해 자연스럽게 발음합니다.', '따뜻한 차 한 잔과 단단한 빵을 준비했습니다.', '["따뜻한", "차", "단단한"]'::jsonb, 12),
        ('BEGINNER', '발음 초급 연습 5 - 문장 끝까지', '짧은 문장에서도 마지막 음절의 힘을 유지합니다.', '오늘의 연습은 여기에서 마치겠습니다.', '["오늘의", "연습", "마치겠습니다"]'::jsonb, 10),

        ('INTERMEDIATE', '발음 중급 연습 1 - 연음 연결', '단어와 단어 사이의 연음을 자연스럽게 연결합니다.', '이번 주에는 새로운 업무를 함께 시작합니다.', '["이번 주에는", "새로운", "함께"]'::jsonb, 13),
        ('INTERMEDIATE', '발음 중급 연습 2 - 비슷한 소리 구분', '비슷하게 들리는 자음의 차이를 분명히 전달합니다.', '발표 자료를 다시 살펴보고 필요한 부분을 고쳤습니다.', '["발표", "자료를", "필요한"]'::jsonb, 14),
        ('INTERMEDIATE', '발음 중급 연습 3 - 겹받침 처리', '겹받침이 있는 단어를 무리하게 끊지 않고 읽습니다.', '값진 경험을 바탕으로 새로운 계획을 세웠습니다.', '["값진", "경험", "계획"]'::jsonb, 14),
        ('INTERMEDIATE', '발음 중급 연습 4 - 속도와 명료도', '속도를 높여도 핵심 단어의 자음과 모음을 유지합니다.', '정확한 안내를 빠르고 이해하기 쉽게 전달하겠습니다.', '["정확한", "빠르고", "이해하기"]'::jsonb, 15),
        ('INTERMEDIATE', '발음 중급 연습 5 - 긴 문장 호흡', '긴 문장을 호흡 단위로 나누면서 발음을 끝까지 유지합니다.', '참여자들은 안내받은 순서에 따라 차분하게 이동했습니다.', '["참여자들", "안내받은", "차분하게"]'::jsonb, 16),

        ('ADVANCED', '발음 고급 연습 1 - 전문 용어', '전문 용어가 많은 문장도 핵심 음절을 정확히 전달합니다.', '정책 변화가 지역 산업과 생활 방식에 미치는 영향을 분석했습니다.', '["정책 변화", "지역 산업", "분석했습니다"]'::jsonb, 17),
        ('ADVANCED', '발음 고급 연습 2 - 복합 문장', '복합 문장의 연결 어미와 핵심 단어를 선명하게 구분합니다.', '자료를 비교한 결과 예상보다 큰 차이가 나타났지만 해결책을 찾았습니다.', '["비교한 결과", "예상보다", "해결책"]'::jsonb, 18),
        ('ADVANCED', '발음 고급 연습 3 - 자음군 안정화', '자음이 연속되는 표현에서도 발화가 흐려지지 않도록 합니다.', '새로운 기준을 적용해 전체 결과를 다시 검토했습니다.', '["새로운 기준", "적용해", "검토했습니다"]'::jsonb, 16),
        ('ADVANCED', '발음 고급 연습 4 - 발표 전달력', '발표 문장에서 정확한 발음과 안정적인 음량을 함께 유지합니다.', '청중이 이해할 수 있도록 핵심 내용을 단계별로 설명하겠습니다.', '["청중이", "핵심 내용", "단계별로"]'::jsonb, 17),
        ('ADVANCED', '발음 고급 연습 5 - 자연스러운 마무리', '긴 문장의 마지막까지 발음과 호흡을 안정적으로 유지합니다.', '지금까지 확인한 내용을 바탕으로 다음 실행 계획을 정리하겠습니다.', '["지금까지", "실행 계획", "정리하겠습니다"]'::jsonb, 18)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status,
    published_at, created_at, updated_at
)
SELECT
    'CLASS_PRACTICE', 'PRONUNCIATION', 'PRONUNCIATION_CLASS', difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, 'PUBLISHED',
    now(), now(), now()
FROM pronunciation_contents source
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'CLASS_PRACTICE'
      AND existing.category = 'PRONUNCIATION_CLASS'
      AND existing.title = source.title
);

WITH pronunciation_courses(course_type, title, description, difficulty, estimated_minutes) AS (
    VALUES
        ('PRONUNCIATION', '발음 기초 클래스', '기본 모음·자음과 받침을 또렷하게 발음하는 초급 클래스입니다.', 'BEGINNER', 18),
        ('PRONUNCIATION', '발음 연결 클래스', '연음·겹받침과 긴 문장의 명료도를 연습하는 중급 클래스입니다.', 'INTERMEDIATE', 22),
        ('PRONUNCIATION', '발음 발표 클래스', '전문 용어와 발표 문장을 정확하게 전달하는 고급 클래스입니다.', 'ADVANCED', 26)
)
INSERT INTO courses(course_type, title, description, difficulty, estimated_minutes, status, created_at, updated_at)
SELECT course_type, title, description, difficulty, estimated_minutes, 'PUBLISHED', now(), now()
FROM pronunciation_courses source
WHERE NOT EXISTS (
    SELECT 1 FROM courses existing
    WHERE existing.course_type = source.course_type AND existing.title = source.title
);

WITH step_source(course_title, theory_title, theory_body) AS (
    VALUES
        ('발음 기초 클래스', '기본 발음 또렷하게 만들기', '모음과 자음, 받침의 기본 원리를 익히고 짧은 문장에서 소리를 분명하게 전달합니다.'),
        ('발음 연결 클래스', '연결 발음과 명료도 높이기', '연음과 겹받침을 자연스럽게 연결하면서 긴 문장의 명료도를 유지합니다.'),
        ('발음 발표 클래스', '발표 발음과 전달력 높이기', '전문 용어와 복합 문장을 정확하게 발음해 청중에게 핵심 내용을 전달합니다.')
)
INSERT INTO course_steps(course_id, practice_content_id, step_type, step_order, title, body, required)
SELECT course.id, NULL, 'THEORY', 1, source.theory_title, source.theory_body, TRUE
FROM step_source source
JOIN courses course ON course.course_type = 'PRONUNCIATION' AND course.title = source.course_title
WHERE NOT EXISTS (
    SELECT 1 FROM course_steps step
    WHERE step.course_id = course.id AND step.step_order = 1
);

WITH step_source(course_title, practice_title, practice_title_text) AS (
    VALUES
        ('발음 기초 클래스', '발음 초급 연습 1 - 모음 또렷하게', '기본 발음 녹음하기'),
        ('발음 연결 클래스', '발음 중급 연습 1 - 연음 연결', '연결 발음 녹음하기'),
        ('발음 발표 클래스', '발음 고급 연습 1 - 전문 용어', '발표 발음 녹음하기')
)
INSERT INTO course_steps(course_id, practice_content_id, step_type, step_order, title, body, required)
SELECT course.id, content.id, 'PRACTICE', 2, source.practice_title_text, NULL, TRUE
FROM step_source source
JOIN courses course ON course.course_type = 'PRONUNCIATION' AND course.title = source.course_title
JOIN practice_contents content
  ON content.owner_id IS NULL
 AND content.content_type = 'CLASS_PRACTICE'
 AND content.category = 'PRONUNCIATION_CLASS'
 AND content.title = source.practice_title
 AND content.status = 'PUBLISHED'
WHERE NOT EXISTS (
    SELECT 1 FROM course_steps step
    WHERE step.course_id = course.id AND step.step_order = 2
);

INSERT INTO course_step_revisions(step_id, revision, title, subtitle, step_order, step_type, blocks_json)
SELECT step.id, 1, step.title,
       CASE WHEN step.step_type = 'THEORY' THEN '발음 학습 개념' ELSE '녹음 연습' END,
       step.step_order, step.step_type,
       CASE
           WHEN step.step_type = 'THEORY' THEN jsonb_build_array(jsonb_build_object('type', 'TEXT', 'body', step.body))::text
           ELSE jsonb_build_array(jsonb_build_object('type', 'PRACTICE_PROMPT', 'practiceContentId', step.practice_content_id))::text
       END
FROM course_steps step
JOIN courses course ON course.id = step.course_id
WHERE course.course_type = 'PRONUNCIATION'
  AND course.title IN ('발음 기초 클래스', '발음 연결 클래스', '발음 발표 클래스')
  AND NOT EXISTS (
      SELECT 1 FROM course_step_revisions revision
      WHERE revision.step_id = step.id AND revision.revision = 1
  );

WITH example_sets(course_title) AS (
    VALUES ('발음 기초 클래스'), ('발음 연결 클래스'), ('발음 발표 클래스')
)
INSERT INTO practice_example_sets(step_id, revision, education_revision_id, published_at)
SELECT step.id, 1, revision.id, now()
FROM example_sets source
JOIN courses course ON course.course_type = 'PRONUNCIATION' AND course.title = source.course_title
JOIN course_steps step ON step.course_id = course.id AND step.step_type = 'PRACTICE' AND step.step_order = 2
JOIN course_step_revisions revision ON revision.step_id = step.id AND revision.revision = 1
WHERE NOT EXISTS (
    SELECT 1 FROM practice_example_sets existing
    WHERE existing.step_id = step.id AND existing.revision = 1
);

WITH example_items(course_title, example_order, practice_title, hint) AS (
    VALUES
        ('발음 기초 클래스', 1, '발음 초급 연습 1 - 모음 또렷하게', '모음의 입모양을 분명히 하고 문장 끝까지 유지하세요.'),
        ('발음 기초 클래스', 2, '발음 초급 연습 2 - 자음 정확하게', '자음이 겹치는 부분을 서두르지 말고 또렷하게 읽으세요.'),
        ('발음 기초 클래스', 3, '발음 초급 연습 3 - 받침 구분하기', '받침을 생략하지 않고 마지막 음절까지 전달하세요.'),
        ('발음 기초 클래스', 4, '발음 초급 연습 4 - 된소리 안정적으로', '된소리에 힘을 주되 문장 전체의 속도는 유지하세요.'),
        ('발음 기초 클래스', 5, '발음 초급 연습 5 - 문장 끝까지', '마지막 음절의 소리가 작아지지 않도록 마무리하세요.'),
        ('발음 연결 클래스', 1, '발음 중급 연습 1 - 연음 연결', '단어 사이를 자연스럽게 연결하되 핵심 소리는 선명하게 유지하세요.'),
        ('발음 연결 클래스', 2, '발음 중급 연습 2 - 비슷한 소리 구분', '비슷한 자음의 차이를 의식하며 정확하게 읽으세요.'),
        ('발음 연결 클래스', 3, '발음 중급 연습 3 - 겹받침 처리', '겹받침을 한 번에 뭉개지 말고 자연스럽게 연결하세요.'),
        ('발음 연결 클래스', 4, '발음 중급 연습 4 - 속도와 명료도', '속도를 높여도 핵심 단어의 음절을 놓치지 마세요.'),
        ('발음 연결 클래스', 5, '발음 중급 연습 5 - 긴 문장 호흡', '호흡 단위로 나누어 읽되 문장 흐름은 끊지 마세요.'),
        ('발음 발표 클래스', 1, '발음 고급 연습 1 - 전문 용어', '전문 용어의 핵심 음절을 정확하게 강조하세요.'),
        ('발음 발표 클래스', 2, '발음 고급 연습 2 - 복합 문장', '연결 어미와 핵심 단어를 분리해 명료하게 전달하세요.'),
        ('발음 발표 클래스', 3, '발음 고급 연습 3 - 자음군 안정화', '자음이 연속되어도 발화가 흐려지지 않도록 유지하세요.'),
        ('발음 발표 클래스', 4, '발음 고급 연습 4 - 발표 전달력', '청중을 향해 안정적인 음량과 정확한 발음을 유지하세요.'),
        ('발음 발표 클래스', 5, '발음 고급 연습 5 - 자연스러운 마무리', '긴 문장의 마지막까지 호흡과 발음을 안정적으로 유지하세요.')
)
INSERT INTO practice_examples(id, set_id, content_id, example_order, hint, focus, locale)
SELECT CASE source.course_title
           WHEN '발음 기초 클래스' THEN 'pronunciation-beginner-r1-' || source.example_order
           WHEN '발음 연결 클래스' THEN 'pronunciation-intermediate-r1-' || source.example_order
           WHEN '발음 발표 클래스' THEN 'pronunciation-advanced-r1-' || source.example_order
       END,
       example_set.id, content.id, source.example_order, source.hint, 'PRONUNCIATION', 'ko-KR'
FROM example_items source
JOIN courses course ON course.course_type = 'PRONUNCIATION' AND course.title = source.course_title
JOIN course_steps step ON step.course_id = course.id AND step.step_type = 'PRACTICE' AND step.step_order = 2
JOIN practice_example_sets example_set ON example_set.step_id = step.id AND example_set.revision = 1
JOIN practice_contents content
  ON content.owner_id IS NULL
 AND content.content_type = 'CLASS_PRACTICE'
 AND content.category = 'PRONUNCIATION_CLASS'
 AND content.title = source.practice_title
 AND content.status = 'PUBLISHED'
WHERE NOT EXISTS (
    SELECT 1 FROM practice_examples existing
    WHERE existing.set_id = example_set.id AND existing.example_order = source.example_order
);

UPDATE courses
SET estimated_minutes = CASE title
        WHEN '발음 기초 클래스' THEN 18
        WHEN '발음 연결 클래스' THEN 22
        WHEN '발음 발표 클래스' THEN 26
        ELSE estimated_minutes
    END,
    updated_at = now()
WHERE course_type = 'PRONUNCIATION'
  AND title IN ('발음 기초 클래스', '발음 연결 클래스', '발음 발표 클래스');
