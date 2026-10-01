INSERT INTO content_categories(content_type, code, label, sort_order)
VALUES ('SENTENCE', 'EXAMPLE_QUESTION', '예시문제', 10)
ON CONFLICT(content_type, code) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order;

WITH example_questions(content_type, learning_focus, category, difficulty, title, description, script_text, target_pronunciations, estimated_seconds, status, published_at, created_at, updated_at) AS (
    VALUES
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'BEGINNER',
         '초급 예시문제 1 - 맑은 발음',
         '짧은 문장을 또박또박 읽으며 기본 발음과 호흡을 확인합니다.',
         '맑은 하늘 아래에서 아이들이 천천히 걸어갑니다.',
         '["맑은", "하늘", "천천히"]'::jsonb, 8, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'BEGINNER',
         '초급 예시문제 2 - 받침 소리',
         '받침이 있는 단어를 자연스럽게 이어 읽는 연습입니다.',
         '작은 꽃밭 옆에서 고양이가 낮잠을 잡니다.',
         '["작은", "꽃밭", "낮잠"]'::jsonb, 8, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'BEGINNER',
         '초급 예시문제 3 - 또렷한 모음',
         '모음을 분명하게 발음하며 문장 끝을 흐리지 않는 연습입니다.',
         '우리는 오늘 오후에 도서관으로 갑니다.',
         '["우리는", "오후", "도서관"]'::jsonb, 7, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'BEGINNER',
         '초급 예시문제 4 - 기본 속도',
         '쉬운 문장을 일정한 속도로 읽는 연습입니다.',
         '친구가 밝은 목소리로 인사를 건넸습니다.',
         '["친구", "밝은", "인사"]'::jsonb, 7, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'BEGINNER',
         '초급 예시문제 5 - 짧은 문장',
         '짧은 문장의 핵심 단어를 정확히 전달하는 연습입니다.',
         '따뜻한 차 한 잔이 마음을 편하게 합니다.',
         '["따뜻한", "차", "편하게"]'::jsonb, 7, 'PUBLISHED', now(), now(), now()),

        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'INTERMEDIATE',
         '중급 예시문제 1 - 문장 연결',
         '두 절이 이어지는 문장을 자연스럽게 읽는 연습입니다.',
         '회의가 길어졌지만 모두가 차분하게 의견을 정리했습니다.',
         '["회의", "차분하게", "의견"]'::jsonb, 10, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'INTERMEDIATE',
         '중급 예시문제 2 - 된소리 구분',
         '된소리와 예사소리가 섞인 문장을 구분해 읽는 연습입니다.',
         '깨끗한 컵을 꺼내 따뜻한 물을 천천히 따랐습니다.',
         '["깨끗한", "컵", "따뜻한"]'::jsonb, 10, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'INTERMEDIATE',
         '중급 예시문제 3 - 긴 호흡',
         '호흡을 조절하며 중간 길이 문장을 끝까지 안정적으로 읽습니다.',
         '새로운 발표 자료를 준비하면서 핵심 내용을 다시 점검했습니다.',
         '["발표", "핵심", "점검"]'::jsonb, 11, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'INTERMEDIATE',
         '중급 예시문제 4 - 조사 연결',
         '조사와 어미를 흐리지 않고 또렷하게 이어 읽는 연습입니다.',
         '시장에서는 신선한 과일과 채소를 고르는 사람들이 많았습니다.',
         '["시장에서는", "신선한", "사람들"]'::jsonb, 11, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'INTERMEDIATE',
         '중급 예시문제 5 - 리듬 유지',
         '의미 단위별로 끊어 읽으며 발음 리듬을 유지합니다.',
         '갑작스러운 비에도 행사는 예정대로 조용히 진행되었습니다.',
         '["갑작스러운", "예정대로", "진행"]'::jsonb, 10, 'PUBLISHED', now(), now(), now()),

        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'ADVANCED',
         '고급 예시문제 1 - 복합 문장',
         '긴 문장에서 호흡과 발음 정확도를 함께 확인합니다.',
         '기술 변화가 빨라질수록 사람들은 더 정확하고 신뢰할 수 있는 정보를 요구합니다.',
         '["기술", "정확하고", "신뢰"]'::jsonb, 13, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'ADVANCED',
         '고급 예시문제 2 - 추상어 발음',
         '추상적인 표현이 포함된 문장을 명확하게 전달하는 연습입니다.',
         '공정한 소통은 서로의 관점을 존중하고 차이를 설명하는 과정에서 시작됩니다.',
         '["공정한", "소통", "관점"]'::jsonb, 13, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'ADVANCED',
         '고급 예시문제 3 - 정보 전달',
         '정보량이 많은 문장을 안정적인 속도로 읽는 연습입니다.',
         '전문가는 복잡한 문제를 단순하게 설명하면서도 중요한 근거를 빠뜨리지 않았습니다.',
         '["전문가", "복잡한", "근거"]'::jsonb, 14, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'ADVANCED',
         '고급 예시문제 4 - 긴장 완화',
         '긴 문장을 읽을 때 긴장으로 인한 발음 뭉개짐을 줄이는 연습입니다.',
         '예상하지 못한 질문을 받았을 때에도 침착하게 생각을 정리해 대답해야 합니다.',
         '["예상하지", "침착하게", "대답"]'::jsonb, 14, 'PUBLISHED', now(), now(), now()),
        ('SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', 'ADVANCED',
         '고급 예시문제 5 - 발표 문장',
         '발표 상황에서 문장 끝까지 힘을 유지하는 연습입니다.',
         '오늘 발표에서는 서비스의 핵심 가치와 향후 개선 방향을 구체적으로 말씀드리겠습니다.',
         '["발표", "핵심 가치", "구체적으로"]'::jsonb, 14, 'PUBLISHED', now(), now(), now())
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
FROM example_questions q
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents p
    WHERE p.owner_id IS NULL
      AND p.content_type = q.content_type
      AND p.category = q.category
      AND p.title = q.title
);
