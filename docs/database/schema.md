# DB Schema - voice

- 데이터베이스: PostgreSQL
- 기준 SQL: `voicebackup(08.08.15.15).sql`
- 덤프 생성 시각: 2026-08-07 15:16:51

## 테이블 목록

### users
- 목적: 서비스 회원 계정과 인증 기본 정보를 저장한다.
- 해당 모듈: user/auth
- Soft delete: `deleted_at`
- Migration: pg_dump `CREATE TABLE public.users`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 사용자 ID |
| email | varchar(255) | N | - | - | Y | 이메일 로그인 식별자 |
| password | varchar(255) | N | - | - | - | 비밀번호 해시 |
| nickname | varchar(50) | Y | - | - | - | 닉네임 |
| status | varchar(20) | Y | - | CHECK | - | 회원 상태 |
| terms_agreed_at | timestamptz | Y | - | - | - | 이용약관 동의 시각 |
| privacy_agreed_at | timestamptz | Y | - | - | - | 개인정보 처리방침 동의 시각 |
| last_login_at | timestamptz | N | - | - | - | 마지막 로그인 시각 |
| created_at | timestamptz | Y | - | - | - | 생성 시각 |
| updated_at | timestamptz | Y | - | - | - | 수정 시각 |
| deleted_at | timestamptz | N | - | - | - | 탈퇴/삭제 시각 |
| role | varchar(63) | Y | - | CHECK | - | 사용자 권한 |

### social_accounts
- 목적: 사용자별 소셜 로그인 계정 연결 정보를 저장한다.
- 해당 모듈: auth/user
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.social_accounts`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 소셜 계정 ID |
| user_id | bigint | Y | - | FK | UQ(user_id, provider) | 사용자 ID |
| provider | varchar(20) | Y | - | CHECK | UQ(provider, provider_user_id), UQ(user_id, provider) | 소셜 제공자 |
| provider_user_id | varchar(255) | Y | - | - | UQ(provider, provider_user_id) | 제공자 사용자 ID |
| provider_email | varchar(255) | N | - | - | - | 제공자 이메일 |
| created_at | timestamptz | Y | - | - | - | 생성 시각 |

### onboarding_profiles
- 목적: 온보딩 설문과 학습 목표 정보를 저장한다.
- 해당 모듈: user/onboarding
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.onboarding_profiles`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 온보딩 프로필 ID |
| user_id | bigint | Y | - | FK | - | 사용자 ID |
| current_level | varchar(30) | N | - | - | - | 현재 수준 |
| goal_text | varchar(500) | N | - | - | - | 학습 목표 |
| daily_goal_minutes | integer | N | - | - | - | 일일 목표 학습 시간 |
| weekly_goal_count | integer | N | - | - | - | 주간 목표 횟수 |
| survey_answers | jsonb | Y | - | - | - | 온보딩 설문 응답 |
| completed_at | timestamptz | N | - | - | - | 온보딩 완료 시각 |
| updated_at | timestamptz | Y | - | - | - | 수정 시각 |

### practice_contents
- 목적: 발음/억양 연습에 사용할 스크립트와 콘텐츠 메타데이터를 저장한다.
- 해당 모듈: practice/content
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.practice_contents`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 연습 콘텐츠 ID |
| content_type | varchar(30) | Y | - | CHECK | - | 콘텐츠 유형 |
| learning_focus | varchar(20) | Y | - | CHECK | - | 학습 초점 |
| category | varchar(50) | N | - | - | - | 콘텐츠 카테고리 |
| title | varchar(200) | Y | - | - | - | 제목 |
| description | varchar(1000) | N | - | - | - | 설명 |
| script_text | text | Y | - | - | - | 연습 스크립트 |
| difficulty | varchar(20) | Y | - | CHECK | - | 난이도 |
| target_pronunciations | jsonb | N | - | - | - | 목표 발음 정보 |
| source_name | varchar(200) | N | - | - | - | 출처 이름 |
| source_url | varchar(1000) | N | - | - | - | 출처 URL |
| news_published_at | timestamptz | N | - | - | - | 뉴스 발행 시각 |
| estimated_seconds | integer | N | - | - | - | 예상 연습 시간(초) |
| status | varchar(20) | Y | - | CHECK | - | 공개 상태 |
| published_at | timestamptz | N | now() | - | - | 게시 시각 |
| created_at | timestamptz | Y | now() | - | - | 생성 시각 |
| updated_at | timestamptz | Y | now() | - | - | 수정 시각 |

### reference_audios
- 목적: 연습 콘텐츠의 모범 음성 파일 정보를 저장한다.
- 해당 모듈: practice/content
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.reference_audios`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 참조 음성 ID |
| content_id | bigint | Y | - | FK | - | 연습 콘텐츠 ID |
| speaker_name | varchar(100) | N | - | - | - | 화자 이름 |
| speaker_type | varchar(30) | N | - | CHECK | - | 화자 유형 |
| audio_url | varchar(1000) | Y | - | - | - | 음성 파일 URL |
| duration_ms | integer | N | - | - | - | 재생 길이(ms) |
| is_primary | boolean | Y | - | - | - | 대표 음성 여부 |
| created_at | timestamptz | Y | now() | - | - | 생성 시각 |

