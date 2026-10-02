INSERT INTO content_categories(content_type, code, label, sort_order)
VALUES
    ('SENTENCE', 'TITLE_EXAM', '승급시험', 20),
    ('CLASS_PRACTICE', 'INTONATION_CLASS', '억양 클래스', 30)
ON CONFLICT(content_type, code) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order;

WITH title_exam_contents(content_type, learning_focus, category, difficulty, title, description, script_text, target_pronunciations, estimated_seconds, status, published_at, created_at, updated_at) AS (
    VALUES
        ('SENTENCE', 'PRONUNCIATION', 'TITLE_EXAM', 'BEGINNER',
         '승급시험 - 초보',
         '짧은 문장을 안정적인 속도와 또렷한 발음으로 읽는 승급 시험 문장입니다.',
         '밝은 목소리로 오늘의 소식을 차분하게 전합니다.',
         '["밝은", "목소리", "차분하게"]'::jsonb, 8, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'TITLE_EXAM', 'INTERMEDIATE',
         '승급시험 - 동네 아나운서',
         '받침과 문장 연결을 자연스럽게 처리하는지 확인하는 승급 시험 문장입니다.',
         '작은 변화도 꾸준히 기록하면 더 큰 성장을 만들 수 있습니다.',
         '["작은", "꾸준히", "성장"]'::jsonb, 10, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'TITLE_EXAM', 'ADVANCED',
         '승급시험 - 아나운서 지망생',
         '긴 문장에서 호흡과 핵심 단어 전달력을 함께 확인하는 승급 시험 문장입니다.',
         '청중에게 정확한 정보를 전하려면 문장의 끝까지 힘을 유지해야 합니다.',
         '["청중", "정확한", "유지"]'::jsonb, 12, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'TITLE_EXAM', 'ADVANCED',
         '승급시험 - 아나운서',
         '발표 상황에 가까운 긴 문장을 명확하고 안정적으로 전달하는 승급 시험 문장입니다.',
         '예상하지 못한 질문에도 침착하게 핵심을 정리해 신뢰감 있게 답변하겠습니다.',
         '["예상하지", "침착하게", "신뢰감"]'::jsonb, 14, 'PUBLISHED', now(), now(), now())
)
INSERT INTO practice_contents(
    content_type,
    learning_focus,
    category,
    difficulty,
    title,
    description,
    script_text,
    target_pronunciations,
    estimated_seconds,
    status,
    published_at,
    created_at,
    updated_at
)
SELECT
    content_type,
    learning_focus,
    category,
    difficulty,
    title,
    description,
    script_text,
    target_pronunciations,
    estimated_seconds,
    status,
    published_at,
    created_at,
    updated_at
FROM title_exam_contents t
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents p
    WHERE p.owner_id IS NULL
      AND p.content_type = t.content_type
      AND p.category = t.category
      AND p.title = t.title
);

WITH policy_content(target_rank, title) AS (
    VALUES
        ('BEGINNER', '승급시험 - 초보'),
        ('LOCAL_ANNOUNCER', '승급시험 - 동네 아나운서'),
        ('ASPIRING_ANNOUNCER', '승급시험 - 아나운서 지망생'),
        ('ANNOUNCER', '승급시험 - 아나운서')
)
UPDATE title_policies policy
SET practice_content_id = content.id
FROM policy_content mapping
JOIN practice_contents content
  ON content.owner_id IS NULL
 AND content.content_type = 'SENTENCE'
 AND content.category = 'TITLE_EXAM'
 AND content.title = mapping.title
 AND content.status = 'PUBLISHED'
WHERE policy.target_rank = mapping.target_rank;

WITH intonation_contents(content_type, learning_focus, category, difficulty, title, description, script_text, target_pronunciations, estimated_seconds, status, published_at, created_at, updated_at) AS (
    VALUES
        ('CLASS_PRACTICE', 'INTONATION', 'INTONATION_CLASS', 'BEGINNER',
         '억양 초급 연습 - 문장 끝 올림과 내림',
         '짧은 문장에서 평서문과 질문의 끝 억양을 구분합니다.',
         '오늘은 날씨가 참 좋습니다. 지금 바로 시작해 볼까요?',
         '["평서문 내림", "의문문 올림"]'::jsonb, 12, 'PUBLISHED', now(), now(), now()),
        ('CLASS_PRACTICE', 'INTONATION', 'INTONATION_CLASS', 'INTERMEDIATE',
         '억양 중급 연습 - 의미 단위 끊어 읽기',
         '긴 문장을 의미 단위로 나누어 자연스럽게 높낮이를 조절합니다.',
         '회의가 길어졌지만 우리는 중요한 결정을 차분하게 정리했습니다.',
         '["의미 단위", "차분한 하강"]'::jsonb, 14, 'PUBLISHED', now(), now(), now()),
        ('CLASS_PRACTICE', 'INTONATION', 'INTONATION_CLASS', 'ADVANCED',
         '억양 고급 연습 - 강조와 설득',
         '핵심 단어를 살리면서 발표형 문장의 억양 흐름을 유지합니다.',
         '이 서비스의 가장 큰 가치는 사용자가 자신의 목소리를 스스로 이해하도록 돕는 데 있습니다.',
         '["핵심 강조", "발표 흐름"]'::jsonb, 18, 'PUBLISHED', now(), now(), now())
)
INSERT INTO practice_contents(
    content_type,
    learning_focus,
    category,
    difficulty,
    title,
    description,
    script_text,
    target_pronunciations,
    estimated_seconds,
    status,
    published_at,
    created_at,
    updated_at
)
SELECT
    content_type,
    learning_focus,
    category,
    difficulty,
    title,
    description,
    script_text,
    target_pronunciations,
    estimated_seconds,
    status,
    published_at,
    created_at,
    updated_at
