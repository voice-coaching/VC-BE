-- Replace the old level-based announcer catalog with the ordered read-along set.
-- Keep referenced content rows hidden so historical sessions remain valid.

WITH legacy AS (
    SELECT id
    FROM practice_contents
    WHERE owner_id IS NULL
      AND content_type = 'ANNOUNCER'
      AND category = 'ANNOUNCER_PRACTICE'
      AND title LIKE '아나운서 연습 %'
)
DELETE FROM content_tts_outbox outbox
USING legacy
WHERE outbox.content_id = legacy.id;

WITH legacy AS (
    SELECT id
    FROM practice_contents
    WHERE owner_id IS NULL
      AND content_type = 'ANNOUNCER'
      AND category = 'ANNOUNCER_PRACTICE'
      AND title LIKE '아나운서 연습 %'
)
DELETE FROM reference_audios audio
USING legacy
WHERE audio.content_id = legacy.id;

WITH legacy AS (
    SELECT id
    FROM practice_contents
    WHERE owner_id IS NULL
      AND content_type = 'ANNOUNCER'
      AND category = 'ANNOUNCER_PRACTICE'
      AND title LIKE '아나운서 연습 %'
), jobs AS (
    SELECT job.id
    FROM example_tts_jobs job
    JOIN legacy ON legacy.id = job.content_id
)
DELETE FROM example_audio_approvals approval
USING jobs
WHERE approval.job_id = jobs.id;

WITH legacy AS (
    SELECT id
    FROM practice_contents
    WHERE owner_id IS NULL
      AND content_type = 'ANNOUNCER'
      AND category = 'ANNOUNCER_PRACTICE'
      AND title LIKE '아나운서 연습 %'
)
DELETE FROM example_tts_jobs job
USING legacy
WHERE job.content_id = legacy.id;

UPDATE practice_contents content
SET status = 'HIDDEN',
    updated_at = now()
WHERE content.owner_id IS NULL
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE'
  AND content.title LIKE '아나운서 연습 %'
  AND (
      EXISTS (SELECT 1 FROM training_sessions session WHERE session.content_id = content.id)
      OR EXISTS (SELECT 1 FROM course_steps step WHERE step.practice_content_id = content.id)
      OR EXISTS (SELECT 1 FROM title_exams exam WHERE exam.practice_content_id = content.id)
      OR EXISTS (SELECT 1 FROM title_policies policy WHERE policy.practice_content_id = content.id)
      OR EXISTS (SELECT 1 FROM custom_content_requests custom WHERE custom.content_id = content.id)
  );

DELETE FROM practice_contents content
WHERE content.owner_id IS NULL
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE'
  AND content.title LIKE '아나운서 연습 %'
  AND NOT EXISTS (SELECT 1 FROM training_sessions session WHERE session.content_id = content.id)
  AND NOT EXISTS (SELECT 1 FROM course_steps step WHERE step.practice_content_id = content.id)
  AND NOT EXISTS (SELECT 1 FROM title_exams exam WHERE exam.practice_content_id = content.id)
  AND NOT EXISTS (SELECT 1 FROM title_policies policy WHERE policy.practice_content_id = content.id)
  AND NOT EXISTS (SELECT 1 FROM custom_content_requests custom WHERE custom.content_id = content.id);

DELETE FROM reference_audios audio
USING practice_contents content
WHERE audio.content_id = content.id
  AND content.owner_id IS NULL
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE'
  AND content.title LIKE '아나운서 따라 읽기 %';

DELETE FROM content_tts_outbox outbox
USING practice_contents content
WHERE outbox.content_id = content.id
  AND content.owner_id IS NULL
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE'
  AND content.title LIKE '아나운서 따라 읽기 %';