### courses
- 목적: 커리큘럼 단위의 학습 코스를 저장한다.
- 해당 모듈: course
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.courses`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 코스 ID |
| course_type | varchar(20) | Y | - | CHECK | - | 코스 유형 |
| title | varchar(200) | Y | - | - | - | 제목 |
| description | varchar(1000) | N | - | - | - | 설명 |
| difficulty | varchar(20) | Y | - | CHECK | - | 난이도 |
| estimated_minutes | integer | N | - | - | - | 예상 소요 시간(분) |
| status | varchar(20) | Y | - | CHECK | - | 공개 상태 |
| created_at | timestamptz | Y | now() | - | - | 생성 시각 |
| updated_at | timestamptz | Y | now() | - | - | 수정 시각 |

### course_steps
- 목적: 코스 내부 단계와 연결된 연습 콘텐츠를 저장한다.
- 해당 모듈: course
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.course_steps`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 코스 단계 ID |
| course_id | bigint | Y | - | FK | UQ(course_id, step_order) | 코스 ID |
| practice_content_id | bigint | N | - | FK | - | 연결된 연습 콘텐츠 ID |
| step_type | varchar(30) | Y | - | CHECK | - | 단계 유형 |
| step_order | integer | Y | - | - | UQ(course_id, step_order) | 단계 순서 |
| title | varchar(200) | Y | - | - | - | 단계 제목 |
| body | text | N | - | - | - | 단계 본문 |
| required | boolean | Y | - | - | - | 필수 단계 여부 |

### user_course_progress
- 목적: 사용자별 코스 진행률과 마지막 학습 단계를 저장한다.
- 해당 모듈: course/user
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.user_course_progress`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 코스 진행 ID |
| last_step_id | bigint | N | - | FK | - | 마지막 진행 단계 ID |
| course_id | bigint | Y | - | FK | UQ(user_id, course_id) | 코스 ID |
| user_id | bigint | Y | - | FK | UQ(user_id, course_id) | 사용자 ID |
| status | varchar(20) | Y | - | CHECK | - | 진행 상태 |
| progress_percent | numeric(5,2) | Y | 0.00 | - | - | 진행률 |
| started_at | timestamptz | N | now() | - | - | 시작 시각 |
| completed_at | timestamptz | N | now() | - | - | 완료 시각 |
| updated_at | timestamptz | Y | now() | - | - | 수정 시각 |

### training_sessions
- 목적: 사용자의 개별 연습 세션을 저장한다.
- 해당 모듈: training/practice
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.training_sessions`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 학습 세션 ID |
| user_id | bigint | Y | - | FK | - | 사용자 ID |
| content_id | bigint | Y | - | FK | - | 연습 콘텐츠 ID |
| course_step_id | bigint | N | - | FK | - | 코스 단계 ID |
| learning_focus | varchar(20) | Y | - | CHECK | - | 학습 초점 |
| status | varchar(20) | Y | - | CHECK | - | 세션 상태 |
| started_at | timestamptz | Y | now() | - | - | 시작 시각 |
| completed_at | timestamptz | N | - | - | - | 완료 시각 |
| total_learning_seconds | integer | Y | 0 | - | - | 총 학습 시간(초) |
| failure_reason | varchar(500) | N | - | - | - | 실패 사유 |

