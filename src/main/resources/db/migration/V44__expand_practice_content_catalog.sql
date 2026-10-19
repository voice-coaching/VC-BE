INSERT INTO content_categories(content_type, code, label, sort_order)
VALUES
    ('NEWS', 'SOCIETY', '사회', 1),
    ('NEWS', 'ECONOMY', '경제', 2),
    ('SENTENCE', 'EXAMPLE_QUESTION', '예시문제', 10),
    ('ANNOUNCER', 'ANNOUNCER_PRACTICE', '아나운서 연습', 10),
    ('CLASS_PRACTICE', 'INTONATION_CLASS', '억양 클래스', 30)
ON CONFLICT(content_type, code) DO UPDATE
SET label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order;

WITH news_contents(category, difficulty, title, description, script_text, target_pronunciations, estimated_seconds) AS (
    VALUES
        ('SOCIETY', 'BEGINNER', '사회 뉴스 연습 1 - 도서관 프로그램',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '지역 도서관이 주민을 위한 저녁 독서 프로그램을 시작했습니다. 참여자는 매주 한 권의 책을 함께 읽고 생각을 나눕니다.',
         '["지역 도서관", "독서 프로그램", "생각을 나눕니다"]'::jsonb, 22),
        ('SOCIETY', 'BEGINNER', '사회 뉴스 연습 2 - 공원 산책로',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '도심 공원의 산책로가 새롭게 정비됐습니다. 시민들은 더 안전하고 편안한 환경에서 산책을 즐길 수 있습니다.',
         '["산책로", "정비됐습니다", "안전하고"]'::jsonb, 20),
        ('SOCIETY', 'BEGINNER', '사회 뉴스 연습 3 - 재활용 교육',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '주민 센터에서 올바른 재활용 방법을 알려 주는 교육이 열렸습니다. 참가자들은 품목별 분리배출 기준을 직접 확인했습니다.',
         '["주민 센터", "재활용", "분리배출"]'::jsonb, 22),
        ('SOCIETY', 'BEGINNER', '사회 뉴스 연습 4 - 안전한 등굣길',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '학교 주변에 안전 표지와 밝은 조명이 추가됐습니다. 학생과 보호자는 한층 편안하게 등굣길을 이용할 수 있게 됐습니다.',
         '["안전 표지", "보호자", "등굣길"]'::jsonb, 22),
        ('SOCIETY', 'INTERMEDIATE', '사회 뉴스 연습 5 - 마을 돌봄 공간',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '지역 주민이 함께 운영하는 돌봄 공간이 문을 열었습니다. 이곳에서는 방과 후 학습과 문화 활동을 연계한 프로그램이 진행됩니다.',
         '["함께 운영하는", "방과 후", "연계한"]'::jsonb, 24),
        ('SOCIETY', 'INTERMEDIATE', '사회 뉴스 연습 6 - 대중교통 안내',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '버스 정류장의 실시간 도착 안내 장치가 확대 설치됩니다. 관계자는 교통 정보 접근성이 크게 개선될 것으로 기대했습니다.',
         '["실시간 도착", "확대 설치", "접근성"]'::jsonb, 23),
        ('SOCIETY', 'INTERMEDIATE', '사회 뉴스 연습 7 - 청년 봉사단',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '청년 봉사단이 홀로 지내는 이웃을 위한 생활 지원 활동을 시작했습니다. 정기적인 방문과 안부 확인도 함께 진행할 계획입니다.',
         '["청년 봉사단", "생활 지원", "정기적인"]'::jsonb, 24),
        ('SOCIETY', 'ADVANCED', '사회 뉴스 연습 8 - 보행 환경 조사',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '시민 조사단은 교차로와 골목길의 보행 환경을 점검하고 개선 의견을 제안했습니다. 수집된 자료는 향후 안전 정책의 기초 자료로 활용됩니다.',
         '["교차로", "개선 의견", "기초 자료"]'::jsonb, 27),
        ('SOCIETY', 'ADVANCED', '사회 뉴스 연습 9 - 문화 공간 개방',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '유휴 공공시설을 지역 문화 공간으로 전환하는 사업이 추진됩니다. 주민이 직접 프로그램 기획에 참여해 공간의 활용도를 높일 예정입니다.',
         '["공공시설", "전환하는", "활용도"]'::jsonb, 27),
        ('SOCIETY', 'ADVANCED', '사회 뉴스 연습 10 - 재난 대응 훈련',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '지역 기관들이 참여한 합동 재난 대응 훈련이 실시됐습니다. 참가자들은 비상 연락망과 대피 절차를 점검하며 기관 간 협력 체계를 확인했습니다.',
         '["합동 재난", "대피 절차", "협력 체계"]'::jsonb, 29),

        ('ECONOMY', 'BEGINNER', '경제 뉴스 연습 1 - 전통시장 장보기',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '전통시장에서 주말 할인 행사가 열렸습니다. 상인들은 신선한 상품과 편리한 결제 서비스를 함께 준비했습니다.',
         '["전통시장", "할인 행사", "결제 서비스"]'::jsonb, 20),
        ('ECONOMY', 'BEGINNER', '경제 뉴스 연습 2 - 지역 상품권',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '지역 상점에서 사용할 수 있는 모바일 상품권이 발행됐습니다. 이용자는 휴대전화로 잔액과 사용 내역을 확인할 수 있습니다.',
         '["모바일 상품권", "휴대전화", "사용 내역"]'::jsonb, 22),
        ('ECONOMY', 'BEGINNER', '경제 뉴스 연습 3 - 소상공인 교육',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '소상공인을 위한 온라인 판매 교육이 시작됐습니다. 교육에서는 상품 촬영과 주문 관리의 기본 방법을 소개합니다.',
         '["소상공인", "온라인 판매", "주문 관리"]'::jsonb, 21),
        ('ECONOMY', 'BEGINNER', '경제 뉴스 연습 4 - 농산물 직거래',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '농가와 소비자를 연결하는 직거래 장터가 운영됩니다. 생산자는 제철 농산물을 소개하고 소비자는 산지 정보를 확인합니다.',
         '["직거래 장터", "생산자", "산지 정보"]'::jsonb, 22),
        ('ECONOMY', 'INTERMEDIATE', '경제 뉴스 연습 5 - 친환경 포장',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '여러 업체가 배송 과정에서 사용하는 포장재를 줄이기로 했습니다. 재사용 가능한 상자를 도입해 비용과 폐기물을 함께 줄일 계획입니다.',
         '["배송 과정", "재사용 가능한", "폐기물"]'::jsonb, 24),
        ('ECONOMY', 'INTERMEDIATE', '경제 뉴스 연습 6 - 창업 상담',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '예비 창업자를 위한 맞춤형 상담 창구가 마련됐습니다. 전문가들은 사업 계획과 자금 운용에 관한 단계별 조언을 제공합니다.',
         '["예비 창업자", "맞춤형 상담", "자금 운용"]'::jsonb, 24),
        ('ECONOMY', 'INTERMEDIATE', '경제 뉴스 연습 7 - 물류 공동화',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '지역 업체들이 배송 차량을 공동으로 운영하는 시범 사업을 시작했습니다. 이동 거리를 줄여 물류비와 탄소 배출을 낮추는 것이 목표입니다.',
         '["공동으로 운영", "이동 거리", "탄소 배출"]'::jsonb, 26),
        ('ECONOMY', 'ADVANCED', '경제 뉴스 연습 8 - 디지털 전환 지원',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '중소기업의 디지털 전환을 지원하는 협력 사업이 확대됩니다. 참여 기업은 생산 관리 시스템을 점검하고 업무 자동화 방안을 설계하게 됩니다.',
         '["중소기업", "디지털 전환", "업무 자동화"]'::jsonb, 27),
        ('ECONOMY', 'ADVANCED', '경제 뉴스 연습 9 - 지역 관광 연계',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '지역 상권과 관광지를 연계한 체류형 프로그램이 마련됐습니다. 방문객의 이동 경로를 다양화해 골목 상권의 매출 증대를 돕는다는 계획입니다.',
         '["체류형 프로그램", "이동 경로", "매출 증대"]'::jsonb, 28),
        ('ECONOMY', 'ADVANCED', '경제 뉴스 연습 10 - 에너지 효율 투자',
         '실제 보도가 아닌 발음 연습용 창작 뉴스입니다.',
         '산업 현장의 에너지 효율을 높이기 위한 설비 투자가 이어지고 있습니다. 기업들은 사용량을 세밀하게 측정해 불필요한 비용을 줄일 방침입니다.',
         '["에너지 효율", "설비 투자", "세밀하게"]'::jsonb, 28)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status, publisher,
    published_at, created_at, updated_at
)
SELECT
    'NEWS', 'BOTH', category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, 'PUBLISHED', 'speakAi 학습 콘텐츠',
    now(), now(), now()
