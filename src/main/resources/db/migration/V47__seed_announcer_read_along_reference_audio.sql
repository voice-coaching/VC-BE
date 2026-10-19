ALTER TABLE reference_audios
    ADD COLUMN object_key VARCHAR(512);

ALTER TABLE reference_audios
    ALTER COLUMN audio_url DROP NOT NULL;

ALTER TABLE reference_audios
    ADD CONSTRAINT ck_reference_audio_source
        CHECK ((audio_url IS NOT NULL AND length(trim(audio_url)) > 0)
            OR (object_key IS NOT NULL AND length(trim(object_key)) > 0));

WITH announcer_contents(difficulty, content_title, script_text, estimated_seconds) AS (
    VALUES
        ('BEGINNER', '아나운서 따라 읽기 01', '오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다.', 16),
        ('BEGINNER', '아나운서 따라 읽기 02', '오늘 전국 대부분 지역의 아침 기온이 전날보다 크게 낮아졌습니다.', 12),
        ('BEGINNER', '아나운서 따라 읽기 03', '최근 낮과 밤의 기온 차가 크게 벌어지면서 감기 환자도 빠르게 늘고 있습니다.', 15),
        ('BEGINNER', '아나운서 따라 읽기 04', '밤사이 남부지방을 중심으로 시간당 50밀리미터가 넘는 강한 비가 내렸습니다.', 15),
        ('INTERMEDIATE', '아나운서 따라 읽기 05', '전국적으로 건조한 날씨가 이어지면서 산불 위험도 크게 높아지고 있습니다.', 14),
        ('INTERMEDIATE', '아나운서 따라 읽기 06', '오늘 오후 서울 도심의 한 상가 건물에서 화재가 발생해 소방당국이 진화 작업을 벌였습니다.', 17),
        ('INTERMEDIATE', '아나운서 따라 읽기 07', '추석 연휴를 하루 앞두고 주요 기차역과 버스터미널에는 귀성객들의 발길이 이어지고 있습니다.', 17),
        ('ADVANCED', '아나운서 따라 읽기 08', '국제회의 개막을 하루 앞두고 행사장 주변에서는 막바지 점검 작업이 진행되고 있습니다.', 17),
        ('ADVANCED', '아나운서 따라 읽기 09', '정부가 오늘 새로운 청년 주거 지원 정책의 세부 내용을 공개했습니다.', 14),
        ('ADVANCED', '아나운서 따라 읽기 10', '정부가 오늘 발표한 조사 결과에서 지역별 소비 회복 속도에 상당한 차이가 나타났습니다.', 16)
)
INSERT INTO practice_contents(
    content_type, learning_focus, category, difficulty, title, description,
    script_text, target_pronunciations, estimated_seconds, status,
    published_at, created_at, updated_at
)
SELECT
    'ANNOUNCER', 'BOTH', 'ANNOUNCER_PRACTICE', difficulty, content_title,
    '전문 아나운서의 발음을 들으며 원고를 따라 읽는 연습입니다.',
    script_text, '[]'::jsonb, estimated_seconds, 'PUBLISHED',
    now(), now(), now()
FROM announcer_contents source
WHERE NOT EXISTS (
    SELECT 1
    FROM practice_contents existing
    WHERE existing.owner_id IS NULL
      AND existing.content_type = 'ANNOUNCER'
      AND existing.category = 'ANNOUNCER_PRACTICE'
      AND existing.title = source.content_title
);

WITH audio_files(script_text, object_key) AS (
    VALUES
        ('오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다.', 'reference-audios/announcer/오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다..m4a'),
        ('오늘 전국 대부분 지역의 아침 기온이 전날보다 크게 낮아졌습니다.', 'reference-audios/announcer/오늘 전국 대부분 지역의 아침 기온이 전날보다 크게 낮아졌습니다..m4a'),
        ('최근 낮과 밤의 기온 차가 크게 벌어지면서 감기 환자도 빠르게 늘고 있습니다.', 'reference-audios/announcer/최근 낮과 밤의 기온 차가 크게 벌어지면서 감기 환자도 빠르게 늘고 있습니다..m4a'),
        ('밤사이 남부지방을 중심으로 시간당 50밀리미터가 넘는 강한 비가 내렸습니다.', 'reference-audios/announcer/밤사이 남부지방을 중심으로 시간당 50밀리미터가 넘는 강한 비가 내렸습니다..m4a'),
        ('전국적으로 건조한 날씨가 이어지면서 산불 위험도 크게 높아지고 있습니다.', 'reference-audios/announcer/전국적으로 건조한 날씨가 이어지면서 산불 위험도 크게 높아지고 있습니다..m4a'),
        ('오늘 오후 서울 도심의 한 상가 건물에서 화재가 발생해 소방당국이 진화 작업을 벌였습니다.', 'reference-audios/announcer/오늘 오후 서울 도심의 한 상가 건물에서 화재가 발생해 소방당국이 진화 작업을 벌였습니다..m4a'),
        ('추석 연휴를 하루 앞두고 주요 기차역과 버스터미널에는 귀성객들의 발길이 이어지고 있습니다.', 'reference-audios/announcer/추석 연휴를 하루 앞두고 주요 기차역과 버스터미널에는 귀성객들의 발길이 이어지고 있습니다..m4a'),
        ('국제회의 개막을 하루 앞두고 행사장 주변에서는 막바지 점검 작업이 진행되고 있습니다.', 'reference-audios/announcer/국제회의 개막을 하루 앞두고 행사장 주변에서는 막바지 점검 작업이 진행되고 있습니다..m4a'),
        ('정부가 오늘 새로운 청년 주거 지원 정책의 세부 내용을 공개했습니다.', 'reference-audios/announcer/정부가 오늘 새로운 청년 주거 지원 정책의 세부 내용을 공개했습니다..m4a'),
        ('정부가 오늘 발표한 조사 결과에서 지역별 소비 회복 속도에 상당한 차이가 나타났습니다.', 'reference-audios/announcer/정부가 오늘 발표한 조사 결과에서 지역별 소비 회복 속도에 상당한 차이가 나타났습니다..m4a')
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
 AND content.script_text = audio.script_text
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
  AND content.content_type = 'ANNOUNCER'
  AND content.category = 'ANNOUNCER_PRACTICE'
  AND EXISTS (
      SELECT 1 FROM reference_audios audio
      WHERE audio.content_id = content.id
        AND audio.speaker_type = 'ANNOUNCER'
        AND audio.object_key IS NOT NULL
  );
