-- Public sentence filters use stable codes; Korean labels are display metadata.
INSERT INTO content_categories(content_type, code, label, sort_order)
VALUES
    ('SENTENCE', 'FINAL_CONSONANT', '받침', 1),
    ('SENTENCE', 'TENSE_CONSONANT', '된소리', 2),
    ('SENTENCE', 'CONSONANT', '자음', 3),
    ('SENTENCE', 'VOWEL', '모음', 4)
ON CONFLICT(content_type, code) DO UPDATE
SET label = EXCLUDED.label, sort_order = EXCLUDED.sort_order;

-- Keep existing example questions, exam scripts and class content unchanged.
-- Use generated IDs so references and existing content IDs remain intact.
WITH sentences(category, difficulty, title, description, script_text, target_pronunciations, estimated_seconds) AS (
    VALUES
        ('FINAL_CONSONANT', 'BEGINNER', '받침 문장 연습 01 - 입술 닫기',
         'ㅁ과 ㅂ 받침 뒤에 불필요한 모음을 덧붙이지 않고 읽습니다.',
         '밤이 되면 따뜻한 밥을 먹고 마음을 편히 쉽니다.',
         '["밤", "밥", "마음"]'::jsonb, 8),
        ('FINAL_CONSONANT', 'BEGINNER', '받침 문장 연습 02 - 혀끝 움직임',
         'ㄴ과 ㄹ 받침이 들어간 단어를 빠뜨리지 않고 읽습니다.',
         '긴 길을 걸은 우리는 작은 문 앞에서 잠시 멈췄습니다.',
         '["긴", "길을", "문"]'::jsonb, 9),
        ('FINAL_CONSONANT', 'INTERMEDIATE', '받침 문장 연습 03 - 받침과 조사',
         '받침 뒤에 모음으로 시작하는 조사가 붙은 표현을 이어 읽습니다.',
         '옷을 챙긴 뒤 꽃이 핀 길을 따라 집으로 돌아왔습니다.',
         '["옷을", "꽃이", "집으로"]'::jsonb, 9),
        ('FINAL_CONSONANT', 'INTERMEDIATE', '받침 문장 연습 04 - 단어 끝 마무리',
         '받침이 연속해서 나와도 속도를 높이지 않고 단어를 마무리합니다.',
         '책 한 권과 연필 한 자루를 작은 상자 속에 넣었습니다.',
         '["책", "권과", "속에"]'::jsonb, 9),
        ('FINAL_CONSONANT', 'ADVANCED', '받침 문장 연습 05 - 문장 속 연결',
         '복잡한 받침이 포함된 표현을 의미 단위로 나누어 읽습니다.',
         '넓은 마당에서 읽던 책을 덮고 맑은 하늘을 바라봤습니다.',
         '["넓은", "읽던", "맑은"]'::jsonb, 10),
        ('CONSONANT', 'BEGINNER', '자음 문장 연습 01 - 입술 소리',
         'ㅁ, ㅂ, ㅍ을 발음할 때 입술의 움직임을 구분합니다.',
         '미나는 바구니에 포도와 복숭아를 담아 부모님께 드렸습니다.',
         '["미나는", "바구니", "포도"]'::jsonb, 9),
        ('CONSONANT', 'BEGINNER', '자음 문장 연습 02 - 혀끝 소리',
         'ㄴ, ㄷ, ㅌ, ㄹ이 포함된 단어를 또렷하게 읽습니다.',
         '누나는 다리를 건너 토요일마다 도서관에 갑니다.',
         '["누나는", "다리를", "토요일마다"]'::jsonb, 8),
        ('CONSONANT', 'INTERMEDIATE', '자음 문장 연습 03 - 예사소리와 거센소리',
         'ㄱ과 ㅋ, ㅂ과 ㅍ이 포함된 표현의 소리를 구분합니다.',
         '가게 앞 카페에서 바나나와 파인애플 주스를 골랐습니다.',
         '["가게", "카페", "바나나", "파인애플"]'::jsonb, 9),
        ('CONSONANT', 'INTERMEDIATE', '자음 문장 연습 04 - 마찰음과 파찰음',
         'ㅅ, ㅈ, ㅊ을 지나치게 세게 내지 않고 분명하게 읽습니다.',
         '수지는 작은 찻잔을 씻고 새 접시에 차를 준비했습니다.',
         '["수지는", "작은", "찻잔", "차를"]'::jsonb, 9),
        ('CONSONANT', 'ADVANCED', '자음 문장 연습 05 - 여러 자음 이어 읽기',
         '서로 다른 자음이 반복되는 긴 문장을 일정한 속도로 읽습니다.',
         '발표자는 필요한 자료를 차례로 펼치고 구체적인 계획을 설명했습니다.',
         '["발표자는", "자료를", "차례로", "구체적인"]'::jsonb, 11),
        ('VOWEL', 'BEGINNER', '모음 문장 연습 01 - 아와 어',
         'ㅏ와 ㅓ가 포함된 단어의 입 모양을 의식하며 읽습니다.',
         '아버지는 아침마다 커다란 거울 앞에서 머리를 빗습니다.',
         '["아버지는", "아침마다", "거울", "머리를"]'::jsonb, 9),
        ('VOWEL', 'BEGINNER', '모음 문장 연습 02 - 오와 우',
         'ㅗ와 ㅜ를 구분하며 입술 모양을 자연스럽게 조절합니다.',
         '오늘 오후 우리는 호수 주변을 천천히 둘러봅니다.',
         '["오늘", "오후", "우리는", "호수"]'::jsonb, 8),
        ('VOWEL', 'INTERMEDIATE', '모음 문장 연습 03 - 으와 이',
         'ㅡ와 ㅣ가 포함된 표현을 흐리지 않고 이어 읽습니다.',
         '지은이는 이른 아침에 그림을 그리고 이름을 적었습니다.',
         '["지은이는", "이른", "그림을", "이름을"]'::jsonb, 9),
        ('VOWEL', 'INTERMEDIATE', '모음 문장 연습 04 - 여러 모음 연결',
         '서로 다른 모음이 이어져도 음절을 빠뜨리지 않고 읽습니다.',
         '아이와 어머니는 오후에 우유를 마시며 이야기를 나눴습니다.',
         '["아이와", "어머니는", "오후에", "우유를"]'::jsonb, 10),
        ('VOWEL', 'ADVANCED', '모음 문장 연습 05 - 긴 문장의 모음',
         '긴 문장에서도 모음과 어미를 분명하게 유지합니다.',
         '여유로운 오후에는 새로운 이야기를 읽으며 우리의 미래를 그려 봅니다.',
         '["여유로운", "새로운", "이야기를", "우리의", "미래를"]'::jsonb, 11)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status, publisher,
    published_at, created_at, updated_at
)
SELECT 'SENTENCE', 'PRONUNCIATION', category, difficulty, title, description,
       script_text, target_pronunciations, estimated_seconds, 'PUBLISHED',
       'speakAi 학습 콘텐츠', now(), now(), now()
FROM sentences source
WHERE NOT EXISTS (
    SELECT 1 FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'SENTENCE'
      AND existing.category = source.category
      AND existing.title = source.title
);