FROM intonation_contents c
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents p
    WHERE p.owner_id IS NULL
      AND p.content_type = c.content_type
      AND p.category = c.category
      AND p.title = c.title
);

WITH intonation_courses(course_type, title, description, difficulty, estimated_minutes, status, created_at, updated_at) AS (
    VALUES
        ('INTONATION', '억양 기초 클래스', '문장 끝 억양과 기본 높낮이를 익히는 초급 클래스입니다.', 'BEGINNER', 8, 'PUBLISHED', now(), now()),
        ('INTONATION', '억양 흐름 클래스', '의미 단위별 끊어 읽기와 자연스러운 억양 흐름을 연습합니다.', 'INTERMEDIATE', 10, 'PUBLISHED', now(), now()),
        ('INTONATION', '발표 억양 클래스', '강조와 설득이 필요한 발표형 문장의 억양을 연습합니다.', 'ADVANCED', 12, 'PUBLISHED', now(), now())
)
INSERT INTO courses(
    course_type,
    title,
    description,
    difficulty,
    estimated_minutes,
    status,
    created_at,
    updated_at
)
SELECT
    course_type,
    title,
    description,
    difficulty,
    estimated_minutes,
    status,
    created_at,
    updated_at
FROM intonation_courses c
WHERE NOT EXISTS (
    SELECT 1
    FROM courses existing
    WHERE existing.course_type = c.course_type
      AND existing.title = c.title
);

WITH step_source(course_title, theory_title, theory_body) AS (
    VALUES
        ('억양 기초 클래스',
         '문장 끝 억양 이해하기',
         '평서문은 끝을 안정적으로 내리고, 질문은 끝을 자연스럽게 올려 의미 차이를 전달합니다.'),
        ('억양 흐름 클래스',
         '의미 단위로 흐름 만들기',
         '긴 문장은 한 번에 밀어 읽지 않고 의미 단위마다 짧게 쉬며 높낮이를 조절합니다.'),
        ('발표 억양 클래스',
         '핵심 단어 강조하기',
)
INSERT INTO course_steps(course_id, practice_content_id, step_type, step_order, title, body, required)
SELECT course.id, NULL, 'THEORY', 1, source.theory_title, source.theory_body, TRUE
FROM step_source source
JOIN courses course
  ON course.course_type = 'INTONATION'
 AND course.title = source.course_title
WHERE NOT EXISTS (
    SELECT 1
    FROM course_steps step
    WHERE step.course_id = course.id
      AND step.step_order = 1
);

WITH step_source(course_title, practice_title, practice_title_text) AS (
    VALUES
        ('억양 기초 클래스', '억양 초급 연습 - 문장 끝 올림과 내림', '문장 끝 억양 녹음하기'),
        ('억양 흐름 클래스', '억양 중급 연습 - 의미 단위 끊어 읽기', '의미 단위 억양 녹음하기'),
        ('발표 억양 클래스', '억양 고급 연습 - 강조와 설득', '발표 억양 녹음하기')
)
INSERT INTO course_steps(course_id, practice_content_id, step_type, step_order, title, body, required)
SELECT course.id, content.id, 'PRACTICE', 2, source.practice_title_text, NULL, TRUE
FROM step_source source
JOIN courses course
  ON course.course_type = 'INTONATION'
 AND course.title = source.course_title
JOIN practice_contents content
  ON content.owner_id IS NULL
 AND content.content_type = 'CLASS_PRACTICE'
 AND content.category = 'INTONATION_CLASS'
 AND content.title = source.practice_title
 AND content.status = 'PUBLISHED'
WHERE NOT EXISTS (
    SELECT 1
    FROM course_steps step
    WHERE step.course_id = course.id
      AND step.step_order = 2
);

INSERT INTO course_step_revisions(step_id, revision, title, subtitle, step_order, step_type, blocks_json)
SELECT
    step.id,
    1,
    step.title,
    CASE
        WHEN step.step_type = 'THEORY' THEN '억양 학습 개념'
        ELSE '녹음 연습'
    END,
    step.step_order,
    step.step_type,
    CASE
        WHEN step.step_type = 'THEORY' THEN
            jsonb_build_array(jsonb_build_object('type', 'TEXT', 'body', step.body))::text
        ELSE
            jsonb_build_array(jsonb_build_object('type', 'PRACTICE_PROMPT', 'practiceContentId', step.practice_content_id))::text
    END
FROM course_steps step
JOIN courses course ON course.id = step.course_id
WHERE course.course_type = 'INTONATION'
  AND course.title IN ('억양 기초 클래스', '억양 흐름 클래스', '발표 억양 클래스')
  AND NOT EXISTS (
      SELECT 1
      FROM course_step_revisions revision
      WHERE revision.step_id = step.id
        AND revision.revision = 1
  );