### voice_recordings
- 목적: 학습 세션 중 업로드된 사용자 음성 녹음 파일과 품질 검사 결과를 저장한다.
- 해당 모듈: recording/training
- Soft delete: `deleted_at`
- Migration: pg_dump `CREATE TABLE public.voice_recordings`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 음성 녹음 ID |
| training_session_id | bigint | Y | - | FK, partial unique index | UQ(training_session_id, attempt_no), UQ selected per session | 학습 세션 ID |
| attempt_no | integer | Y | - | - | UQ(training_session_id, attempt_no) | 녹음 시도 번호 |
| audio_url | varchar(1000) | Y | - | - | - | 음성 파일 URL |
| mime_type | varchar(100) | N | - | - | - | MIME 타입 |
| file_size_bytes | bigint | N | - | - | - | 파일 크기(byte) |
| duration_ms | integer | N | - | - | - | 재생 길이(ms) |
| quality_status | varchar(30) | Y | - | CHECK | - | 품질 검사 상태 |
| volume_score | numeric(5,2) | N | - | - | - | 볼륨 점수 |
| noise_score | numeric(5,2) | N | - | - | - | 노이즈 점수 |
| is_selected | boolean | Y | - | partial unique index | UQ selected per session | 분석 대상으로 선택된 녹음 여부 |
| created_at | timestamptz | Y | now() | - | - | 생성 시각 |
| deleted_at | timestamptz | N | - | - | - | 삭제 시각 |