FROM news_contents source
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'NEWS'
      AND existing.category = source.category
      AND existing.title = source.title
);

WITH sentence_contents(difficulty, title, description, script_text, target_pronunciations, estimated_seconds) AS (
    VALUES
        ('BEGINNER', '초급 예시문제 6 - 입술 소리', 'ㅁ, ㅂ, ㅍ 소리를 분명하게 구분합니다.',
         '민수는 파란 모자를 바르게 매만졌습니다.', '["민수", "파란", "매만졌습니다"]'::jsonb, 7),
        ('BEGINNER', '초급 예시문제 7 - 혀끝 소리', 'ㄴ, ㄷ, ㄹ이 이어지는 짧은 문장을 연습합니다.',
         '나리는 다리를 건너 노란 들판을 달렸습니다.', '["나리는", "다리를", "들판"]'::jsonb, 8),
        ('BEGINNER', '초급 예시문제 8 - 시옷 소리', 'ㅅ 소리를 세지 않게 또렷이 발음합니다.',
         '수진이는 새 신발을 신고 산책을 시작했습니다.', '["수진이는", "새 신발", "산책"]'::jsonb, 8),
        ('BEGINNER', '초급 예시문제 9 - 받침 연결', '받침 뒤에 모음이 올 때 자연스럽게 이어 읽습니다.',
         '저녁에는 작은 음악회를 함께 열었습니다.', '["저녁에는", "작은", "음악회"]'::jsonb, 8),
        ('BEGINNER', '초급 예시문제 10 - 문장 마무리', '문장 끝소리를 생략하지 않고 마무리합니다.',
         '우리는 약속한 시간을 정확하게 지켰습니다.', '["약속한", "정확하게", "지켰습니다"]'::jsonb, 8),

        ('INTERMEDIATE', '중급 예시문제 6 - 겹받침', '겹받침이 포함된 단어를 문장 안에서 연습합니다.',
         '넓은 들판을 지나 맑고 짧은 개울을 만났습니다.', '["넓은", "맑고", "짧은"]'::jsonb, 10),
        ('INTERMEDIATE', '중급 예시문제 7 - 유음 연결', '연속되는 ㄹ 소리를 뭉개지 않고 발음합니다.',
         '신뢰로운 결과를 알리려면 자료를 올바르게 분류해야 합니다.', '["신뢰로운", "알리려면", "올바르게"]'::jsonb, 11),
        ('INTERMEDIATE', '중급 예시문제 8 - 비음화', '받침과 비음이 만나는 표현을 자연스럽게 읽습니다.',
         '국민 모두가 작년보다 건강한 봄날을 맞이했습니다.', '["국민", "작년보다", "봄날"]'::jsonb, 10),
        ('INTERMEDIATE', '중급 예시문제 9 - 속도 조절', '쉼표 전후의 속도를 조절하며 핵심을 전달합니다.',
         '준비는 충분했지만, 마지막 점검은 더욱 꼼꼼하게 진행했습니다.', '["충분했지만", "마지막", "꼼꼼하게"]'::jsonb, 11),
        ('INTERMEDIATE', '중급 예시문제 10 - 숫자 전달', '수량과 시각을 포함한 정보를 정확하게 전달합니다.',
         '참가자는 오전 아홉 시까지 세 개의 자료를 제출해야 합니다.', '["오전 아홉 시", "세 개", "제출"]'::jsonb, 11),

        ('ADVANCED', '고급 예시문제 6 - 자음 전환', '다양한 자음이 빠르게 바뀌는 문장을 정확히 읽습니다.',
         '복잡한 협상 과정에서도 객관적 근거를 바탕으로 적극적인 대안을 제시했습니다.', '["복잡한", "객관적", "적극적인"]'::jsonb, 14),
        ('ADVANCED', '고급 예시문제 7 - 논리적 강조', '대조되는 내용을 발음과 호흡으로 구분합니다.',
         '속도를 높이는 것보다 중요한 것은 핵심 정보를 정확하게 전달하는 일입니다.', '["높이는", "중요한", "정확하게"]'::jsonb, 14),
        ('ADVANCED', '고급 예시문제 8 - 발표 도입', '발표의 첫 문장을 안정된 호흡으로 전달합니다.',
         '지금부터 조사 과정에서 발견한 세 가지 문제와 구체적인 해결 방안을 설명드리겠습니다.', '["조사 과정", "세 가지", "해결 방안"]'::jsonb, 15),
        ('ADVANCED', '고급 예시문제 9 - 질문 대응', '복합적인 질문에 답하는 문장을 차분하게 읽습니다.',
         '말씀하신 우려를 충분히 이해하며 검토 결과와 보완 계획을 순서대로 답변드리겠습니다.', '["충분히", "검토 결과", "답변드리겠습니다"]'::jsonb, 15),
        ('ADVANCED', '고급 예시문제 10 - 결론 전달', '긴 문장의 결론에서 발음과 음량을 유지합니다.',
         '여러 가능성을 종합적으로 검토한 결과 지속 가능한 운영 체계를 마련하는 것이 가장 중요합니다.', '["종합적으로", "지속 가능한", "운영 체계"]'::jsonb, 16)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status,
    published_at, created_at, updated_at
)
SELECT
    'SENTENCE', 'PRONUNCIATION', 'EXAMPLE_QUESTION', difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, 'PUBLISHED',
    now(), now(), now()