WITH announcer_contents(content_title, difficulty, script_text, estimated_seconds) AS (
    VALUES
        ('아나운서 따라 읽기 01', 'BEGINNER', '오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다. 특히 출근 시간대에는 찬바람까지 강하게 불면서 체감온도가 크게 떨어졌습니다. 기상청은 당분간 아침저녁으로 쌀쌀한 날씨가 이어질 것으로 전망했습니다.', 32),
        ('아나운서 따라 읽기 02', 'BEGINNER', '최근 낮과 밤의 기온 차가 크게 벌어지면서 감기 환자도 빠르게 늘고 있습니다. 전문가들은 충분한 수면과 규칙적인 식사를 통해 면역력을 관리해야 한다고 설명했습니다. 외출 후에는 손을 깨끗하게 씻는 것도 꼭 지켜야 할 생활 수칙이라고 강조했습니다.', 34),
        ('아나운서 따라 읽기 03', 'BEGINNER', '정부가 오늘 발표한 조사 결과에서 지역별 소비 회복 속도에 상당한 차이가 나타났습니다. 특히 수도권과 일부 지방 도시 사이에서 서비스업 매출 증가 폭이 다르게 나타났습니다. 정부는 지역별로 큰 차이가 발생한 원인을 추가로 분석할 계획입니다.', 32),
        ('아나운서 따라 읽기 04', 'BEGINNER', '밤사이 남부지방을 중심으로 시간당 50밀리미터가 넘는 강한 비가 내렸습니다. 일부 도로가 침수되면서 차량 통행이 제한됐고, 주민들이 긴급 대피하기도 했습니다. 소방당국은 추가 피해가 발생하지 않도록 위험 지역에 접근을 자제해 달라고 당부했습니다.', 34),
        ('아나운서 따라 읽기 05', 'INTERMEDIATE', '오늘 전국 대부분 지역의 아침 기온이 전날보다 크게 낮아졌습니다. 강원 산간 지역에서는 일부 지역의 최저 기온이 영하권까지 내려가기도 했습니다. 기온이 갑자기 떨어져 건강 관리에 각별한 주의가 필요합니다.', 30),
        ('아나운서 따라 읽기 06', 'INTERMEDIATE', '추석 연휴를 하루 앞두고 주요 기차역과 버스터미널에는 귀성객들의 발길이 이어지고 있습니다. 일부 인기 노선은 오전부터 승차권이 대부분 매진된 것으로 확인됐습니다. 철도공사는 열차가 예정된 시각에 출발할 수 있도록 안전 점검을 강화했습니다.', 34),
        ('아나운서 따라 읽기 07', 'INTERMEDIATE', '오늘 오후 서울 도심의 한 상가 건물에서 화재가 발생해 소방당국이 진화 작업을 벌였습니다. 신고를 받고 출동한 소방대원들은 건물 내부에 있던 시민들을 우선 대피시켰습니다. 불길이 빠르게 번질 가능성에 대비해 인근 도로도 한동안 통제됐습니다.', 35),
        ('아나운서 따라 읽기 08', 'ADVANCED', '정부가 오늘 새로운 청년 주거 지원 정책의 세부 내용을 공개했습니다. 지원 대상과 신청 방법은 다음 달부터 각 지방자치단체 홈페이지에서 확인할 수 있습니다. 정부는 국민들이 쉽게 이해할 수 있도록 짧은 안내 영상을 함께 제공할 예정입니다.', 33),
        ('아나운서 따라 읽기 09', 'ADVANCED', '전국적으로 건조한 날씨가 이어지면서 산불 위험도 크게 높아지고 있습니다. 산림청은 등산객들에게 산에서 화기를 사용하지 말고 지정된 탐방로를 이용해 달라고 요청했습니다. 관계자는 작은 불씨도 대형 산불로 이어질 수 있다며 각별한 주의를 당부한다고 말씀했습니다.', 35),
        ('아나운서 따라 읽기 10', 'ADVANCED', '국제회의 개막을 하루 앞두고 행사장 주변에서는 막바지 점검 작업이 진행되고 있습니다. 경찰은 주요 도로의 교통을 통제하고 참가자들의 이동 동선을 확인하고 있습니다. 조직위원회도 예정된 행사가 차질 없이 진행되도록 모든 준비를 마쳤다고 밝혔습니다.', 32)
)
UPDATE practice_contents content
SET difficulty = source.difficulty,
    script_text = source.script_text,
    description = '전문 아나운서의 발음을 들으며 원고를 순서대로 따라 읽는 연습입니다.',
    target_pronunciations = '[]'::jsonb,
    estimated_seconds = source.estimated_seconds,
    status = 'PUBLISHED',
    published_at = COALESCE(content.published_at, now()),
    updated_at = now()
FROM announcer_contents source
WHERE content.owner_id IS NULL
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE'
  AND content.title = source.content_title;

WITH audio_files(content_title, object_key) AS (
    VALUES
        ('아나운서 따라 읽기 01', 'reference-audios/announcer/오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다..m4a'),
        ('아나운서 따라 읽기 02', 'reference-audios/announcer/최근 낮과 밤의 기온 차가 크게 벌어지면서 감기 환자도 빠르게 늘고 있습니다..m4a'),
        ('아나운서 따라 읽기 03', 'reference-audios/announcer/정부가 오늘 발표한 조사 결과에서 지역별 소비 회복 속도에 상당한 차이가 나타났습니다..m4a'),
        ('아나운서 따라 읽기 04', 'reference-audios/announcer/밤사이 남부지방을 중심으로 시간당 50밀리미터가 넘는 강한 비가 내렸습니다..m4a'),
        ('아나운서 따라 읽기 05', 'reference-audios/announcer/오늘 전국 대부분 지역의 아침 기온이 전날보다 크게 낮아졌습니다..m4a'),
        ('아나운서 따라 읽기 06', 'reference-audios/announcer/추석 연휴를 하루 앞두고 주요 기차역과 버스터미널에는 귀성객들의 발길이 이어지고 있습니다..m4a'),
        ('아나운서 따라 읽기 07', 'reference-audios/announcer/오늘 오후 서울 도심의 한 상가 건물에서 화재가 발생해 소방당국이 진화 작업을 벌였습니다..m4a'),
        ('아나운서 따라 읽기 08', 'reference-audios/announcer/정부가 오늘 새로운 청년 주거 지원 정책의 세부 내용을 공개했습니다..m4a'),
        ('아나운서 따라 읽기 09', 'reference-audios/announcer/전국적으로 건조한 날씨가 이어지면서 산불 위험도 크게 높아지고 있습니다..m4a'),
        ('아나운서 따라 읽기 10', 'reference-audios/announcer/국제회의 개막을 하루 앞두고 행사장 주변에서는 막바지 점검 작업이 진행되고 있습니다..m4a')
)
INSERT INTO reference_audios(
    content_id, speaker_name, speaker_type, audio_url, object_key,
    duration_ms, is_primary, created_at
)
SELECT content.id, '전문 아나운서', 'ANNOUNCER', NULL, audio.object_key,
       NULL, TRUE, now()
FROM audio_files audio
JOIN practice_contents content
  ON content.owner_id IS NULL
 AND content.content_type = 'ANNOUNCER'
 AND content.category = 'ANNOUNCER_PRACTICE'
 AND content.title = audio.content_title
WHERE NOT EXISTS (
    SELECT 1
    FROM reference_audios existing
    WHERE existing.content_id = content.id
      AND existing.speaker_type = 'ANNOUNCER'
      AND existing.object_key = audio.object_key
);

DELETE FROM content_tts_outbox outbox
USING practice_contents content
WHERE outbox.content_id = content.id
  AND content.owner_id IS NULL
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE';