### analysis_results
- 목적: 선택된 음성 녹음의 STT 및 종합 분석 결과를 저장한다.
- 해당 모듈: analysis/ai
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.analysis_results`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 분석 결과 ID |
| recording_id | bigint | Y | - | FK | Y | 음성 녹음 ID |
| status | varchar(20) | Y | - | CHECK | - | 분석 상태 |
| transcript | text | N | - | - | - | STT 변환 텍스트 |
| stt_confidence | numeric(5,4) | N | - | - | - | STT 신뢰도 |
| stt_model_name | varchar(100) | N | - | - | - | STT 모델명 |
| overall_score | numeric(5,2) | N | - | - | - | 종합 점수 |
| pronunciation_score | numeric(5,2) | N | - | - | - | 발음 점수 |
| intonation_score | numeric(5,2) | N | - | - | - | 억양 점수 |
| speed_wpm | numeric(7,2) | N | - | - | - | 말하기 속도(WPM) |
| speed_status | varchar(30) | N | - | - | - | 속도 상태 |
| stress_score | numeric(5,2) | N | - | - | - | 강세 점수 |
| pause_score | numeric(5,2) | N | - | - | - | 휴지 점수 |
| strengths_text | text | N | - | - | - | 강점 피드백 |
| weaknesses_text | text | N | - | - | - | 개선점 피드백 |
| summary_feedback | text | N | - | - | - | 요약 피드백 |
| analyzed_at | timestamptz | N | now() | - | - | 분석 완료/처리 시각 |
| failure_reason | varchar(500) | N | - | - | - | 실패 사유 |
| created_at | timestamptz | Y | now() | - | - | 생성 시각 |

| feedback_regeneration_count | integer | Y | 0 | - | - | 종합 피드백 재생성 횟수 |
| feedback_regenerated_at | timestamptz | N | - | - | - | 마지막 종합 피드백 재생성 시각 |
| active_request_event_id | varchar(36) | N | - | Index | - | 현재 분석 요청 generation UUID. 이전 재시도 결과를 거부한다. |
| analysis_outcome | varchar(40) | N | - | - | - | 완료된 분석의 근거 제한 outcome |
| failure_code | varchar(100) | N | - | - | - | 내부 안정 실패 코드. public API에는 노출하지 않는다. |
| worker_revision | varchar(100) | N | - | - | - | worker revision receipt |
| pipeline_revision | varchar(100) | N | - | - | - | pipeline revision receipt |
| audio_sha256 | varchar(64) | N | - | - | - | 처리한 audio digest receipt |

### analysis_request_outbox
- 목적: DB 분석 요청과 Redis Stream `XADD` 사이의 at-least-once dispatch를 보장한다.
- 해당 모듈: analysis/training AI integration
- Soft delete: 없음
- Migration: `V3__add_analysis_stream_integration.sql`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | outbox ID |
| event_id | varchar(36) | Y | - | - | Y | request generation UUID |
| analysis_id | bigint | Y | - | FK | - | 대상 `analysis_results.id` |
| payload | text | Y | - | - | - | versioned request JSON |
| status | varchar(20) | Y | - | Index | - | `PENDING`, `PUBLISHED`, `FAILED` |
| attempt_count | integer | Y | 0 | - | - | dispatch attempt count |
| next_attempt_at | timestamptz | Y | now() | Index | - | next retry time |
| last_error_code | varchar(100) | N | - | - | - | stable dispatch failure code |
| created_at | timestamptz | Y | now() | - | - | enqueue time |
| published_at | timestamptz | N | - | - | - | successful XADD time |

### analysis_segments
- 목적: 분석 결과의 문장/구간별 매칭 및 세부 피드백을 저장한다.
- 해당 모듈: analysis/ai
- Soft delete: 없음
- Migration: pg_dump `CREATE TABLE public.analysis_segments`

| Column | Type | Required | Default | Index | Unique | Description |
| --- | --- | --- | --- | --- | --- | --- |
| id | bigint | Y | identity | PK | Y | 분석 세그먼트 ID |
| analysis_result_id | bigint | Y | - | FK | - | 분석 결과 ID |
| sequence_no | integer | Y | - | - | - | 세그먼트 순서 |
| expected_text | varchar(100) | N | - | - | - | 기대 텍스트 |
| recognized_text | varchar(100) | N | - | - | - | 인식 텍스트 |
| start_ms | integer | N | - | - | - | 시작 위치(ms) |
| end_ms | integer | N | - | - | - | 종료 위치(ms) |
| match_type | varchar(30) | Y | - | CHECK | - | 텍스트 매칭 유형 |
| result_status | varchar(30) | Y | - | CHECK | - | 세그먼트 결과 상태 |
| target_unit | varchar(100) | N | - | - | - | 분석 대상 단위 |
| error_type | varchar(50) | N | - | - | - | 오류 유형 |
| pronunciation_score | numeric(5,2) | N | - | - | - | 발음 점수 |
| intonation_score | numeric(5,2) | N | - | - | - | 억양 점수 |
| feedback | varchar(1000) | N | - | - | - | 세부 피드백 |

## 관계

| From | To | Cardinality | Delete Behavior | Notes |
| --- | --- | --- | --- | --- |
| social_accounts.user_id | users.id | N:1 | ON UPDATE CASCADE, ON DELETE CASCADE | 사용자별 provider 중복 불가 |
| onboarding_profiles.user_id | users.id | N:1 | 기본 FK 동작 | 온보딩 프로필의 사용자 참조 |
| training_sessions.user_id | users.id | N:1 | 기본 FK 동작 | 사용자의 학습 세션 |
| user_course_progress.user_id | users.id | N:1 | 기본 FK 동작, NOT VALID | 사용자별 코스 진행 |
| user_course_progress.course_id | courses.id | N:1 | 기본 FK 동작, NOT VALID | 코스별 진행 |
| user_course_progress.last_step_id | course_steps.id | N:1 | 기본 FK 동작, NOT VALID | 마지막 진행 단계 |
| course_steps.course_id | courses.id | N:1 | 기본 FK 동작 | 코스의 단계 |
| course_steps.practice_content_id | practice_contents.id | N:1 | 기본 FK 동작 | 단계와 연습 콘텐츠 연결 |
| reference_audios.content_id | practice_contents.id | N:1 | 기본 FK 동작 | 콘텐츠별 모범 음성 |
| training_sessions.content_id | practice_contents.id | N:1 | 기본 FK 동작 | 세션의 연습 콘텐츠 |
| training_sessions.course_step_id | course_steps.id | N:1 | 기본 FK 동작 | 코스 기반 세션일 때 사용 |
| voice_recordings.training_session_id | training_sessions.id | N:1 | 기본 FK 동작 | 세션별 녹음 시도 |
| analysis_results.recording_id | voice_recordings.id | 1:1 | 기본 FK 동작 | 녹음 1개당 분석 결과 1개 |
| analysis_segments.analysis_result_id | analysis_results.id | N:1 | 기본 FK 동작 | 분석 결과별 세그먼트 |
| analysis_request_outbox.analysis_id | analysis_results.id | N:1 | 기본 FK 동작 | 분석 요청 Stream dispatch 기록 |

## Enum 값

| Table | Column | Value | Meaning |
| --- | --- | --- | --- |
| users | role | USER | 일반 사용자 |
| users | role | ADMIN | 관리자 |
| users | status | ACTIVE | 활성 |
| users | status | SUSPENDED | 정지 |
| users | status | WITHDRAWN | 탈퇴 |
| social_accounts | provider | GOOGLE | Google |
| social_accounts | provider | KAKAO | Kakao |
| social_accounts | provider | NAVER | Naver |
| social_accounts | provider | APPLE | Apple |
| practice_contents | content_type | NEWS | 뉴스 콘텐츠 |
| practice_contents | content_type | SENTENCE | 문장 연습 |
| practice_contents | content_type | ANNOUNCER | 아나운서 연습 |
| practice_contents | content_type | CLASS_PRACTICE | 수업 연습 |
| practice_contents | learning_focus | PRONUNCIATION | 발음 |
| practice_contents | learning_focus | INTONATION | 억양 |
| practice_contents | learning_focus | BOTH | 발음+억양 |
| practice_contents | difficulty | BEGINNER | 초급 |
| practice_contents | difficulty | INTERMEDIATE | 중급 |
| practice_contents | difficulty | ADVANCED | 고급 |
| practice_contents | status | DRAFT | 초안 |
| practice_contents | status | PUBLISHED | 게시 |
| practice_contents | status | HIDDEN | 숨김 |
| reference_audios | speaker_type | ANNOUNCER | 아나운서 |
| reference_audios | speaker_type | COACH | 코치 |
| reference_audios | speaker_type | TTS | TTS 음성 |
| courses | course_type | PRONUNCIATION | 발음 코스 |
| courses | course_type | INTONATION | 억양 코스 |
| courses | difficulty | BEGINNER | 초급 |
| courses | difficulty | INTERMEDIATE | 중급 |
| courses | difficulty | ADVANCED | 고급 |
| courses | status | DRAFT | 초안 |
| courses | status | PUBLISHED | 게시 |
| courses | status | HIDDEN | 숨김 |
| course_steps | step_type | THEORY | 이론 |
| course_steps | step_type | AUDIO_EXAMPLE | 예시 음성 |
| course_steps | step_type | PRACTICE | 연습 |
| course_steps | step_type | RESULT_REVIEW | 결과 리뷰 |
| user_course_progress | status | NOT_STARTED | 시작 전 |
| user_course_progress | status | IN_PROGRESS | 진행 중 |
| user_course_progress | status | COMPLETED | 완료 |
| training_sessions | learning_focus | PRONUNCIATION | 발음 |
| training_sessions | learning_focus | INTONATION | 억양 |
| training_sessions | learning_focus | BOTH | 발음+억양 |
| training_sessions | status | RECORDING | 녹음 중 |
| training_sessions | status | UPLOADING | 업로드 중 |
| training_sessions | status | ANALYZING | 분석 중 |
| training_sessions | status | COMPLETED | 완료 |
| training_sessions | status | FAILED | 실패 |
| training_sessions | status | CANCELED | 취소 |
| voice_recordings | quality_status | PENDING | 품질 검사 대기 |
| voice_recordings | quality_status | PASS | 통과 |
| voice_recordings | quality_status | LOW_VOLUME | 볼륨 낮음 |
| voice_recordings | quality_status | TOO_NOISY | 소음 과다 |
| voice_recordings | quality_status | TOO_SHORT | 길이 부족 |
| voice_recordings | quality_status | NO_SPEECH | 음성 없음 |
| voice_recordings | quality_status | FAILED | 검사 실패 |
| analysis_results | status | PENDING | 분석 대기 |
| analysis_results | status | PROCESSING | 분석 중 |
| analysis_results | status | COMPLETED | 분석 완료 |
| analysis_results | status | FAILED | 분석 실패 |
| analysis_results | analysis_outcome | COACHING_READY | 코칭 가능한 근거가 확인됨 |
| analysis_results | analysis_outcome | COMPLETED_NO_ISSUE | 관찰된 개선 이슈 없음 |
| analysis_results | analysis_outcome | RERECORD_REQUIRED | 내용/품질 사유로 재녹음 필요 |
| analysis_results | analysis_outcome | UNCERTAIN | 근거 부족으로 안전한 결론 불가 |
| analysis_results | analysis_outcome | FAILED_CLOSED | 안전 gate가 fail-closed 됨 |
| analysis_request_outbox | status | PENDING | Stream 발행 대기 |
| analysis_request_outbox | status | PUBLISHED | Stream 발행 완료 |
| analysis_request_outbox | status | FAILED | dispatch 재시도 소진 |
| analysis_segments | match_type | MATCH | 일치 |
| analysis_segments | match_type | SUBSTITUTION | 대체 |
| analysis_segments | match_type | OMISSION | 누락 |
| analysis_segments | match_type | ADDITION | 추가 |
| analysis_segments | result_status | NORMAL | 정상 |
| analysis_segments | result_status | CAUTION | 주의 |
| analysis_segments | result_status | NEEDS_IMPROVEMENT | 개선 필요 |

## 인증 관련 테이블
- 인증/사용자 계정 관련 테이블: `users`, `social_accounts`
- 별도의 `roles`, `sessions`, `tokens`, `audit` 테이블은 현재 덤프에 존재하지 않는다.
- 권한은 `users.role` 컬럼의 CHECK 제약(`USER`, `ADMIN`)으로 관리한다.
- 소셜 로그인 제공자는 `social_accounts.provider` 컬럼의 CHECK 제약(`GOOGLE`, `KAKAO`, `NAVER`, `APPLE`)으로 관리한다.