FROM sentence_contents source
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'SENTENCE'
      AND existing.category = 'EXAMPLE_QUESTION'
      AND existing.title = source.title
);

WITH announcer_contents(difficulty, title, description, script_text, target_pronunciations, estimated_seconds) AS (
    VALUES
        ('BEGINNER', '아나운서 연습 초급 1 - 날씨 안내', '짧은 생활 정보를 밝고 또렷하게 전달합니다.',
         '오늘 낮에는 맑은 하늘이 이어지겠습니다. 외출하실 때에는 가벼운 겉옷을 챙기시기 바랍니다.', '["맑은 하늘", "외출하실", "챙기시기"]'::jsonb, 16),
        ('BEGINNER', '아나운서 연습 초급 2 - 교통 안내', '위치와 이동 정보를 일정한 속도로 읽습니다.',
         '현재 중앙로 주변의 차량 흐름은 원활합니다. 버스를 이용하는 시민께서는 도착 시간을 확인해 주시기 바랍니다.', '["중앙로", "차량 흐름", "도착 시간"]'::jsonb, 17),
        ('BEGINNER', '아나운서 연습 초급 3 - 행사 안내', '행사 일시와 장소를 정확하게 전달합니다.',
         '주민 음악회는 토요일 오후 두 시에 시민 광장에서 열립니다. 참가 신청은 금요일까지 가능합니다.', '["토요일", "오후 두 시", "시민 광장"]'::jsonb, 16),
        ('BEGINNER', '아나운서 연습 초급 4 - 생활 정보', '청자가 바로 이해할 수 있도록 핵심어를 살립니다.',
         '이번 주에는 재활용품 수거 시간이 한 시간 앞당겨집니다. 주민 여러분은 배출 시간을 꼭 확인해 주십시오.', '["재활용품", "한 시간", "확인해"]'::jsonb, 17),
        ('BEGINNER', '아나운서 연습 초급 5 - 프로그램 소개', '친근한 어조로 프로그램 시작을 알립니다.',
         '안녕하십니까. 생활 속 유용한 정보를 전해 드리는 아침 소식 시간입니다.', '["안녕하십니까", "유용한", "아침 소식"]'::jsonb, 12),

        ('INTERMEDIATE', '아나운서 연습 중급 1 - 문화 소식', '고유한 정보가 이어지는 문장을 자연스럽게 읽습니다.',
         '지역 예술가들의 작품을 소개하는 전시가 다음 달까지 이어집니다. 전시장은 평일 오전 열 시부터 관람할 수 있습니다.', '["예술가들의", "다음 달까지", "관람"]'::jsonb, 20),
        ('INTERMEDIATE', '아나운서 연습 중급 2 - 교육 소식', '두 문장의 중심 정보를 구분해 전달합니다.',
         '청소년 진로 체험 교실이 새롭게 문을 열었습니다. 참가자들은 다양한 직업을 알아보고 전문가의 조언을 들을 수 있습니다.', '["진로 체험", "참가자들은", "전문가"]'::jsonb, 21),
        ('INTERMEDIATE', '아나운서 연습 중급 3 - 건강 정보', '주의가 필요한 내용을 신뢰감 있게 전달합니다.',
         '일교차가 큰 날에는 충분한 수분 섭취와 규칙적인 휴식이 필요합니다. 야외 활동 전에는 기온 변화를 확인하시기 바랍니다.', '["일교차", "수분 섭취", "기온 변화"]'::jsonb, 21),
        ('INTERMEDIATE', '아나운서 연습 중급 4 - 경제 동향', '수치 없이도 변화의 흐름이 드러나도록 읽습니다.',
         '온라인 주문이 늘면서 지역 상점의 배송 서비스도 빠르게 달라지고 있습니다. 공동 물류를 활용하는 업체도 점차 증가하는 추세입니다.', '["온라인 주문", "배송 서비스", "공동 물류"]'::jsonb, 23),
        ('INTERMEDIATE', '아나운서 연습 중급 5 - 인터뷰 연결', '인용 내용을 소개하는 연결 문장을 연습합니다.',
         '현장 관계자는 시민의 의견을 세심하게 듣고 개선 과정에 반영하겠다고 밝혔습니다. 자세한 이야기를 직접 들어보겠습니다.', '["현장 관계자", "세심하게", "들어보겠습니다"]'::jsonb, 21),

        ('ADVANCED', '아나운서 연습 고급 1 - 주요 소식', '정보량이 많은 원고의 호흡과 속도를 조절합니다.',
         '오늘 주요 소식입니다. 관계 기관은 교통과 안전, 생활 편의를 아우르는 종합 대책을 발표하고 세부 실행 계획을 순차적으로 공개할 예정입니다.', '["관계 기관", "종합 대책", "순차적으로"]'::jsonb, 25),
        ('ADVANCED', '아나운서 연습 고급 2 - 정책 브리핑', '공식적인 문장을 안정된 어조로 전달합니다.',
         '이번 계획은 현장의 요구를 반영해 지원 절차를 간소화하고 참여 대상을 확대하는 데 중점을 두고 있습니다.', '["현장의 요구", "간소화하고", "중점을"]'::jsonb, 22),
        ('ADVANCED', '아나운서 연습 고급 3 - 긴급 안내', '긴급한 내용도 지나치게 빠르지 않게 전달합니다.',
         '현재 강한 비로 일부 도로의 통행이 제한되고 있습니다. 운전자는 우회 경로를 확인하고 안전거리를 충분히 확보해 주시기 바랍니다.', '["통행이 제한", "우회 경로", "안전거리"]'::jsonb, 23),
        ('ADVANCED', '아나운서 연습 고급 4 - 심층 보도 도입', '배경과 쟁점을 구분하는 도입부를 연습합니다.',
         '빠르게 변화하는 산업 환경 속에서 기업의 대응 방식도 달라지고 있습니다. 오늘은 기술 투자와 인력 양성의 과제를 함께 살펴봅니다.', '["산업 환경", "대응 방식", "인력 양성"]'::jsonb, 24),
        ('ADVANCED', '아나운서 연습 고급 5 - 마무리 멘트', '방송형 문장의 마지막까지 호흡과 음량을 유지합니다.',
         '지금까지 오늘의 주요 소식을 전해 드렸습니다. 정확하고 필요한 정보를 바르게 전달하기 위해 계속 노력하겠습니다.', '["주요 소식", "정확하고", "전달하기"]'::jsonb, 20)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status,
    published_at, created_at, updated_at
)
SELECT
    'ANNOUNCER', 'BOTH', 'ANNOUNCER_PRACTICE', difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, 'PUBLISHED',
    now(), now(), now()
FROM announcer_contents source
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'ANNOUNCER'
      AND existing.category = 'ANNOUNCER_PRACTICE'
      AND existing.title = source.title
);

WITH class_contents(difficulty, title, description, script_text, target_pronunciations, estimated_seconds) AS (
    VALUES
        ('BEGINNER', '억양 초급 연습 2 - 짧은 질문', '짧은 질문의 끝을 자연스럽게 올립니다.',
         '오늘 회의는 세 시에 시작하나요?', '["질문 끝 올림", "세 시"]'::jsonb, 7),
        ('BEGINNER', '억양 초급 연습 3 - 평서문 마무리', '안내 문장의 끝을 안정적으로 내립니다.',
         '신청서는 안내 데스크에 제출하시면 됩니다.', '["안내 어조", "문장 끝 내림"]'::jsonb, 8),
        ('BEGINNER', '억양 초급 연습 4 - 두 문장 비교', '질문과 대답의 끝 억양을 구분합니다.',
         '준비가 모두 끝났나요? 네, 지금 바로 시작할 수 있습니다.', '["질문 올림", "대답 내림"]'::jsonb, 10),
        ('BEGINNER', '억양 초급 연습 5 - 감탄 표현', '감정 표현 뒤에도 문장 마무리를 유지합니다.',
         '정말 반가운 소식이네요! 모두에게 차분히 알려 주세요.', '["감탄 강조", "안내 내림"]'::jsonb, 10),

        ('INTERMEDIATE', '억양 중급 연습 2 - 원인과 결과', '원인과 결과를 의미 단위로 나누어 읽습니다.',
         '비가 많이 내렸기 때문에, 야외 행사는 실내로 장소를 옮겼습니다.', '["원인 구간", "결과 구간"]'::jsonb, 12),
        ('INTERMEDIATE', '억양 중급 연습 3 - 대조 표현', '대조되는 두 내용을 높낮이로 구분합니다.',
         '속도는 조금 느렸지만, 전달하려는 내용은 분명했습니다.', '["대조 전환", "핵심 내림"]'::jsonb, 11),
        ('INTERMEDIATE', '억양 중급 연습 4 - 나열 문장', '나열 항목과 마지막 결론의 억양을 구분합니다.',
         '자료를 확인하고, 의견을 정리하고, 마지막으로 결론을 말씀드리겠습니다.', '["나열 연결", "결론 내림"]'::jsonb, 13),
        ('INTERMEDIATE', '억양 중급 연습 5 - 조건과 요청', '조건절 뒤에 짧게 쉬고 요청을 분명히 전달합니다.',
         '시간이 괜찮으시다면, 회의가 끝난 뒤 잠시 의견을 들려주시겠습니까?', '["조건절 휴지", "요청 올림"]'::jsonb, 13),

        ('ADVANCED', '억양 고급 연습 2 - 핵심 가치 강조', '핵심어에 힘을 주되 전체 흐름을 유지합니다.',
         '우리가 가장 중요하게 생각하는 가치는 정확성과 신뢰입니다.', '["가장 중요하게", "정확성과 신뢰"]'::jsonb, 11),
        ('ADVANCED', '억양 고급 연습 3 - 문제와 대안', '문제 제기와 해결 방안을 다른 흐름으로 전달합니다.',
         '현재 방식에는 분명한 한계가 있습니다. 그래서 우리는 새로운 대안을 제안합니다.', '["문제 제기", "대안 강조"]'::jsonb, 13),
        ('ADVANCED', '억양 고급 연습 4 - 설득형 질문', '청중의 주의를 끄는 질문 뒤에 결론을 강조합니다.',
         '왜 지금 변화해야 할까요? 더 나은 결과를 만들 수 있는 기회가 바로 지금이기 때문입니다.', '["설득형 질문", "결론 강조"]'::jsonb, 15),
        ('ADVANCED', '억양 고급 연습 5 - 발표 마무리', '발표 결론을 안정된 속도와 하강 억양으로 마칩니다.',
         '지금까지의 내용을 바탕으로, 실행 가능한 변화를 함께 만들어 가겠습니다.', '["내용 요약", "마무리 하강"]'::jsonb, 13)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status,
    published_at, created_at, updated_at
)
SELECT
    'CLASS_PRACTICE', 'INTONATION', 'INTONATION_CLASS', difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, 'PUBLISHED',
    now(), now(), now()
FROM class_contents source
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'CLASS_PRACTICE'
      AND existing.category = 'INTONATION_CLASS'
      AND existing.title = source.title
);

WITH example_sets(course_title) AS (
    VALUES
        ('억양 기초 클래스'),
        ('억양 흐름 클래스'),
        ('발표 억양 클래스')
)
INSERT INTO practice_example_sets(step_id, revision, education_revision_id, published_at)
SELECT step.id, 1, revision.id, now()
FROM example_sets source
JOIN courses course
  ON course.course_type = 'INTONATION'
 AND course.title = source.course_title
JOIN course_steps step
  ON step.course_id = course.id
 AND step.step_type = 'PRACTICE'
 AND step.step_order = 2
JOIN course_step_revisions revision
  ON revision.step_id = step.id
 AND revision.revision = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_example_sets existing
    WHERE existing.step_id = step.id
      AND existing.revision = 1
);

WITH example_items(course_title, example_order, practice_title, hint) AS (
    VALUES
        ('억양 기초 클래스', 1, '억양 초급 연습 - 문장 끝 올림과 내림', '평서문과 질문의 끝 억양을 구분해 보세요.'),
        ('억양 기초 클래스', 2, '억양 초급 연습 2 - 짧은 질문', '질문의 마지막 음절을 자연스럽게 올려 보세요.'),
        ('억양 기초 클래스', 3, '억양 초급 연습 3 - 평서문 마무리', '안내 문장의 끝을 안정적으로 내려 보세요.'),
        ('억양 기초 클래스', 4, '억양 초급 연습 4 - 두 문장 비교', '질문과 대답의 끝 억양 차이를 살려 보세요.'),
        ('억양 기초 클래스', 5, '억양 초급 연습 5 - 감탄 표현', '감탄을 살리면서 마지막 안내는 차분히 마무리하세요.'),

        ('억양 흐름 클래스', 1, '억양 중급 연습 - 의미 단위 끊어 읽기', '의미 단위 사이에 짧은 쉼을 넣어 보세요.'),
        ('억양 흐름 클래스', 2, '억양 중급 연습 2 - 원인과 결과', '원인과 결과가 구분되도록 흐름을 바꿔 보세요.'),
        ('억양 흐름 클래스', 3, '억양 중급 연습 3 - 대조 표현', '대조되는 두 내용을 다른 높낮이로 전달해 보세요.'),
        ('억양 흐름 클래스', 4, '억양 중급 연습 4 - 나열 문장', '나열은 이어 읽고 마지막 결론은 내려 마무리하세요.'),
        ('억양 흐름 클래스', 5, '억양 중급 연습 5 - 조건과 요청', '조건절 뒤에 쉬고 요청의 끝을 자연스럽게 올려 보세요.'),

        ('발표 억양 클래스', 1, '억양 고급 연습 - 강조와 설득', '핵심 단어를 강조하면서 발표 흐름을 유지하세요.'),
        ('발표 억양 클래스', 2, '억양 고급 연습 2 - 핵심 가치 강조', '핵심 가치에 힘을 주되 속도는 일정하게 유지하세요.'),
        ('발표 억양 클래스', 3, '억양 고급 연습 3 - 문제와 대안', '문제 제기와 대안 제시의 흐름을 구분하세요.'),
        ('발표 억양 클래스', 4, '억양 고급 연습 4 - 설득형 질문', '질문으로 주의를 끈 뒤 결론을 분명하게 강조하세요.'),
        ('발표 억양 클래스', 5, '억양 고급 연습 5 - 발표 마무리', '결론을 안정된 속도와 하강 억양으로 마치세요.')
)
INSERT INTO practice_examples(id, set_id, content_id, example_order, hint, focus, locale)
SELECT
    CASE source.course_title
        WHEN '억양 기초 클래스' THEN 'intonation-beginner-r1-' || source.example_order
        WHEN '억양 흐름 클래스' THEN 'intonation-intermediate-r1-' || source.example_order
        WHEN '발표 억양 클래스' THEN 'intonation-advanced-r1-' || source.example_order
    END,
    example_set.id,
    content.id,
    source.example_order,
    source.hint,
    'INTONATION',
    'ko-KR'
FROM example_items source
JOIN courses course
  ON course.course_type = 'INTONATION'
 AND course.title = source.course_title
JOIN course_steps step
  ON step.course_id = course.id
 AND step.step_type = 'PRACTICE'
 AND step.step_order = 2
JOIN practice_example_sets example_set
  ON example_set.step_id = step.id
 AND example_set.revision = 1
JOIN practice_contents content
  ON content.owner_id IS NULL
 AND content.content_type = 'CLASS_PRACTICE'
 AND content.category = 'INTONATION_CLASS'
 AND content.title = source.practice_title
 AND content.status = 'PUBLISHED'
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_examples existing
    WHERE existing.set_id = example_set.id
      AND existing.example_order = source.example_order
);

UPDATE courses
SET estimated_minutes = CASE title
        WHEN '억양 기초 클래스' THEN 18
        WHEN '억양 흐름 클래스' THEN 22
        WHEN '발표 억양 클래스' THEN 26
        ELSE estimated_minutes
    END,
    updated_at = now()
WHERE course_type = 'INTONATION'
  AND title IN ('억양 기초 클래스', '억양 흐름 클래스', '발표 억양 클래스');
