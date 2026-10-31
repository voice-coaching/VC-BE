# DB Schema - voice

## 기준과 적용 범위

- 기준 백업: 사용자 제공 `voicebackup 10.27.sql` (2026-10-27 스냅샷, DB 이름 `voice`).
- 확장자는 `.sql`이지만 실제 형식은 PostgreSQL custom archive (`PGDMP`)다. `pg_restore`로 SQL을 파일로 추출하여 분석했으며 DB 연결·복원·변경은 수행하지 않았다.
- 덤프의 서버 버전은 PostgreSQL 18.6, pg_dump 버전은 18.3이다.
- 아래 구조는 백업에 존재하는 `public` 스키마의 **60개 테이블** 기준이다. 백업 이후의 운영 변경이나 현재 배포 상태를 보증하지 않는다.
- 백업 SHA-256: `2e37c5909c861f03736082c01ba8c3ef5d4e5ebd5a9b3ca13f76e9bdeeeeed17`.
- 배포 변경의 원본은 `src/main/resources/db/migration/`이다. 이 문서는 백업의 최종 DDL을 설명하며 마이그레이션 실행을 대체하지 않는다.
- 사용자·토큰·동의·개인 원고 등 행 데이터와 저장소 URL은 문서에 복사하지 않는다. 콘텐츠 집계와 아래 공개 콘텐츠 식별자만 기록한다.

## Flyway 적용 이력

- `flyway_schema_history`: V0은 `BASELINE`, V1~V48은 적용 성공(`success=true`). 실패 이력은 없다.
- 기존 문서에서 V38, V40~V42를 미배포·미적용으로 설명하던 내용은 이 백업 시점에는 해당하지 않는다. 적용 이력이 기능별 운영 QA 완료를 의미하지는 않는다.

| 버전 | 변경 | 백업 상태 |
| --- | --- | --- |
| V0 | << Flyway Baseline >> | 성공 |
| V1 | create auth session schema | 성공 |
| V2 | add feedback regeneration tracking | 성공 |
| V3 | add analysis stream integration | 성공 |
| V4 | add recording media integrity | 성공 |
| V5 | persist analysis retry count | 성공 |
| V6 | create processing consent ledger | 성공 |
| V7 | create recording deletion outbox | 성공 |
| V8 | create recording upload intents | 성공 |
| V9 | store pronunciation evidence | 성공 |
| V10 | retain restricted visual analysis input | 성공 |
| V11 | store same attempt visual supplement | 성공 |
| V12 | create analysis cancellation outbox | 성공 |
| V13 | bind analysis outbox to stream retention | 성공 |
| V14 | harden processing consent invariants | 성공 |
| V15 | store closed beta lip observation | 성공 |
| V16 | add runpod http analysis execution state | 성공 |
| V17 | add runpod execution deadline | 성공 |
| V18 | create notices and inquiries | 성공 |
| V19 | create profile images | 성공 |
| V20 | create title exams | 성공 |
| V21 | create course step revisions | 성공 |
| V22 | create custom practice contents | 성공 |
| V23 | add content catalog metadata | 성공 |
| V24 | add notifications | 성공 |
| V25 | add practice examples and audio | 성공 |
| V26 | allow analysis completed recording deletion | 성공 |
| V27 | add clova score evidence | 성공 |
| V28 | release withdrawn login identities | 성공 |
| V29 | add analysis coaching document | 성공 |
| V30 | automate example tts | 성공 |
| V31 | generalize content tts | 성공 |
| V32 | seed example question contents | 성공 |
| V33 | create canonical execution registry | 성공 |
| V34 | create canonical evidence receipts | 성공 |
| V35 | create canonical callback inbox and results | 성공 |
| V36 | create canonical backend journal | 성공 |
| V37 | seed title exam and intonation courses | 성공 |
| V38 | analysis dispatch capacity | 성공 |
| V39 | canonical handoff archive | 성공 |
| V40 | direct analysis history | 성공 |
| V41 | recording media preparations | 성공 |
| V42 | analysis execution media | 성공 |
| V43 | canonical audiovisual v6 | 성공 |
| V44 | expand practice content catalog | 성공 |
| V45 | allow variable practice example count | 성공 |
| V46 | seed pronunciation courses | 성공 |
| V47 | seed announcer read along reference audio | 성공 |
| V48 | reset announcer read along order | 성공 |

## 콘텐츠 데이터와 된소리 조회 확인

백업의 `practice_contents`는 총 **147건**이다. 아래 개수는 게시 예정 시각·소유권을 추가 필터링하기 전, 유형·카테고리·상태별 저장 건수다.

| type | category | status | 건수 |
| --- | --- | --- | ---: |
| ANNOUNCER | ANNOUNCER_PRACTICE | PUBLISHED | 10 |
| CLASS_PRACTICE | FINAL_CONSONANT | PUBLISHED | 5 |
| CLASS_PRACTICE | INTONATION_CLASS | PUBLISHED | 15 |
| CLASS_PRACTICE | PRONUNCIATION_CLASS | PUBLISHED | 15 |
| NEWS | ECONOMY | PUBLISHED | 12 |
| NEWS | SOCIETY | PUBLISHED | 10 |
| SENTENCE | CUSTOM | HIDDEN | 43 |
| SENTENCE | EXAMPLE_QUESTION | PUBLISHED | 30 |
| SENTENCE | TENSE_CONSONANT | HIDDEN | 1 |
| SENTENCE | TENSE_CONSONANT | PUBLISHED | 2 |
| SENTENCE | TITLE_EXAM | PUBLISHED | 4 |

### 된소리 카테고리

- `category=된소리`인 콘텐츠는 **0건**이다. 된소리 분류의 실제 저장 코드는 **`TENSE_CONSONANT`**다.
- `type=SENTENCE AND category=TENSE_CONSONANT`는 3건이며, 공개 소유(`owner_id IS NULL`)·게시 상태는 2건이다. 두 건 모두 백업일 이전에 게시되었다. 숨김 1건은 목록 조회에서 제외된다.

| 공개 콘텐츠 ID | 제목 | category | status |
| ---: | --- | --- | --- |
| 101 | 된소리 ㅆ 연습 | TENSE_CONSONANT | PUBLISHED |
| 9 | 쌀과 콩 문장 연습 | TENSE_CONSONANT | PUBLISHED |

```http
GET /api/practice-contents?type=SENTENCE&category=TENSE_CONSONANT&page=0&size=20
```

- 현재 목록 구현(`ContentCatalogPersistence`)은 `category`를 문자열 완전 일치로 비교한다. 한글 표시명을 코드로 자동 변환하거나 제목을 검색하지 않는다.
- 따라서 한글 `category=된소리` 요청의 빈 목록은 **관련 콘텐츠 부재가 아니라 카테고리 코드 불일치**로 설명된다. 위 수정 요청은 백업과 현재 코드 기준으로 2건이 대상이며 실제 HTTP 응답을 검증한 것은 아니다.
- 「중급 예시문제 2 - 된소리 구분」(ID 32)은 별도 분류 `EXAMPLE_QUESTION`에 속한다. 된소리 관련 제목을 가진 모든 콘텐츠가 `TENSE_CONSONANT`에 속하는 것은 아니다.
- `content_categories`에 `TENSE_CONSONANT` 표시명 등록은 없다. 현재 facets 구현은 미등록 코드의 label을 코드 그대로 반환한다. 프론트는 `GET /api/practice-contents/facets?type=SENTENCE`의 `value`를 조회 조건에 사용해야 한다.

### 등록된 카테고리 표시명

| type | code | label | sort_order |
| --- | --- | --- | ---: |
| ANNOUNCER | ANNOUNCER_PRACTICE | 아나운서 연습 | 10 |
| CLASS_PRACTICE | PRONUNCIATION_CLASS | 발음 클래스 | 20 |
| CLASS_PRACTICE | INTONATION_CLASS | 억양 클래스 | 30 |
| NEWS | SOCIETY | 사회 | 1 |
| NEWS | ECONOMY | 경제 | 2 |
| SENTENCE | EXAMPLE_QUESTION | 예시문제 | 10 |
| SENTENCE | TITLE_EXAM | 승급시험 | 20 |

## 테이블 목록과 소유 모듈

모듈은 코드의 논리적 책임을 의미하며 PostgreSQL 소유 role과 다르다. Soft delete는 삭제 시각 필드의 존재를 기준으로 기재하고, 보관·삭제 허용 범위는 각 CHECK·FK·트리거를 함께 확인한다.

| 테이블 | 담당 모듈 | 목적 | Soft delete 필드 |
| --- | --- | --- | --- |
| [analysis_cancellation_outbox](#analysis_cancellation_outbox) | analysis | 분석 취소 요청과 외부 전달 재시도 | 없음 |
| [analysis_canonical_ack_journal](#analysis_canonical_ack_journal) | analysis | Canonical 수신 확인 저널 | 없음 |
| [analysis_canonical_archive_artifacts](#analysis_canonical_archive_artifacts) | analysis | Canonical 산출물별 아카이브 상태 | 없음 |
| [analysis_canonical_archive_jobs](#analysis_canonical_archive_jobs) | analysis | Canonical 아카이브 작업과 재시도 | 없음 |
| [analysis_canonical_callback_apply](#analysis_canonical_callback_apply) | analysis | Canonical 콜백 반영 상태 | 없음 |
| [analysis_canonical_callback_inbox](#analysis_canonical_callback_inbox) | analysis | Canonical 콜백 수신·검증 이력 | 없음 |
| [analysis_canonical_executions](#analysis_canonical_executions) | analysis | Canonical 실행 등록 및 계약 버전 | 없음 |
| [analysis_canonical_handoff_artifacts](#analysis_canonical_handoff_artifacts) | analysis | 인계 산출물 원본·크기·해시 | 없음 |
| [analysis_canonical_handoffs](#analysis_canonical_handoffs) | analysis | Canonical 결과 인계와 검증·커밋 상태 | 없음 |
| [analysis_canonical_journals](#analysis_canonical_journals) | analysis | Canonical 처리 저널 | 없음 |
| [analysis_canonical_result_effects](#analysis_canonical_result_effects) | analysis | 결과 반영에 따른 후속 효과 처리 | 없음 |
| [analysis_canonical_results](#analysis_canonical_results) | analysis | 검증·커밋한 Canonical 결과 원본 | 없음 |
| [analysis_canonical_upload_journal](#analysis_canonical_upload_journal) | analysis | Canonical 업로드 저널 | 없음 |
| [analysis_dispatch_gates](#analysis_dispatch_gates) | analysis | 엔드포인트별 분석 전송·capacity 대기 제어 | 없음 |
| [analysis_evidence_receipts](#analysis_evidence_receipts) | analysis | 분석 근거 산출물 수신 증빙 | 없음 |
| [analysis_execution_media](#analysis_execution_media) | analysis | 분석 실행에 고정된 미디어 준비 snapshot | 없음 |
| [analysis_request_outbox](#analysis_request_outbox) | analysis | 분석 요청 발행 및 재시도 | 없음 |
| [analysis_results](#analysis_results) | analysis | 녹음별 분석 상태·점수·피드백·실행 식별자 | 없음 |
| [analysis_segments](#analysis_segments) | analysis | 분석 구간과 원문·인식 결과 대응 | 없음 |
| [content_categories](#content_categories) | practicecontent | 콘텐츠 유형별 카테고리 코드·표시명·순서 | 없음 |
| [content_tts_outbox](#content_tts_outbox) | practicecontent | 공개 콘텐츠 TTS 생성 요청 | 없음 |
| [course_step_revisions](#course_step_revisions) | course | 클래스 단계의 교육 내용 revision | 없음 |
| [course_steps](#course_steps) | course | 클래스 단계와 순서 | 없음 |
| [courses](#courses) | course | 학습 클래스와 게시 정보 | 없음 |
| [custom_content_requests](#custom_content_requests) | practicecontent | 사용자 원고 생성 요청의 멱등성 | 없음 |
| [direct_analysis_history](#direct_analysis_history) | analysis | 직접 분석 결과·원고·아카이브 이력 | 없음 |
| [direct_analysis_history_links](#direct_analysis_history_links) | analysis | 사용자와 직접 분석 이력 연결 | 없음 |
| [example_audio_approvals](#example_audio_approvals) | practiceexample | 예문 음성의 승인 이력 | 없음 |
| [example_audio_cache](#example_audio_cache) | practiceexample | 예문·콘텐츠 음성 캐시 메타데이터 | 없음 |
| [example_tts_jobs](#example_tts_jobs) | practiceexample | 예문 음성 합성 작업 | 없음 |
| [example_tts_outbox](#example_tts_outbox) | practiceexample | 예문 음성 합성 발행 대기 | 없음 |
| [example_tts_profiles](#example_tts_profiles) | practiceexample | 음성 합성 프로필과 버전 | 없음 |
| [example_tts_quotas](#example_tts_quotas) | practiceexample | 음성 합성 사용량 한도 | 없음 |
| [flyway_schema_history](#flyway_schema_history) | common / Flyway | 스키마 마이그레이션 적용 이력 | 없음 |
| [inquiries](#inquiries) | support | 사용자 문의와 답변 | 없음 |
| [notices](#notices) | support | 공지사항과 게시 기간 | 없음 |
| [notification_preferences](#notification_preferences) | notification | 사용자 알림 수신 설정 | 없음 |
| [onboarding_profiles](#onboarding_profiles) | onboarding | 온보딩 설문과 학습 목표 | 없음 |
| [practice_contents](#practice_contents) | practicecontent | 공개 연습 콘텐츠와 사용자 비공개 원고 | custom_deleted_at |
| [practice_example_sets](#practice_example_sets) | practiceexample | 클래스 단계별 게시 예문 세트와 revision | 없음 |
| [practice_examples](#practice_examples) | practiceexample | 예문 순서와 실제 연습 콘텐츠 연결 | 없음 |
| [processing_consents](#processing_consents) | training / analysis | 미디어 처리 동의 증빙 | 없음 |
| [profile_images](#profile_images) | profileimage | 프로필 이미지 저장소 메타데이터 | 없음 |
| [push_registration_requests](#push_registration_requests) | notification | Push 등록 요청의 멱등성 이력 | 없음 |
| [push_subscriptions](#push_subscriptions) | notification | 암호화한 Push 구독 정보 | 없음 |
| [recording_deletion_outbox](#recording_deletion_outbox) | training | 녹음 객체 삭제 요청 및 재시도 | deleted_at |
| [recording_media_preparations](#recording_media_preparations) | training | 정규화 미디어 준비 receipt | 없음 |
| [recording_upload_intents](#recording_upload_intents) | training | 녹음 업로드 URL 발급·소비 이력 | 없음 |
| [reference_audios](#reference_audios) | practicecontent | 콘텐츠별 기준 음성 메타데이터 | 없음 |
| [refresh_tokens](#refresh_tokens) | auth | Refresh Token 해시·세션·만료 | 없음 |
| [social_accounts](#social_accounts) | auth | 소셜 계정과 사용자 연결 | 없음 |
| [title_exam_requests](#title_exam_requests) | title | 승급 시험 요청 멱등성 이력 | 없음 |
| [title_exams](#title_exams) | title | 사용자 승급 시험과 평가 결과 | 없음 |
| [title_policies](#title_policies) | title | 칭호 등급별 승급 정책 | 없음 |
| [training_sessions](#training_sessions) | training | 콘텐츠별 학습 세션과 상태 | 없음 |
| [user_course_progress](#user_course_progress) | course | 사용자별 클래스 진도 | 없음 |
| [user_notifications](#user_notifications) | notification | 사용자에게 전달할 알림과 읽음 상태 | 없음 |
| [user_titles](#user_titles) | title | 사용자별 현재 칭호 | 없음 |
| [users](#users) | user | 사용자 계정·권한·탈퇴 상태 | deleted_at |
| [voice_recordings](#voice_recordings) | training | 학습 녹음·미디어 무결성·품질 상태 | deleted_at |

## 테이블 상세

필수는 SQL `NOT NULL` 또는 PK 여부다. `—`는 명시적 기본값·제약이 없음을 뜻한다. UNIQUE는 아래 복합 키와 부분 인덱스 조건까지 함께 해석해야 한다. enum/허용 값과 범위·JSON 제약은 각 테이블의 CHECK 원문에 모두 기재한다.
FK 관계는 하단 관계 표에 모았으며, FK가 없는 식별자 컬럼에 DB 관계를 임의로 가정하지 않는다.

### analysis_cancellation_outbox

- 담당 모듈: `analysis`. 목적: 분석 취소 요청과 외부 전달 재시도.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | nextval('public.analysis_cancellation_outbox_id_seq'::regclass) | PK (analysis_cancellation_outbox_pkey); INDEX idx_analysis_cancellation_outbox_dispatch | 레코드 식별자 |
| request_event_id | character varying(36) | Y | — | UNIQUE (analysis_cancellation_outbox_request_event_id_key) | request_event 식별자 |
| status | character varying(20) | Y | — | INDEX idx_analysis_cancellation_outbox_dispatch | 처리 또는 게시 상태; 허용 값은 CHECK 제약 참조 |
| attempt_count | integer | Y | 0 | — | 처리 시도 횟수 |
| next_attempt_at | timestamp with time zone | Y | now() | INDEX idx_analysis_cancellation_outbox_dispatch | 다음 처리 가능 시각 |
| last_error_code | character varying(100) | N | — | — | 분석 취소 요청과 외부 전달 재시도에 사용하는 `last_error_code` 값 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| published_at | timestamp with time zone | N | — | — | 게시 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_cancellation_outbox_pkey PRIMARY KEY (id);
analysis_cancellation_outbox_request_event_id_key UNIQUE (request_event_id);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_analysis_cancellation_outbox_dispatch ON public.analysis_cancellation_outbox USING btree (status, next_attempt_at, id);
```

### analysis_canonical_ack_journal

- 담당 모듈: `analysis`. 목적: Canonical 수신 확인 저널.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| event_id | uuid | Y | — | PK (analysis_canonical_ack_journal_pkey); FK (analysis_canonical_ack_journal_event_id_fkey) | 이벤트 식별자 |
| disposition | character varying(20) | Y | — | PK (analysis_canonical_ack_journal_pkey) | Canonical 수신 확인 저널에 사용하는 `disposition` 값 |
| ack_bytes | bytea | Y | — | — | Canonical 수신 확인 저널에 사용하는 `ack_bytes` 값 |
| created_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_ack_journal_pkey PRIMARY KEY (event_id, disposition);
CONSTRAINT analysis_canonical_ack_journal_ack_bytes_check CHECK (((octet_length(ack_bytes) >= 1) AND (octet_length(ack_bytes) <= 65536)));
CONSTRAINT analysis_canonical_ack_journal_disposition_check CHECK (((disposition)::text = ANY ((ARRAY['APPLIED'::character varying, 'DUPLICATE'::character varying])::text[])));
```

트리거:

```sql
CREATE TRIGGER canonical_ack_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_ack_journal FOR EACH ROW EXECUTE FUNCTION public.reject_canonical_execution_mutation();
CREATE TRIGGER canonical_ack_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_ack_journal FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
```

### analysis_canonical_archive_artifacts

- 담당 모듈: `analysis`. 목적: Canonical 산출물별 아카이브 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| handoff_id | uuid | Y | — | PK (analysis_canonical_archive_artifacts_pkey); FK (analysis_canonical_archive_artifacts_handoff_id_kind_fkey) | Canonical 인계 식별자 |
| kind | character varying(40) | Y | — | PK (analysis_canonical_archive_artifacts_pkey); FK (analysis_canonical_archive_artifacts_handoff_id_kind_fkey) | 산출물 종류 |
| state | character varying(24) | Y | 'PENDING'::character varying | — | Canonical 산출물별 아카이브 상태에 사용하는 `state` 값 |
| object_key | character varying(1000) | N | — | — | 객체 저장소 키 |
| version_id | character varying(1024) | N | — | — | version 식별자 |
| definitive_rejection | boolean | Y | false CONSTRAINT analysis_canonical_archive_artifa_definitive_rejection_not_null | — | Canonical 산출물별 아카이브 상태에 사용하는 `definitive_rejection` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_archive_artifacts_pkey PRIMARY KEY (handoff_id, kind);
CONSTRAINT analysis_canonical_archive_artifacts_check CHECK ((((state)::text <> ALL ((ARRAY['VERIFYING'::character varying, 'ARCHIVED'::character varying])::text[])) OR ((object_key IS NOT NULL) AND (version_id IS NOT NULL) AND ((version_id)::text <> 'null'::text))));
CONSTRAINT analysis_canonical_archive_artifacts_state_check CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'UPLOADING'::character varying, 'VERIFYING'::character varying, 'ARCHIVED'::character varying, 'RETRY_WAIT'::character varying, 'RECONCILE_REQUIRED'::character varying])::text[])));
```

트리거:

```sql
CREATE TRIGGER archive_checkpoint_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_archive_artifacts FOR EACH ROW EXECUTE FUNCTION public.protect_archive_checkpoint();
CREATE TRIGGER archive_checkpoint_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_archive_artifacts FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
```

### analysis_canonical_archive_jobs

- 담당 모듈: `analysis`. 목적: Canonical 아카이브 작업과 재시도.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| handoff_id | uuid | Y | — | PK (analysis_canonical_archive_jobs_pkey); FK (analysis_canonical_archive_jobs_handoff_id_fkey) | Canonical 인계 식별자 |
| state | character varying(24) | Y | 'PENDING'::character varying | — | Canonical 아카이브 작업과 재시도에 사용하는 `state` 값 |
| claim_id | uuid | N | — | — | 처리 점유 식별자 |
| claim_until | timestamp with time zone | N | — | — | 처리 점유 만료 시각 |
| attempts | integer | Y | 0 | — | 처리 시도 횟수 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 다음 처리 가능 시각 |
| reason_code | character varying(60) | N | — | — | 처리 사유 코드 |
| archived_at | timestamp with time zone | N | — | — | 아카이브 완료 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_archive_jobs_pkey PRIMARY KEY (handoff_id);
CONSTRAINT analysis_canonical_archive_jobs_state_check CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'UPLOADING'::character varying, 'VERIFYING'::character varying, 'ARCHIVED'::character varying, 'RETRY_WAIT'::character varying, 'RECONCILE_REQUIRED'::character varying])::text[])));
```

### analysis_canonical_callback_apply

- 담당 모듈: `analysis`. 목적: Canonical 콜백 반영 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| event_id | uuid | Y | — | PK (analysis_canonical_callback_apply_pkey); FK (analysis_canonical_callback_apply_event_id_fkey) | 이벤트 식별자 |
| state | character varying(20) | Y | 'PENDING'::character varying | — | Canonical 콜백 반영 상태에 사용하는 `state` 값 |
| claim_id | uuid | N | — | — | 처리 점유 식별자 |
| claim_until | timestamp with time zone | N | — | — | 처리 점유 만료 시각 |
| attempts | integer | Y | 0 | — | 처리 시도 횟수 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 다음 처리 가능 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_callback_apply_pkey PRIMARY KEY (event_id);
CONSTRAINT analysis_canonical_callback_apply_attempts_check CHECK ((attempts >= 0));
CONSTRAINT analysis_canonical_callback_apply_check CHECK ((((state)::text = 'APPLYING'::text) = ((claim_id IS NOT NULL) AND (claim_until IS NOT NULL))));
CONSTRAINT analysis_canonical_callback_apply_check1 CHECK ((((state)::text = 'APPLYING'::text) OR ((claim_id IS NULL) AND (claim_until IS NULL))));
CONSTRAINT analysis_canonical_callback_apply_state_check CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLYING'::character varying, 'DONE'::character varying, 'STOPPED'::character varying])::text[])));
```

### analysis_canonical_callback_inbox

- 담당 모듈: `analysis`. 목적: Canonical 콜백 수신·검증 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| event_id | uuid | Y | — | PK (analysis_canonical_callback_inbox_pkey) | 이벤트 식별자 |
| execution_id | uuid | Y | — | UNIQUE (analysis_canonical_callback_inbox_execution_id_key); FK (analysis_canonical_callback_inbox_execution_id_fkey) | 분석 실행 식별자 |
| request_id | uuid | Y | — | — | 요청 식별자 |
| analysis_id | bigint | Y | — | — | 분석 식별자 |
| worker_instance_id | uuid | Y | — | — | worker_instance 식별자 |
| evidence_receipt_id | uuid | N | — | FK (analysis_canonical_callback_inbox_evidence_receipt_id_fkey) | evidence_receipt 식별자 |
| payload_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| raw_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| callback_bytes | bytea | Y | — | — | Canonical 콜백 수신·검증 이력에 사용하는 `callback_bytes` 값 |
| status | character varying(20) | Y | 'PENDING'::character varying | INDEX canonical_callback_pending | 처리 또는 게시 상태; 허용 값은 CHECK 제약 참조 |
| reason_code | character varying(60) | N | — | — | 처리 사유 코드 |
| attempts | integer | Y | 0 | — | 처리 시도 횟수 |
| claim_id | uuid | N | — | — | 처리 점유 식별자 |
| claim_until | timestamp with time zone | N | — | — | 처리 점유 만료 시각 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | INDEX canonical_callback_pending | 다음 처리 가능 시각 |
| registered_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | INDEX canonical_callback_pending | registered 시각 |
| updated_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 수정 시각 |
| verified_at | timestamp with time zone | N | — | — | verified 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_callback_inbox_execution_id_key UNIQUE (execution_id);
analysis_canonical_callback_inbox_pkey PRIMARY KEY (event_id);
CONSTRAINT analysis_canonical_callback_inbox_attempts_check CHECK ((attempts >= 0));
CONSTRAINT analysis_canonical_callback_inbox_callback_bytes_check CHECK (((octet_length(callback_bytes) >= 1) AND (octet_length(callback_bytes) <= 1048576)));
CONSTRAINT analysis_canonical_callback_inbox_check CHECK (((((status)::text = 'VERIFYING'::text) AND (claim_id IS NOT NULL) AND (claim_until IS NOT NULL)) OR (((status)::text <> 'VERIFYING'::text) AND (claim_id IS NULL) AND (claim_until IS NULL))));
CONSTRAINT analysis_canonical_callback_inbox_check1 CHECK ((((status)::text = ANY ((ARRAY['VERIFIED'::character varying, 'APPLIED'::character varying])::text[])) = (verified_at IS NOT NULL)));
CONSTRAINT analysis_canonical_callback_inbox_check2 CHECK (((((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFYING'::character varying, 'VERIFIED'::character varying, 'APPLIED'::character varying])::text[])) AND (reason_code IS NULL)) OR (((status)::text = 'REJECTED'::text) AND ((reason_code)::text = ANY ((ARRAY['EVIDENCE_INVALID'::character varying, 'EVIDENCE_STORAGE_UNAVAILABLE'::character varying])::text[])) AND (reason_code IS NOT NULL)) OR (((status)::text = 'CANCELED'::text) AND ((reason_code)::text = 'EXECUTION_INACTIVE'::text) AND (reason_code IS NOT NULL)) OR (((status)::text = 'EXPIRED'::text) AND ((reason_code)::text = 'DEADLINE_EXCEEDED'::text) AND (reason_code IS NOT NULL))));
CONSTRAINT analysis_canonical_callback_inbox_payload_sha256_check CHECK (((payload_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_callback_inbox_raw_sha256_check CHECK (((raw_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_callback_inbox_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFYING'::character varying, 'VERIFIED'::character varying, 'APPLIED'::character varying, 'REJECTED'::character varying, 'CANCELED'::character varying, 'EXPIRED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX canonical_callback_pending ON public.analysis_canonical_callback_inbox USING btree (next_attempt_at, registered_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFYING'::character varying])::text[]));
```

트리거:

```sql
CREATE TRIGGER canonical_callback_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_callback_inbox FOR EACH ROW EXECUTE FUNCTION public.protect_canonical_callback_inbox();
CREATE TRIGGER canonical_callback_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_callback_inbox FOR EACH STATEMENT EXECUTE FUNCTION public.protect_canonical_callback_inbox();
```

### analysis_canonical_executions

- 담당 모듈: `analysis`. 목적: Canonical 실행 등록 및 계약 버전.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| execution_id | uuid | Y | — | UNIQUE (analysis_canonical_executions_analysis_id_execution_id_key); PK (analysis_canonical_executions_pkey) | 분석 실행 식별자 |
| request_id | uuid | Y | — | UNIQUE (analysis_canonical_executions_request_id_key) | 요청 식별자 |
| analysis_id | bigint | Y | — | UNIQUE (analysis_canonical_executions_analysis_id_execution_id_key) | 분석 식별자 |
| recording_id | bigint | Y | — | — | 녹음 식별자 |
| content_id | bigint | Y | — | — | 콘텐츠 식별자 |
| analysis_profile | character varying(100) | Y | — | — | Canonical 실행 등록 및 계약 버전에 사용하는 `analysis_profile` 값 |
| request_schema_version | character varying(100) | Y | — | — | Canonical 실행 등록 및 계약 버전에 사용하는 `request_schema_version` 값 |
| result_schema_version | character varying(100) | Y | — | — | Canonical 실행 등록 및 계약 버전에 사용하는 `result_schema_version` 값 |
| request_payload_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| script_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| audio_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| deadline_at | timestamp with time zone | Y | — | — | deadline 시각 |
| registered_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | registered 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_executions_analysis_id_execution_id_key UNIQUE (analysis_id, execution_id);
analysis_canonical_executions_pkey PRIMARY KEY (execution_id);
analysis_canonical_executions_request_id_key UNIQUE (request_id);
CONSTRAINT analysis_canonical_executions_analysis_id_check CHECK (((analysis_id >= 1) AND (analysis_id <= '9007199254740991'::bigint)));
CONSTRAINT analysis_canonical_executions_audio_sha256_check CHECK (((audio_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_executions_content_id_check CHECK (((content_id >= 1) AND (content_id <= '9007199254740991'::bigint)));
CONSTRAINT analysis_canonical_executions_recording_id_check CHECK (((recording_id >= 1) AND (recording_id <= '9007199254740991'::bigint)));
CONSTRAINT analysis_canonical_executions_request_payload_sha256_check CHECK (((request_payload_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_executions_script_sha256_check CHECK (((script_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT canonical_execution_profile_tuple CHECK (((((analysis_profile)::text = 'CANONICAL_FROZEN_20260928_V4'::text) AND ((request_schema_version)::text = 'voice-coaching.runpod-analysis-request.v2'::text) AND ((result_schema_version)::text = 'voice-coaching.runpod-analysis-result.v4'::text)) OR (((analysis_profile)::text = 'CANONICAL_HANDOFF_20261004_V5'::text) AND ((request_schema_version)::text = 'voice-coaching.runpod-analysis-request.v3'::text) AND ((result_schema_version)::text = 'voice-coaching.runpod-analysis-result.v5'::text)) OR (((analysis_profile)::text = 'CANONICAL_AUDIOVISUAL_20261007_V6'::text) AND ((request_schema_version)::text = 'voice-coaching.runpod-analysis-request.v4'::text) AND ((result_schema_version)::text = 'voice-coaching.runpod-analysis-result.v6'::text))));
```

트리거:

```sql
CREATE TRIGGER canonical_execution_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_executions FOR EACH ROW EXECUTE FUNCTION public.reject_canonical_execution_mutation();
CREATE TRIGGER canonical_execution_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_executions FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
```

### analysis_canonical_handoff_artifacts

- 담당 모듈: `analysis`. 목적: 인계 산출물 원본·크기·해시.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| handoff_id | uuid | Y | — | PK (analysis_canonical_handoff_artifacts_pkey); FK (analysis_canonical_handoff_artifacts_handoff_id_fkey) | Canonical 인계 식별자 |
| kind | character varying(40) | Y | — | PK (analysis_canonical_handoff_artifacts_pkey) | 산출물 종류 |
| schema_version | character varying(100) | Y | — | — | 계약 스키마 버전 |
| sha256 | character varying(64) | Y | — | — | 원본 SHA-256 |
| byte_size | integer | Y | — | — | 원본 바이트 크기 |
| raw_bytes | bytea | N | — | — | 원본 바이트 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_handoff_artifacts_pkey PRIMARY KEY (handoff_id, kind);
CONSTRAINT analysis_canonical_handoff_artifacts_byte_size_check CHECK (((byte_size >= 1) AND (byte_size <= 16777216)));
CONSTRAINT analysis_canonical_handoff_artifacts_check CHECK (((raw_bytes IS NULL) OR (octet_length(raw_bytes) = byte_size)));
CONSTRAINT analysis_canonical_handoff_artifacts_kind_check CHECK (((kind)::text = ANY ((ARRAY['CORE'::character varying, 'BRIDGE_RESULT'::character varying, 'SELECTION_PROJECTION'::character varying, 'BINDING'::character varying, 'ASSOCIATION'::character varying, 'MEDIA_RECEIPT'::character varying, 'VISUAL_EVIDENCE'::character varying])::text[])));
CONSTRAINT analysis_canonical_handoff_artifacts_sha256_check CHECK (((sha256)::text ~ '^[0-9a-f]{64}$'::text));
```

트리거:

```sql
CREATE TRIGGER handoff_artifact_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_handoff_artifacts FOR EACH ROW EXECUTE FUNCTION public.protect_handoff_artifact();
CREATE TRIGGER handoff_artifact_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_handoff_artifacts FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
```

### analysis_canonical_handoffs

- 담당 모듈: `analysis`. 목적: Canonical 결과 인계와 검증·커밋 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| handoff_id | uuid | Y | — | PK (analysis_canonical_handoffs_pkey) | Canonical 인계 식별자 |
| execution_id | uuid | Y | — | UNIQUE (analysis_canonical_handoffs_execution_id_key); FK (analysis_canonical_handoffs_execution_id_fkey) | 분석 실행 식별자 |
| event_id | uuid | Y | — | UNIQUE (analysis_canonical_handoffs_event_id_key) | 이벤트 식별자 |
| request_id | uuid | Y | — | — | 요청 식별자 |
| analysis_id | bigint | Y | — | — | 분석 식별자 |
| worker_instance_id | uuid | Y | — | — | worker_instance 식별자 |
| metadata_bytes | bytea | Y | — | — | Canonical 결과 인계와 검증·커밋 상태에 사용하는 `metadata_bytes` 값 |
| projection_bytes | bytea | Y | — | — | Canonical 결과 인계와 검증·커밋 상태에 사용하는 `projection_bytes` 값 |
| handoff_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| reserved_bytes | bigint | Y | — | — | Canonical 결과 인계와 검증·커밋 상태에 사용하는 `reserved_bytes` 값 |
| state | character varying(20) | Y | 'STAGING'::character varying | INDEX canonical_handoff_pending | Canonical 결과 인계와 검증·커밋 상태에 사용하는 `state` 값 |
| received_at | timestamp with time zone | N | — | — | received 시각 |
| verified_at | timestamp with time zone | N | — | — | verified 시각 |
| committed_at | timestamp with time zone | N | — | — | committed 시각 |
| created_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 생성 시각 |
| claim_id | uuid | N | — | — | 처리 점유 식별자 |
| claim_until | timestamp with time zone | N | — | — | 처리 점유 만료 시각 |
| attempts | integer | Y | 0 | — | 처리 시도 횟수 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | INDEX canonical_handoff_pending | 다음 처리 가능 시각 |
| reason_code | character varying(60) | N | — | — | 처리 사유 코드 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_handoffs_event_id_key UNIQUE (event_id);
analysis_canonical_handoffs_execution_id_key UNIQUE (execution_id);
analysis_canonical_handoffs_pkey PRIMARY KEY (handoff_id);
CONSTRAINT analysis_canonical_handoffs_check CHECK ((((state)::text = 'VERIFYING'::text) = ((claim_id IS NOT NULL) AND (claim_until IS NOT NULL))));
CONSTRAINT analysis_canonical_handoffs_check1 CHECK ((((state)::text <> ALL ((ARRAY['RECEIVED'::character varying, 'VERIFYING'::character varying, 'COMMITTED'::character varying])::text[])) OR (received_at IS NOT NULL)));
CONSTRAINT analysis_canonical_handoffs_check2 CHECK ((((state)::text <> 'COMMITTED'::text) OR ((verified_at IS NOT NULL) AND (committed_at IS NOT NULL))));
CONSTRAINT analysis_canonical_handoffs_handoff_sha256_check CHECK (((handoff_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_handoffs_metadata_bytes_check CHECK (((octet_length(metadata_bytes) >= 1) AND (octet_length(metadata_bytes) <= 65536)));
CONSTRAINT analysis_canonical_handoffs_projection_bytes_check CHECK (((octet_length(projection_bytes) >= 1) AND (octet_length(projection_bytes) <= 1048576)));
CONSTRAINT analysis_canonical_handoffs_reserved_bytes_check CHECK (((reserved_bytes >= 1) AND (reserved_bytes <= 118620160)));
CONSTRAINT analysis_canonical_handoffs_state_check CHECK (((state)::text = ANY ((ARRAY['STAGING'::character varying, 'RECEIVED'::character varying, 'VERIFYING'::character varying, 'COMMITTED'::character varying, 'REJECTED'::character varying, 'REVOKED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX canonical_handoff_pending ON public.analysis_canonical_handoffs USING btree (next_attempt_at) WHERE ((state)::text = ANY ((ARRAY['RECEIVED'::character varying, 'VERIFYING'::character varying])::text[]));
```

트리거:

```sql
CREATE TRIGGER canonical_handoff_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_handoffs FOR EACH ROW EXECUTE FUNCTION public.protect_canonical_handoff();
CREATE TRIGGER canonical_handoff_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_handoffs FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
```

### analysis_canonical_journals

- 담당 모듈: `analysis`. 목적: Canonical 처리 저널.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| execution_id | uuid | Y | — | PK (analysis_canonical_journals_pkey); FK (analysis_canonical_journals_execution_id_fkey) | 분석 실행 식별자 |
| request_id | uuid | Y | — | UNIQUE (analysis_canonical_journals_request_id_key) | 요청 식별자 |
| analysis_id | bigint | Y | — | — | 분석 식별자 |
| worker_instance_id | uuid | Y | — | — | worker_instance 식별자 |
| event_id | uuid | Y | — | UNIQUE (analysis_canonical_journals_event_id_key) | 이벤트 식별자 |
| worker_revision | character varying(100) | Y | — | — | Canonical 처리 저널에 사용하는 `worker_revision` 값 |
| pipeline_revision | character varying(100) | Y | — | — | Canonical 처리 저널에 사용하는 `pipeline_revision` 값 |
| request_bytes | bytea | Y | — | — | Canonical 처리 저널에 사용하는 `request_bytes` 값 |
| request_raw_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| prepare_bytes | bytea | N | — | — | Canonical 처리 저널에 사용하는 `prepare_bytes` 값 |
| manifest_bytes | bytea | N | — | — | Canonical 처리 저널에 사용하는 `manifest_bytes` 값 |
| producer_started_at | timestamp with time zone | N | — | — | producer_started 시각 |
| registered_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | registered 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_journals_event_id_key UNIQUE (event_id);
analysis_canonical_journals_pkey PRIMARY KEY (execution_id);
analysis_canonical_journals_request_id_key UNIQUE (request_id);
CONSTRAINT analysis_canonical_journals_manifest_bytes_check CHECK (((octet_length(manifest_bytes) >= 1) AND (octet_length(manifest_bytes) <= 65536)));
CONSTRAINT analysis_canonical_journals_prepare_bytes_check CHECK (((octet_length(prepare_bytes) >= 1) AND (octet_length(prepare_bytes) <= 65536)));
CONSTRAINT analysis_canonical_journals_request_bytes_check CHECK (((octet_length(request_bytes) >= 1) AND (octet_length(request_bytes) <= 65536)));
CONSTRAINT analysis_canonical_journals_request_raw_sha256_check CHECK (((request_raw_sha256)::text ~ '^[0-9a-f]{64}$'::text));
```

트리거:

```sql
CREATE TRIGGER canonical_journal_guard BEFORE DELETE OR UPDATE ON public.analysis_canonical_journals FOR EACH ROW EXECUTE FUNCTION public.protect_canonical_journal();
CREATE TRIGGER canonical_journal_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_journals FOR EACH STATEMENT EXECUTE FUNCTION public.protect_canonical_journal();
```

### analysis_canonical_result_effects

- 담당 모듈: `analysis`. 목적: 결과 반영에 따른 후속 효과 처리.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| event_id | uuid | Y | — | PK (analysis_canonical_result_effects_pkey); FK (analysis_canonical_result_effects_event_id_fkey) | 이벤트 식별자 |
| analysis_id | bigint | Y | — | — | 분석 식별자 |
| user_id | bigint | Y | — | — | 사용자 식별자 |
| session_id | bigint | Y | — | — | 세션 식별자 |
| completed_at | timestamp with time zone | N | — | — | completed 시각 |
| claim_id | uuid | N | — | — | 처리 점유 식별자 |
| claim_until | timestamp with time zone | N | — | — | 처리 점유 만료 시각 |
| attempts | integer | Y | 0 | — | 처리 시도 횟수 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 다음 처리 가능 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_result_effects_pkey PRIMARY KEY (event_id);
```

### analysis_canonical_results

- 담당 모듈: `analysis`. 목적: 검증·커밋한 Canonical 결과 원본.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| event_id | uuid | Y | — | PK (analysis_canonical_results_pkey) | 이벤트 식별자 |
| execution_id | uuid | Y | — | UNIQUE (analysis_canonical_results_execution_id_key); FK (analysis_canonical_results_execution_id_fkey) | 분석 실행 식별자 |
| request_id | uuid | Y | — | — | 요청 식별자 |
| analysis_id | bigint | Y | — | — | 분석 식별자 |
| recording_id | bigint | Y | — | — | 녹음 식별자 |
| content_id | bigint | Y | — | — | 콘텐츠 식별자 |
| worker_instance_id | uuid | Y | — | — | worker_instance 식별자 |
| schema_version | character varying(100) | Y | — | — | 계약 스키마 버전 |
| analysis_profile | character varying(100) | Y | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `analysis_profile` 값 |
| status | character varying(20) | Y | — | — | 처리 또는 게시 상태; 허용 값은 CHECK 제약 참조 |
| payload_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| raw_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| callback_bytes | bytea | Y | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `callback_bytes` 값 |
| callback_document | json | Y | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `callback_document` 값 |
| evidence_receipt_id | uuid | N | — | FK (analysis_canonical_results_evidence_receipt_id_fkey) | evidence_receipt 식별자 |
| core_sha256 | character varying(64) | N | — | — | 대상 데이터 SHA-256 |
| canonical_analysis_id | character varying(200) | N | — | — | canonical_analysis 식별자 |
| representation | character varying(20) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `representation` 값 |
| decision_status | character varying(20) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `decision_status` 값 |
| decision_reason_code | character varying(60) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `decision_reason_code` 값 |
| decision_stage | character varying(30) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `decision_stage` 값 |
| core_status | character varying(20) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `core_status` 값 |
| adapter_status | character varying(60) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `adapter_status` 값 |
| generation_status | character varying(60) | N | — | — | 검증·커밋한 Canonical 결과 원본에 사용하는 `generation_status` 값 |
| applied_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | applied 시각 |
| handoff_id | uuid | N | — | FK (analysis_canonical_results_handoff_id_fkey) | Canonical 인계 식별자 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_results_execution_id_key UNIQUE (execution_id);
analysis_canonical_results_pkey PRIMARY KEY (event_id);
CONSTRAINT analysis_canonical_results_callback_bytes_check CHECK (((octet_length(callback_bytes) >= 1) AND (octet_length(callback_bytes) <= 1048576)));
CONSTRAINT analysis_canonical_results_check1 CHECK (((core_sha256 IS NULL) = (decision_status IS NULL)));
CONSTRAINT analysis_canonical_results_decision_status_check CHECK (((decision_status)::text = ANY ((ARRAY['ACCEPT'::character varying, 'REJECT'::character varying, 'INCONCLUSIVE'::character varying, 'SYSTEM_FAILURE'::character varying])::text[])));
CONSTRAINT analysis_canonical_results_payload_sha256_check CHECK (((payload_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_results_raw_sha256_check CHECK (((raw_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_results_status_check CHECK (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'FAILED'::character varying])::text[])));
CONSTRAINT canonical_result_transport CHECK (((((schema_version)::text = 'voice-coaching.runpod-analysis-result.v4'::text) AND ((analysis_profile)::text = 'CANONICAL_FROZEN_20260928_V4'::text) AND (handoff_id IS NULL) AND ((core_sha256 IS NULL) = (evidence_receipt_id IS NULL))) OR (((schema_version)::text = 'voice-coaching.runpod-analysis-result.v5'::text) AND ((analysis_profile)::text = 'CANONICAL_HANDOFF_20261004_V5'::text) AND (handoff_id IS NOT NULL) AND (evidence_receipt_id IS NULL)) OR (((schema_version)::text = 'voice-coaching.runpod-analysis-result.v6'::text) AND ((analysis_profile)::text = 'CANONICAL_AUDIOVISUAL_20261007_V6'::text) AND (handoff_id IS NOT NULL) AND (evidence_receipt_id IS NULL))));
```

트리거:

```sql
CREATE TRIGGER canonical_result_immutable BEFORE DELETE OR UPDATE ON public.analysis_canonical_results FOR EACH ROW EXECUTE FUNCTION public.reject_canonical_execution_mutation();
CREATE TRIGGER canonical_result_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_results FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
CREATE TRIGGER canonical_result_origin BEFORE INSERT ON public.analysis_canonical_results FOR EACH ROW EXECUTE FUNCTION public.require_canonical_result_origin();
```

### analysis_canonical_upload_journal

- 담당 모듈: `analysis`. 목적: Canonical 업로드 저널.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| execution_id | uuid | Y | — | PK (analysis_canonical_upload_journal_pkey); FK (analysis_canonical_upload_journal_execution_id_fkey) | 분석 실행 식별자 |
| kind | character varying(30) | Y | — | PK (analysis_canonical_upload_journal_pkey) | 산출물 종류 |
| metadata_bytes | bytea | Y | — | — | Canonical 업로드 저널에 사용하는 `metadata_bytes` 값 |
| sha256 | character varying(64) | Y | — | — | 원본 SHA-256 |
| byte_size | integer | Y | — | — | 원본 바이트 크기 |
| staged_bytes | bytea | N | — | — | Canonical 업로드 저널에 사용하는 `staged_bytes` 값 |
| state | character varying(20) | Y | 'DECLARED'::character varying | — | Canonical 업로드 저널에 사용하는 `state` 값 |
| reference_bytes | bytea | N | — | — | Canonical 업로드 저널에 사용하는 `reference_bytes` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_canonical_upload_journal_pkey PRIMARY KEY (execution_id, kind);
CONSTRAINT analysis_canonical_upload_journal_byte_size_check CHECK (((byte_size >= 1) AND (byte_size <= 16777216)));
CONSTRAINT analysis_canonical_upload_journal_check CHECK (((((state)::text = 'DECLARED'::text) AND (staged_bytes IS NULL)) OR (((state)::text <> 'DECLARED'::text) AND (staged_bytes IS NOT NULL) AND (octet_length(staged_bytes) = byte_size))));
CONSTRAINT analysis_canonical_upload_journal_check1 CHECK ((((state)::text = ANY ((ARRAY['UPLOADED'::character varying, 'VERIFIED'::character varying])::text[])) = (reference_bytes IS NOT NULL)));
CONSTRAINT analysis_canonical_upload_journal_kind_check CHECK (((kind)::text = ANY ((ARRAY['CORE'::character varying, 'BRIDGE_RESULT'::character varying, 'SELECTION_PROJECTION'::character varying, 'BINDING'::character varying, 'ASSOCIATION'::character varying])::text[])));
CONSTRAINT analysis_canonical_upload_journal_metadata_bytes_check CHECK (((octet_length(metadata_bytes) >= 1) AND (octet_length(metadata_bytes) <= 4096)));
CONSTRAINT analysis_canonical_upload_journal_reference_bytes_check CHECK (((octet_length(reference_bytes) >= 1) AND (octet_length(reference_bytes) <= 8192)));
CONSTRAINT analysis_canonical_upload_journal_sha256_check CHECK (((sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_canonical_upload_journal_state_check CHECK (((state)::text = ANY ((ARRAY['DECLARED'::character varying, 'PREPARED'::character varying, 'UPLOADING'::character varying, 'UPLOADED'::character varying, 'VERIFIED'::character varying])::text[])));
```

트리거:

```sql
CREATE TRIGGER canonical_upload_guard BEFORE DELETE OR UPDATE ON public.analysis_canonical_upload_journal FOR EACH ROW EXECUTE FUNCTION public.protect_canonical_journal();
CREATE TRIGGER canonical_upload_no_truncate BEFORE TRUNCATE ON public.analysis_canonical_upload_journal FOR EACH STATEMENT EXECUTE FUNCTION public.protect_canonical_journal();
```

### analysis_dispatch_gates

- 담당 모듈: `analysis`. 목적: 엔드포인트별 분석 전송·capacity 대기 제어.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| endpoint_sha256 | character(64) | Y | — | PK (analysis_dispatch_gates_pkey) | 대상 데이터 SHA-256 |
| claim_id | uuid | N | — | — | 처리 점유 식별자 |
| claim_until | timestamp with time zone | N | — | — | 처리 점유 만료 시각 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 다음 처리 가능 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_dispatch_gates_pkey PRIMARY KEY (endpoint_sha256);
```

### analysis_evidence_receipts

- 담당 모듈: `analysis`. 목적: 분석 근거 산출물 수신 증빙.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| receipt_id | uuid | Y | — | PK (analysis_evidence_receipts_pkey) | receipt 식별자 |
| execution_id | uuid | Y | — | UNIQUE (analysis_evidence_receipts_execution_id_key); FK (analysis_evidence_receipts_execution_id_fkey) | 분석 실행 식별자 |
| request_id | uuid | Y | — | — | 요청 식별자 |
| analysis_id | bigint | Y | — | — | 분석 식별자 |
| worker_instance_id | uuid | Y | — | — | worker_instance 식별자 |
| manifest_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| manifest_bytes | bytea | Y | — | — | 분석 근거 산출물 수신 증빙에 사용하는 `manifest_bytes` 값 |
| status | character varying(20) | Y | 'PENDING'::character varying | INDEX evidence_receipt_verification_queue | 처리 또는 게시 상태; 허용 값은 CHECK 제약 참조 |
| reason_code | character varying(60) | N | — | — | 처리 사유 코드 |
| registered_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | INDEX evidence_receipt_verification_queue | registered 시각 |
| updated_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 수정 시각 |
| verification_attempts | integer | Y | 0 | — | 분석 근거 산출물 수신 증빙에 사용하는 `verification_attempts` 값 |
| next_attempt_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | INDEX evidence_receipt_verification_queue | 다음 처리 가능 시각 |
| verifier_claim_id | uuid | N | — | — | verifier_claim 식별자 |
| verifier_claim_until | timestamp with time zone | N | — | — | 분석 근거 산출물 수신 증빙에 사용하는 `verifier_claim_until` 값 |
| verified_at | timestamp with time zone | N | — | — | verified 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_evidence_receipts_execution_id_key UNIQUE (execution_id);
analysis_evidence_receipts_pkey PRIMARY KEY (receipt_id);
CONSTRAINT analysis_evidence_receipts_check CHECK (((((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFYING'::character varying, 'VERIFIED'::character varying])::text[])) AND (reason_code IS NULL)) OR (((status)::text = 'REJECTED'::text) AND (reason_code IS NOT NULL) AND ((reason_code)::text = ANY ((ARRAY['EVIDENCE_INVALID'::character varying, 'EVIDENCE_STORAGE_UNAVAILABLE'::character varying])::text[]))) OR (((status)::text = 'EXPIRED'::text) AND (reason_code IS NOT NULL) AND ((reason_code)::text = 'DEADLINE_EXCEEDED'::text)) OR (((status)::text = 'CANCELED'::text) AND (reason_code IS NOT NULL) AND ((reason_code)::text = 'EXECUTION_INACTIVE'::text))));
CONSTRAINT analysis_evidence_receipts_check1 CHECK (((((status)::text = 'VERIFYING'::text) AND (verifier_claim_id IS NOT NULL) AND (verifier_claim_until IS NOT NULL)) OR (((status)::text <> 'VERIFYING'::text) AND (verifier_claim_id IS NULL) AND (verifier_claim_until IS NULL))));
CONSTRAINT analysis_evidence_receipts_check2 CHECK ((((status)::text = 'VERIFIED'::text) = (verified_at IS NOT NULL)));
CONSTRAINT analysis_evidence_receipts_manifest_bytes_check CHECK (((octet_length(manifest_bytes) >= 1) AND (octet_length(manifest_bytes) <= 65536)));
CONSTRAINT analysis_evidence_receipts_manifest_sha256_check CHECK (((manifest_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_evidence_receipts_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFYING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying, 'CANCELED'::character varying])::text[])));
CONSTRAINT analysis_evidence_receipts_verification_attempts_check CHECK ((verification_attempts >= 0));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX evidence_receipt_verification_queue ON public.analysis_evidence_receipts USING btree (next_attempt_at, registered_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFYING'::character varying])::text[]));
```

트리거:

```sql
CREATE TRIGGER canonical_receipt_immutable_identity BEFORE DELETE OR UPDATE ON public.analysis_evidence_receipts FOR EACH ROW EXECUTE FUNCTION public.protect_canonical_receipt();
CREATE TRIGGER canonical_receipt_no_truncate BEFORE TRUNCATE ON public.analysis_evidence_receipts FOR EACH STATEMENT EXECUTE FUNCTION public.protect_canonical_receipt();
```

### analysis_execution_media

- 담당 모듈: `analysis`. 목적: 분석 실행에 고정된 미디어 준비 snapshot.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| execution_id | uuid | Y | — | PK (analysis_execution_media_pkey); FK (analysis_execution_media_execution_id_fkey) | 분석 실행 식별자 |
| recording_id | bigint | Y | — | — | 녹음 식별자 |
| media_type | character varying(16) | Y | — | — | 분석 실행에 고정된 미디어 준비 snapshot에 사용하는 `media_type` 값 |
| audio_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| video_sha256 | character varying(64) | N | — | — | 대상 데이터 SHA-256 |
| receipt_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| receipt_bytes | bytea | Y | — | — | 분석 실행에 고정된 미디어 준비 snapshot에 사용하는 `receipt_bytes` 값 |
| created_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_execution_media_pkey PRIMARY KEY (execution_id);
CONSTRAINT analysis_execution_media_audio_sha256_check CHECK (((audio_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_execution_media_check CHECK (((((media_type)::text = 'AUDIO_ONLY'::text) AND (video_sha256 IS NULL)) OR (((media_type)::text = 'AUDIO_VISUAL'::text) AND (video_sha256 IS NOT NULL))));
CONSTRAINT analysis_execution_media_media_type_check CHECK (((media_type)::text = ANY ((ARRAY['AUDIO_ONLY'::character varying, 'AUDIO_VISUAL'::character varying])::text[])));
CONSTRAINT analysis_execution_media_receipt_bytes_check CHECK (((octet_length(receipt_bytes) >= 1) AND (octet_length(receipt_bytes) <= 16384)));
CONSTRAINT analysis_execution_media_receipt_sha256_check CHECK (((receipt_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT analysis_execution_media_video_sha256_check CHECK (((video_sha256)::text ~ '^[0-9a-f]{64}$'::text));
```

트리거:

```sql
CREATE TRIGGER execution_media_immutable BEFORE DELETE OR UPDATE ON public.analysis_execution_media FOR EACH ROW EXECUTE FUNCTION public.reject_canonical_execution_mutation();
CREATE TRIGGER execution_media_no_truncate BEFORE TRUNCATE ON public.analysis_execution_media FOR EACH STATEMENT EXECUTE FUNCTION public.reject_canonical_execution_mutation();
```

### analysis_request_outbox

- 담당 모듈: `analysis`. 목적: 분석 요청 발행 및 재시도.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | nextval('public.analysis_request_outbox_id_seq'::regclass) | PK (analysis_request_outbox_pkey); INDEX idx_analysis_request_outbox_dispatch; INDEX idx_analysis_request_outbox_retention; INDEX idx_analysis_request_outbox_transport_dispatch | outbox ID |
| event_id | character varying(36) | Y | — | UNIQUE (analysis_request_outbox_event_id_key) | request generation UUID |
| analysis_id | bigint | Y | — | FK (analysis_request_outbox_analysis_id_fkey) | 대상 `analysis_results.id` |
| payload | text | Y | — | — | versioned request JSON |
| status | character varying(20) | Y | — | INDEX idx_analysis_request_outbox_dispatch; INDEX idx_analysis_request_outbox_transport_dispatch | `PENDING`, `PUBLISHED`, `FAILED` |
| attempt_count | integer | Y | 0 | — | dispatch attempt count |
| next_attempt_at | timestamp with time zone | Y | now() | INDEX idx_analysis_request_outbox_dispatch; INDEX idx_analysis_request_outbox_transport_dispatch | next retry time |
| last_error_code | character varying(100) | N | — | — | stable dispatch failure code |
| created_at | timestamp with time zone | Y | now() | INDEX idx_analysis_request_outbox_retention | enqueue time |
| published_at | timestamp with time zone | N | — | — | successful XADD time |
| request_stream_id | character varying(64) | N | — | — | Redis Stream 전송 시 XADD stream id |
| retention_protocol_version | integer | N | — | INDEX idx_analysis_request_outbox_retention | Redis Stream retention 처리 protocol version |
| transport | character varying(20) | Y | 'REDIS_STREAM'::character varying | INDEX idx_analysis_request_outbox_transport_dispatch | 전송 방식. `REDIS_STREAM`, `RUNPOD_HTTP` |
| execution_id | character varying(36) | N | — | — | RunPod HTTP 전송 시 실행 세대 UUID |
| delivery_reference | character varying(128) | N | — | — | RunPod HTTP 접수 성공 참조값 |
| private_payload_ciphertext | text | N | — | — | 분석 요청 발행 및 재시도에 사용하는 `private_payload_ciphertext` 값 |
| busy_count | integer | Y | 0 | — | 분석 요청 발행 및 재시도에 사용하는 `busy_count` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_request_outbox_event_id_key UNIQUE (event_id);
analysis_request_outbox_pkey PRIMARY KEY (id);
CONSTRAINT analysis_request_outbox_busy_count_check CHECK ((busy_count >= 0));
CONSTRAINT chk_analysis_request_outbox_execution_id CHECK (((execution_id IS NULL) OR ((execution_id)::text ~ '^[0-9a-fA-F-]{36}$'::text)));
CONSTRAINT chk_analysis_request_outbox_transport CHECK (((transport)::text = ANY ((ARRAY['REDIS_STREAM'::character varying, 'RUNPOD_HTTP'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_analysis_request_outbox_dispatch ON public.analysis_request_outbox USING btree (status, next_attempt_at, id);
CREATE INDEX idx_analysis_request_outbox_retention ON public.analysis_request_outbox USING btree (retention_protocol_version, created_at, id);
CREATE INDEX idx_analysis_request_outbox_transport_dispatch ON public.analysis_request_outbox USING btree (transport, status, next_attempt_at, id);
```

### analysis_results

- 담당 모듈: `analysis`. 목적: 녹음별 분석 상태·점수·피드백·실행 식별자.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (analysis_results_pkey) | 분석 결과 ID |
| recording_id | bigint | Y | — | UNIQUE (uq_analysis_results_recording); FK (analysis_results_recording_id_fkey) | 음성 녹음 ID |
| status | character varying(20) | Y | — | INDEX idx_analysis_http_deadline | 분석 상태 |
| transcript | text | N | — | — | STT 변환 텍스트 |
| stt_confidence | numeric(5,4) | N | — | — | STT 신뢰도 |
| stt_model_name | character varying(100) | N | — | — | STT 모델명 |
| overall_score | numeric(5,2) | N | — | — | 종합 점수 |
| pronunciation_score | numeric(5,2) | N | — | — | 발음 점수 |
| intonation_score | numeric(5,2) | N | — | — | 억양 점수 |
| speed_wpm | numeric(7,2) | N | — | — | 말하기 속도(WPM) |
| speed_status | character varying(30) | N | — | — | 속도 상태 |
| stress_score | numeric(5,2) | N | — | — | 강세 점수 |
| pause_score | numeric(5,2) | N | — | — | 휴지 점수 |
| strengths_text | text | N | — | — | 강점 피드백 |
| weaknesses_text | text | N | — | — | 개선점 피드백 |
| summary_feedback | text | N | — | — | 요약 피드백 |
| analyzed_at | timestamp with time zone | N | now() | — | 분석 완료/처리 시각 |
| failure_reason | character varying(500) | N | — | — | 실패 사유 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| feedback_regeneration_count | integer | Y | 0 | — | 종합 피드백 재생성 횟수 |
| feedback_regenerated_at | timestamp with time zone | N | — | — | 마지막 종합 피드백 재생성 시각 |
| active_request_event_id | character varying(36) | N | — | INDEX idx_analysis_results_active_request_event_id | 현재 분석 요청 generation UUID. 이전 재시도 결과를 거부한다. |
| analysis_outcome | character varying(40) | N | — | — | 완료된 분석의 근거 제한 outcome |
| failure_code | character varying(100) | N | — | — | 내부 안정 실패 코드. public API에는 노출하지 않는다. |
| worker_revision | character varying(100) | N | — | — | worker revision receipt |
| pipeline_revision | character varying(100) | N | — | — | pipeline revision receipt |
| audio_sha256 | character varying(64) | N | — | — | 처리한 audio digest receipt |
| retry_count | integer | Y | 0 | — | 동일 분석 행의 누적 사용자 재시도 횟수(0~3). 상태 변경이나 서버 재시작으로 초기화되지 않는다. |
| pronunciation_evidence_schema_version | character varying(100) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `pronunciation_evidence_schema_version` 값 |
| selected_phone | character varying(16) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `selected_phone` 값 |
| selected_expected_index | integer | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `selected_expected_index` 값 |
| selected_start_ms | integer | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `selected_start_ms` 값 |
| selected_end_ms | integer | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `selected_end_ms` 값 |
| detector_score | numeric(8,6) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `detector_score` 값 |
| operating_threshold | numeric(8,6) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `operating_threshold` 값 |
| score_semantics | character varying(100) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `score_semantics` 값 |
| evidence_state | character varying(100) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `evidence_state` 값 |
| visual_supplement_schema_version | character varying(100) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `visual_supplement_schema_version` 값 |
| visual_evidence_relation | character varying(40) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `visual_evidence_relation` 값 |
| visual_approved_claim_id | character varying(192) | N | — | — | visual_approved_claim 식별자 |
| visual_renderer_key | character varying(192) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `visual_renderer_key` 값 |
| visual_phone_anchor_ref | character varying(64) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `visual_phone_anchor_ref` 값 |
| visual_supplement_sha256 | character varying(64) | N | — | — | 대상 데이터 SHA-256 |
| visual_closed_beta_lip_observation | jsonb | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `visual_closed_beta_lip_observation` 값 |
| active_execution_id | character varying(36) | N | — | INDEX idx_analysis_http_deadline; INDEX idx_analysis_results_active_execution_id | 현재 RunPod HTTP 실행 세대 UUID. 이전 Pod의 늦은 결과를 거부한다. |
| worker_instance_id | character varying(100) | N | — | — | 현재 실행을 점유한 RunPod worker instance ID |
| claim_expires_at | timestamp with time zone | N | — | INDEX idx_analysis_results_claim_expires_at | RunPod 실행 점유 만료 시각 |
| last_heartbeat_at | timestamp with time zone | N | — | — | 마지막 RunPod heartbeat 수신 시각 |
| last_result_event_id | character varying(36) | N | — | — | 마지막으로 반영한 RunPod 결과 callback event UUID |
| last_result_payload_sha256 | character varying(64) | N | — | — | 마지막으로 반영한 RunPod 결과 callback payload digest |
| execution_deadline_at | timestamp with time zone | N | — | INDEX idx_analysis_http_deadline | execution_deadline 시각 |
| clova_score_evidence | jsonb | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `clova_score_evidence` 값 |
| coaching_document | jsonb | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `coaching_document` 값 |
| analysis_profile | character varying(100) | Y | 'LEGACY_SEUNGUN_V3'::character varying | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `analysis_profile` 값 |
| expected_result_schema_version | character varying(100) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `expected_result_schema_version` 값 |
| canonical_result_event_id | uuid | N | — | — | canonical_result_event 식별자 |
| committed_result_schema_version | character varying(100) | N | — | — | 녹음별 분석 상태·점수·피드백·실행 식별자에 사용하는 `committed_result_schema_version` 값 |
| handoff_received_at | timestamp with time zone | N | — | — | handoff_received 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_results_pkey PRIMARY KEY (id);
uq_analysis_results_recording UNIQUE (recording_id);
CONSTRAINT chk_analysis_results_active_execution_id CHECK (((active_execution_id IS NULL) OR ((active_execution_id)::text ~ '^[0-9a-fA-F-]{36}$'::text)));
CONSTRAINT chk_analysis_results_closed_beta_lip_observation_object CHECK (((visual_closed_beta_lip_observation IS NULL) OR (jsonb_typeof(visual_closed_beta_lip_observation) = 'object'::text)));
CONSTRAINT chk_analysis_results_last_result_event_id CHECK (((last_result_event_id IS NULL) OR ((last_result_event_id)::text ~ '^[0-9a-fA-F-]{36}$'::text)));
CONSTRAINT chk_analysis_results_last_result_payload_sha256 CHECK (((last_result_payload_sha256 IS NULL) OR ((last_result_payload_sha256)::text ~ '^[0-9a-f]{64}$'::text)));
CONSTRAINT chk_analysis_results_speed_status CHECK (((speed_status IS NULL) OR ((speed_status)::text = ANY ((ARRAY['TOO_SLOW'::character varying, 'NORMAL'::character varying, 'TOO_FAST'::character varying])::text[]))));
CONSTRAINT chk_analysis_results_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])));
CONSTRAINT chk_analysis_results_visual_all_or_none CHECK ((((visual_supplement_schema_version IS NULL) AND (visual_evidence_relation IS NULL) AND (visual_approved_claim_id IS NULL) AND (visual_renderer_key IS NULL) AND (visual_phone_anchor_ref IS NULL) AND (visual_supplement_sha256 IS NULL)) OR (((visual_supplement_schema_version)::text = 'voice-coaching.visual-supplement.v1'::text) AND ((visual_evidence_relation)::text = 'supports_upstream'::text) AND (visual_approved_claim_id IS NOT NULL) AND (visual_renderer_key IS NOT NULL) AND (length((visual_phone_anchor_ref)::text) = 64) AND (length((visual_supplement_sha256)::text) = 64))));
CONSTRAINT ck_analysis_clova_score CHECK (((clova_score_evidence IS NULL) OR ((overall_score IS NOT NULL) AND ((overall_score >= (0)::numeric) AND (overall_score <= (100)::numeric)) AND ((status)::text = 'COMPLETED'::text))));
CONSTRAINT ck_analysis_coaching_document CHECK (((coaching_document IS NULL) OR COALESCE(((jsonb_typeof(coaching_document) = 'object'::text) AND ((coaching_document ->> 'schemaVersion'::text) = 'voice-coaching.coaching-result.v1'::text)), false)));
CONSTRAINT ck_analysis_results_detector_score CHECK (((detector_score IS NULL) OR ((detector_score >= (0)::numeric) AND (detector_score <= (1)::numeric))));
CONSTRAINT ck_analysis_results_operating_threshold CHECK (((operating_threshold IS NULL) OR ((operating_threshold >= (0)::numeric) AND (operating_threshold <= (1)::numeric))));
CONSTRAINT ck_analysis_results_retry_count CHECK (((retry_count >= 0) AND (retry_count <= 3)));
CONSTRAINT ck_analysis_results_selected_phone_index CHECK (((selected_expected_index IS NULL) OR (selected_expected_index >= 0)));
CONSTRAINT ck_analysis_results_selected_phone_offsets CHECK ((((selected_start_ms IS NULL) AND (selected_end_ms IS NULL)) OR ((selected_start_ms >= 0) AND (selected_end_ms > selected_start_ms))));
CONSTRAINT ck_analysis_results_threshold_passed CHECK (((detector_score IS NULL) OR (operating_threshold IS NULL) OR (detector_score >= operating_threshold)));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_analysis_http_deadline ON public.analysis_results USING btree (execution_deadline_at) WHERE ((active_execution_id IS NOT NULL) AND ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[])));
CREATE INDEX idx_analysis_results_active_execution_id ON public.analysis_results USING btree (active_execution_id);
CREATE INDEX idx_analysis_results_active_request_event_id ON public.analysis_results USING btree (active_request_event_id);
CREATE INDEX idx_analysis_results_claim_expires_at ON public.analysis_results USING btree (claim_expires_at);
```

### analysis_segments

- 담당 모듈: `analysis`. 목적: 분석 구간과 원문·인식 결과 대응.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (analysis_segments_pkey) | 분석 세그먼트 ID |
| analysis_result_id | bigint | Y | — | FK (analysis_segments_analysis_result_id_fkey) | 분석 결과 ID |
| sequence_no | integer | Y | — | — | 세그먼트 순서 |
| expected_text | character varying(100) | N | — | — | 기대 텍스트 |
| recognized_text | character varying(100) | N | — | — | 인식 텍스트 |
| start_ms | integer | N | — | — | 시작 위치(ms) |
| end_ms | integer | N | — | — | 종료 위치(ms) |
| match_type | character varying(30) | Y | — | — | 텍스트 매칭 유형 |
| result_status | character varying(30) | Y | — | — | 세그먼트 결과 상태 |
| target_unit | character varying(100) | N | — | — | 분석 대상 단위 |
| error_type | character varying(50) | N | — | — | 오류 유형 |
| pronunciation_score | numeric(5,2) | N | — | — | 발음 점수 |
| intonation_score | numeric(5,2) | N | — | — | 억양 점수 |
| feedback | character varying(1000) | N | — | — | 세부 피드백 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
analysis_segments_pkey PRIMARY KEY (id);
CONSTRAINT chk_analysis_segments_match_type CHECK (((match_type)::text = ANY ((ARRAY['MATCH'::character varying, 'SUBSTITUTION'::character varying, 'OMISSION'::character varying, 'ADDITION'::character varying])::text[])));
CONSTRAINT chk_analysis_segments_result_status CHECK (((result_status)::text = ANY ((ARRAY['NORMAL'::character varying, 'CAUTION'::character varying, 'NEEDS_IMPROVEMENT'::character varying])::text[])));
```

### content_categories

- 담당 모듈: `practicecontent`. 목적: 콘텐츠 유형별 카테고리 코드·표시명·순서.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (content_categories_pkey) | 레코드 식별자 |
| content_type | character varying(30) | Y | — | UNIQUE (uq_content_category) | 콘텐츠 유형별 카테고리 코드·표시명·순서에 사용하는 `content_type` 값 |
| code | character varying(100) | Y | — | UNIQUE (uq_content_category) | 분류 코드 |
| label | character varying(100) | Y | — | — | 사용자 표시명 |
| sort_order | integer | Y | — | — | 표시 순서 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
content_categories_pkey PRIMARY KEY (id);
uq_content_category UNIQUE (content_type, code);
```

### content_tts_outbox

- 담당 모듈: `practicecontent`. 목적: 공개 콘텐츠 TTS 생성 요청.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| content_id | bigint | Y | — | PK (content_tts_outbox_pkey); FK (content_tts_outbox_content_id_fkey) | 콘텐츠 식별자 |
| text_revision | integer | Y | — | — | 공개 콘텐츠 TTS 생성 요청에 사용하는 `text_revision` 값 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| processed_at | timestamp with time zone | N | — | — | 처리 완료 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
content_tts_outbox_pkey PRIMARY KEY (content_id);
```

### course_step_revisions

- 담당 모듈: `course`. 목적: 클래스 단계의 교육 내용 revision.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (course_step_revisions_pkey) | 레코드 식별자 |
| step_id | bigint | Y | — | UNIQUE (uq_course_step_revision); FK (course_step_revisions_step_id_fkey) | 클래스 단계 식별자 |
| revision | integer | Y | — | UNIQUE (uq_course_step_revision) | 내용 revision |
| title | character varying(255) | Y | — | — | 클래스 단계의 교육 내용 revision에 사용하는 `title` 값 |
| subtitle | character varying(255) | N | — | — | 클래스 단계의 교육 내용 revision에 사용하는 `subtitle` 값 |
| step_order | integer | Y | — | — | 클래스 단계의 교육 내용 revision에 사용하는 `step_order` 값 |
| step_type | character varying(50) | Y | — | — | 클래스 단계의 교육 내용 revision에 사용하는 `step_type` 값 |
| blocks_json | text | Y | — | — | 클래스 단계의 교육 내용 revision에 사용하는 `blocks_json` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
course_step_revisions_pkey PRIMARY KEY (id);
uq_course_step_revision UNIQUE (step_id, revision);
CONSTRAINT course_step_revisions_blocks_json_check CHECK ((jsonb_typeof((blocks_json)::jsonb) = 'array'::text));
CONSTRAINT course_step_revisions_revision_check CHECK ((revision > 0));
```

트리거:

```sql
CREATE TRIGGER course_revision_immutable BEFORE DELETE OR UPDATE ON public.course_step_revisions FOR EACH ROW EXECUTE FUNCTION public.prevent_course_revision_mutation();
```

### course_steps

- 담당 모듈: `course`. 목적: 클래스 단계와 순서.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (course_steps_pkey) | 코스 단계 ID |
| course_id | bigint | Y | — | UNIQUE (uq_course_steps_course_order); FK (courses_to_course_steps) | 코스 ID |
| practice_content_id | bigint | N | — | FK (practice_contend_id_to_course_step) | 연결된 연습 콘텐츠 ID |
| step_type | character varying(30) | Y | — | — | 단계 유형 |
| step_order | integer | Y | — | UNIQUE (uq_course_steps_course_order) | 단계 순서 |
| title | character varying(200) | Y | — | — | 단계 제목 |
| body | text | N | — | — | 단계 본문 |
| required | boolean | Y | — | — | 필수 단계 여부 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
course_steps_pkey PRIMARY KEY (id);
uq_course_steps_course_order UNIQUE (course_id, step_order);
CONSTRAINT chk_course_steps_step_type CHECK (((step_type)::text = ANY ((ARRAY['THEORY'::character varying, 'AUDIO_EXAMPLE'::character varying, 'PRACTICE'::character varying, 'RESULT_REVIEW'::character varying])::text[])));
```

### courses

- 담당 모듈: `course`. 목적: 학습 클래스와 게시 정보.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (courses_pkey) | 코스 ID |
| course_type | character varying(20) | Y | — | — | 코스 유형 |
| title | character varying(200) | Y | — | — | 제목 |
| description | character varying(1000) | N | — | — | 설명 |
| difficulty | character varying(20) | Y | — | — | 난이도 |
| estimated_minutes | integer | N | — | — | 예상 소요 시간(분) |
| status | character varying(20) | Y | — | — | 공개 상태 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| updated_at | timestamp with time zone | Y | now() | — | 수정 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
courses_pkey PRIMARY KEY (id);
CONSTRAINT chk_courses_course_type CHECK (((course_type)::text = ANY ((ARRAY['PRONUNCIATION'::character varying, 'INTONATION'::character varying])::text[])));
CONSTRAINT chk_courses_difficulty CHECK (((difficulty)::text = ANY ((ARRAY['BEGINNER'::character varying, 'INTERMEDIATE'::character varying, 'ADVANCED'::character varying])::text[])));
CONSTRAINT chk_courses_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'HIDDEN'::character varying])::text[])));
```

### custom_content_requests

- 담당 모듈: `practicecontent`. 목적: 사용자 원고 생성 요청의 멱등성.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (custom_content_requests_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | UNIQUE (uq_custom_content_request); FK (custom_content_requests_user_id_fkey) | 사용자 식별자 |
| key_digest | character varying(64) | Y | — | UNIQUE (uq_custom_content_request) | 사용자 원고 생성 요청의 멱등성에 사용하는 `key_digest` 값 |
| request_digest | character varying(64) | Y | — | — | 사용자 원고 생성 요청의 멱등성에 사용하는 `request_digest` 값 |
| content_id | bigint | Y | — | FK (custom_content_requests_content_id_fkey) | 콘텐츠 식별자 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
custom_content_requests_pkey PRIMARY KEY (id);
uq_custom_content_request UNIQUE (user_id, key_digest);
```

### direct_analysis_history

- 담당 모듈: `analysis`. 목적: 직접 분석 결과·원고·아카이브 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| job_id | uuid | Y | — | PK (direct_analysis_history_pkey) | 작업 식별자 |
| execution_id | uuid | Y | — | — | 분석 실행 식별자 |
| claim_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| result_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| result_json | text | Y | — | — | 분석 결과 JSON 원문 |
| script_text | text | Y | — | — | 직접 분석 결과·원고·아카이브 이력에 사용하는 `script_text` 값 |
| content_id | bigint | N | — | — | 콘텐츠 식별자 |
| archive_json | text | N | — | — | 아카이브 JSON 정보 |
| created_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 생성 시각 |
| archived_at | timestamp with time zone | N | — | — | 아카이브 완료 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
direct_analysis_history_pkey PRIMARY KEY (job_id);
```

### direct_analysis_history_links

- 담당 모듈: `analysis`. 목적: 사용자와 직접 분석 이력 연결.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| user_id | bigint | Y | — | PK (direct_analysis_history_links_pkey); FK (direct_analysis_history_links_user_id_fkey); INDEX direct_analysis_history_links_recent | 사용자 식별자 |
| job_id | uuid | Y | — | PK (direct_analysis_history_links_pkey) | 작업 식별자 |
| claim_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| created_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | INDEX direct_analysis_history_links_recent | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
direct_analysis_history_links_pkey PRIMARY KEY (user_id, job_id);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX direct_analysis_history_links_recent ON public.direct_analysis_history_links USING btree (user_id, created_at DESC);
```

### example_audio_approvals

- 담당 모듈: `practiceexample`. 목적: 예문 음성의 승인 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| job_id | bigint | Y | — | PK (example_audio_approvals_pkey); FK (example_audio_approvals_job_id_fkey) | 작업 식별자 |
| audio_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| reviewer_id | character varying(120) | Y | — | PK (example_audio_approvals_pkey) | reviewer 식별자 |
| decision | character varying(10) | Y | — | — | 예문 음성의 승인 이력에 사용하는 `decision` 값 |
| reviewed_at | timestamp with time zone | Y | now() | — | reviewed 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
example_audio_approvals_pkey PRIMARY KEY (job_id, reviewer_id);
CONSTRAINT example_audio_approvals_decision_check CHECK (((decision)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])));
CONSTRAINT example_audio_approvals_reviewer_id_check CHECK ((length(TRIM(BOTH FROM reviewer_id)) > 0));
```

### example_audio_cache

- 담당 모듈: `practiceexample`. 목적: 예문·콘텐츠 음성 캐시 메타데이터.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | character varying(64) | Y | — | PK (example_audio_cache_pkey) | 레코드 식별자 |
| lease | character varying(36) | N | — | — | 예문·콘텐츠 음성 캐시 메타데이터에 사용하는 `lease` 값 |
| lease_until | timestamp with time zone | N | — | — | 예문·콘텐츠 음성 캐시 메타데이터에 사용하는 `lease_until` 값 |
| audio | bytea | N | — | — | 예문·콘텐츠 음성 캐시 메타데이터에 사용하는 `audio` 값 |
| etag | character varying(66) | N | — | — | 예문·콘텐츠 음성 캐시 메타데이터에 사용하는 `etag` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
example_audio_cache_pkey PRIMARY KEY (id);
CONSTRAINT example_audio_cache_audio_check CHECK (((audio IS NULL) OR ((octet_length(audio) >= 3) AND (octet_length(audio) <= 2000000))));
CONSTRAINT example_audio_cache_check CHECK (((audio IS NULL) = (etag IS NULL)));
```

### example_tts_jobs

- 담당 모듈: `practiceexample`. 목적: 예문 음성 합성 작업.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (example_tts_jobs_pkey) | 레코드 식별자 |
| example_id | character varying(100) | N | — | FK (example_tts_jobs_example_id_fkey) | example 식별자 |
| profile_revision | character varying(120) | Y | — | UNIQUE (uq_content_tts_revision); FK (example_tts_jobs_profile_revision_fkey); INDEX idx_example_tts_pending | 예문 음성 합성 작업에 사용하는 `profile_revision` 값 |
| state | character varying(20) | Y | 'PENDING'::character varying | INDEX idx_example_tts_pending | 예문 음성 합성 작업에 사용하는 `state` 값 |
| attempt | integer | Y | 0 | — | 예문 음성 합성 작업에 사용하는 `attempt` 값 |
| next_attempt_at | timestamp with time zone | Y | now() | INDEX idx_example_tts_pending | 다음 처리 가능 시각 |
| lease_owner | uuid | N | — | — | 예문 음성 합성 작업에 사용하는 `lease_owner` 값 |
| lease_until | timestamp with time zone | N | — | — | 예문 음성 합성 작업에 사용하는 `lease_until` 값 |
| cache_key | character varying(64) | N | — | FK (example_tts_jobs_cache_key_fkey) | 예문 음성 합성 작업에 사용하는 `cache_key` 값 |
| audio_sha256 | character varying(64) | N | — | — | 대상 데이터 SHA-256 |
| duration_ms | integer | N | — | — | 예문 음성 합성 작업에 사용하는 `duration_ms` 값 |
| last_error_code | character varying(80) | N | — | — | 예문 음성 합성 작업에 사용하는 `last_error_code` 값 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| updated_at | timestamp with time zone | Y | now() | — | 수정 시각 |
| content_id | bigint | Y | — | UNIQUE (uq_content_tts_revision); FK (example_tts_jobs_content_id_fkey) | 콘텐츠 식별자 |
| text_revision | integer | Y | 1 | UNIQUE (uq_content_tts_revision) | 예문 음성 합성 작업에 사용하는 `text_revision` 값 |
| object_key | character varying(512) | N | — | — | 객체 저장소 키 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
example_tts_jobs_pkey PRIMARY KEY (id);
uq_content_tts_revision UNIQUE (content_id, text_revision, profile_revision);
CONSTRAINT example_tts_jobs_attempt_check CHECK (((attempt >= 0) AND (attempt <= 5)));
CONSTRAINT example_tts_jobs_check CHECK ((((state)::text = 'RUNNING'::text) = ((lease_owner IS NOT NULL) AND (lease_until IS NOT NULL))));
CONSTRAINT example_tts_jobs_check1 CHECK ((((state)::text <> 'GENERATED'::text) OR ((cache_key IS NOT NULL) AND (audio_sha256 IS NOT NULL) AND (duration_ms > 0))));
CONSTRAINT example_tts_jobs_state_check CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'RETRY_WAIT'::character varying, 'GENERATED'::character varying, 'FAILED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_example_tts_pending ON public.example_tts_jobs USING btree (profile_revision, next_attempt_at) WHERE ((state)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying, 'RUNNING'::character varying])::text[]));
```

### example_tts_outbox

- 담당 모듈: `practiceexample`. 목적: 예문 음성 합성 발행 대기.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| example_id | character varying(100) | Y | — | PK (example_tts_outbox_pkey); FK (example_tts_outbox_example_id_fkey) | example 식별자 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| processed_at | timestamp with time zone | N | — | — | 처리 완료 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
example_tts_outbox_pkey PRIMARY KEY (example_id);
```

### example_tts_profiles

- 담당 모듈: `practiceexample`. 목적: 음성 합성 프로필과 버전.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| revision | character varying(120) | Y | — | PK (example_tts_profiles_pkey) | 내용 revision |
| fingerprint | character varying(64) | Y | — | — | 음성 합성 프로필과 버전에 사용하는 `fingerprint` 값 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
example_tts_profiles_pkey PRIMARY KEY (revision);
```

### example_tts_quotas

- 담당 모듈: `practiceexample`. 목적: 음성 합성 사용량 한도.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | — | PK (example_tts_quotas_pkey) | 레코드 식별자 |
| window_minute | bigint | Y | — | — | 음성 합성 사용량 한도에 사용하는 `window_minute` 값 |
| attempts | integer | Y | — | — | 처리 시도 횟수 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
example_tts_quotas_pkey PRIMARY KEY (id);
```

### flyway_schema_history

- 담당 모듈: `common / Flyway`. 목적: 스키마 마이그레이션 적용 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| installed_rank | integer | Y | — | PK (flyway_schema_history_pk) | 스키마 마이그레이션 적용 이력에 사용하는 `installed_rank` 값 |
| version | character varying(50) | N | — | — | 버전 |
| description | character varying(200) | Y | — | — | 스키마 마이그레이션 적용 이력에 사용하는 `description` 값 |
| type | character varying(20) | Y | — | — | 스키마 마이그레이션 적용 이력에 사용하는 `type` 값 |
| script | character varying(1000) | Y | — | — | 스키마 마이그레이션 적용 이력에 사용하는 `script` 값 |
| checksum | integer | N | — | — | 스키마 마이그레이션 적용 이력에 사용하는 `checksum` 값 |
| installed_by | character varying(100) | Y | — | — | 스키마 마이그레이션 적용 이력에 사용하는 `installed_by` 값 |
| installed_on | timestamp without time zone | Y | now() | — | 스키마 마이그레이션 적용 이력에 사용하는 `installed_on` 값 |
| execution_time | integer | Y | — | — | 스키마 마이그레이션 적용 이력에 사용하는 `execution_time` 값 |
| success | boolean | Y | — | INDEX flyway_schema_history_s_idx | 스키마 마이그레이션 적용 이력에 사용하는 `success` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
flyway_schema_history_pk PRIMARY KEY (installed_rank);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
```

### inquiries

- 담당 모듈: `support`. 목적: 사용자 문의와 답변.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (inquiries_pkey); INDEX idx_inquiries_user_created | 레코드 식별자 |
| user_id | bigint | Y | — | UNIQUE (uq_inquiries_user_idempotency); FK (inquiries_user_id_fkey); INDEX idx_inquiries_user_created | 사용자 식별자 |
| category | character varying(50) | Y | — | — | 사용자 문의와 답변에 사용하는 `category` 값 |
| subject | character varying(100) | Y | — | — | 사용자 문의와 답변에 사용하는 `subject` 값 |
| body | character varying(2000) | Y | — | — | 사용자 문의와 답변에 사용하는 `body` 값 |
| related_session_id | bigint | N | — | — | related_session 식별자 |
| reply_email | character varying(254) | N | — | — | 사용자 문의와 답변에 사용하는 `reply_email` 값 |
| status | character varying(20) | Y | — | — | 처리 또는 게시 상태; 허용 값은 CHECK 제약 참조 |
| answer | character varying(4000) | N | — | — | 사용자 문의와 답변에 사용하는 `answer` 값 |
| created_at | timestamp with time zone | Y | — | INDEX idx_inquiries_user_created | 생성 시각 |
| answered_at | timestamp with time zone | N | — | — | answered 시각 |
| idempotency_key_sha256 | character varying(64) | N | — | UNIQUE (uq_inquiries_user_idempotency) | 대상 데이터 SHA-256 |
| request_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
inquiries_pkey PRIMARY KEY (id);
uq_inquiries_user_idempotency UNIQUE (user_id, idempotency_key_sha256);
CONSTRAINT inquiries_status_check CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'IN_PROGRESS'::character varying, 'ANSWERED'::character varying, 'CLOSED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_inquiries_user_created ON public.inquiries USING btree (user_id, created_at DESC, id DESC);
```

### notices

- 담당 모듈: `support`. 목적: 공지사항과 게시 기간.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (notices_pkey); INDEX idx_notices_visible | 레코드 식별자 |
| title | character varying(200) | Y | — | — | 공지사항과 게시 기간에 사용하는 `title` 값 |
| summary | character varying(500) | Y | — | — | 공지사항과 게시 기간에 사용하는 `summary` 값 |
| sections | jsonb | Y | — | — | 공지사항과 게시 기간에 사용하는 `sections` 값 |
| pinned | boolean | Y | false | INDEX idx_notices_visible | 공지사항과 게시 기간에 사용하는 `pinned` 값 |
| published | boolean | Y | false | INDEX idx_notices_visible | 공지사항과 게시 기간에 사용하는 `published` 값 |
| published_at | timestamp with time zone | Y | — | INDEX idx_notices_visible | 게시 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
notices_pkey PRIMARY KEY (id);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_notices_visible ON public.notices USING btree (published, pinned DESC, published_at DESC, id DESC);
```

### notification_preferences

- 담당 모듈: `notification`. 목적: 사용자 알림 수신 설정.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| user_id | bigint | Y | — | PK (notification_preferences_pkey); FK (notification_preferences_user_id_fkey); INDEX idx_notification_reminders | 사용자 식별자 |
| enabled | boolean | Y | — | INDEX idx_notification_reminders | 사용자 알림 수신 설정에 사용하는 `enabled` 값 |
| reminder_time | character varying(5) | Y | — | — | 사용자 알림 수신 설정에 사용하는 `reminder_time` 값 |
| weekdays | character varying(27) | Y | — | — | 사용자 알림 수신 설정에 사용하는 `weekdays` 값 |
| timezone | character varying(100) | Y | — | — | 사용자 알림 수신 설정에 사용하는 `timezone` 값 |
| updated_at | timestamp with time zone | Y | — | — | 수정 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
notification_preferences_pkey PRIMARY KEY (user_id);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_notification_reminders ON public.notification_preferences USING btree (user_id) WHERE (enabled = true);
```

### onboarding_profiles

- 담당 모듈: `onboarding`. 목적: 온보딩 설문과 학습 목표.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (onboarding_profiles_pkey) | 온보딩 프로필 ID |
| user_id | bigint | Y | — | FK ("user_to_onboardingProfiles") | 사용자 ID |
| current_level | character varying(30) | N | — | — | 현재 수준 |
| goal_text | character varying(500) | N | — | — | 학습 목표 |
| daily_goal_minutes | integer | N | — | — | 일일 목표 학습 시간 |
| weekly_goal_count | integer | N | — | — | 주간 목표 횟수 |
| survey_answers | jsonb | Y | — | — | 온보딩 설문 응답 |
| completed_at | timestamp with time zone | N | — | — | 온보딩 완료 시각 |
| updated_at | timestamp with time zone | Y | — | — | 수정 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
onboarding_profiles_pkey PRIMARY KEY (id);
CONSTRAINT chk_onboarding_profiles_current_level CHECK (((current_level IS NULL) OR ((current_level)::text = ANY ((ARRAY['BEGINNER'::character varying, 'INTERMEDIATE'::character varying, 'ADVANCED'::character varying])::text[]))));
```

### practice_contents

- 담당 모듈: `practicecontent`. 목적: 공개 연습 콘텐츠와 사용자 비공개 원고.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (practice_contents_pkey); INDEX idx_content_catalog_order | 연습 콘텐츠 ID |
| content_type | character varying(30) | Y | — | INDEX idx_content_catalog_order | 콘텐츠 유형 |
| learning_focus | character varying(20) | Y | — | — | 학습 초점 |
| category | character varying(50) | N | — | — | 콘텐츠 카테고리 |
| title | character varying(200) | Y | — | — | 제목 |
| description | character varying(1000) | N | — | — | 설명 |
| script_text | text | Y | — | — | 연습 스크립트 |
| difficulty | character varying(20) | Y | — | — | 난이도 |
| target_pronunciations | jsonb | N | — | — | 목표 발음 정보 |
| source_name | character varying(200) | N | — | — | 출처 이름 |
| source_url | character varying(1000) | N | — | — | 출처 URL |
| news_published_at | timestamp with time zone | N | — | — | 뉴스 발행 시각 |
| estimated_seconds | integer | N | — | — | 예상 연습 시간(초) |
| status | character varying(20) | Y | — | INDEX idx_content_catalog_order | 공개 상태 |
| published_at | timestamp with time zone | N | now() | INDEX idx_content_catalog_order | 게시 시각 |
| created_at | timestamp with time zone | Y | now() | INDEX idx_content_catalog_order | 생성 시각 |
| updated_at | timestamp with time zone | Y | now() | — | 수정 시각 |
| owner_id | bigint | N | — | FK (practice_contents_owner_id_fkey); INDEX idx_content_catalog_order; INDEX idx_custom_content_owner | 소유 사용자; NULL이면 공개 콘텐츠 |
| custom_script_ciphertext | text | N | — | — | 암호화한 사용자 원고 |
| custom_title_ciphertext | text | N | — | — | 암호화한 사용자 제목 |
| custom_deleted_at | timestamp with time zone | N | — | — | 사용자 원고 삭제 시각 |
| publisher | character varying(255) | N | — | — | 발행사 |
| speaker_name | character varying(255) | N | — | — | 화자명 |
| tts_revision | integer | Y | 1 | — | 음성 합성 대상 내용 revision |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
practice_contents_pkey PRIMARY KEY (id);
CONSTRAINT chk_practice_contents_content_type CHECK (((content_type)::text = ANY ((ARRAY['NEWS'::character varying, 'SENTENCE'::character varying, 'ANNOUNCER'::character varying, 'CLASS_PRACTICE'::character varying])::text[])));
CONSTRAINT chk_practice_contents_difficulty CHECK (((difficulty)::text = ANY ((ARRAY['BEGINNER'::character varying, 'INTERMEDIATE'::character varying, 'ADVANCED'::character varying])::text[])));
CONSTRAINT chk_practice_contents_learning_focus CHECK (((learning_focus)::text = ANY ((ARRAY['PRONUNCIATION'::character varying, 'INTONATION'::character varying, 'BOTH'::character varying])::text[])));
CONSTRAINT chk_practice_contents_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'HIDDEN'::character varying])::text[])));
CONSTRAINT ck_private_content_storage CHECK (((owner_id IS NULL) OR ((script_text = '[private]'::text) AND ((title)::text = '내 문장'::text) AND (((custom_deleted_at IS NULL) AND (custom_script_ciphertext IS NOT NULL) AND (custom_title_ciphertext IS NOT NULL)) OR ((custom_deleted_at IS NOT NULL) AND (custom_script_ciphertext IS NULL) AND (custom_title_ciphertext IS NULL))))));
CONSTRAINT ck_private_content_visibility CHECK (((owner_id IS NULL) OR ((status)::text = 'HIDDEN'::text)));
CONSTRAINT practice_contents_tts_revision_check CHECK ((tts_revision > 0));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_content_catalog_order ON public.practice_contents USING btree (content_type, ( CASE WHEN (published_at IS NULL) THEN 1 ELSE 0 END), published_at DESC, created_at DESC, id DESC) WHERE (((status)::text = 'PUBLISHED'::text) AND (owner_id IS NULL));
CREATE INDEX idx_custom_content_owner ON public.practice_contents USING btree (owner_id) WHERE (owner_id IS NOT NULL);
```

트리거:

```sql
CREATE TRIGGER enqueue_content_tts AFTER INSERT OR UPDATE OF script_text, status, owner_id ON public.practice_contents FOR EACH ROW EXECUTE FUNCTION public.enqueue_content_tts();
CREATE TRIGGER example_content_immutable BEFORE UPDATE ON public.practice_contents FOR EACH ROW EXECUTE FUNCTION public.prevent_example_content_mutation();
CREATE TRIGGER revise_content_tts BEFORE UPDATE ON public.practice_contents FOR EACH ROW EXECUTE FUNCTION public.revise_content_tts();
```

### practice_example_sets

- 담당 모듈: `practiceexample`. 목적: 클래스 단계별 게시 예문 세트와 revision.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | UNIQUE (practice_example_sets_id_revision_key); PK (practice_example_sets_pkey) | 레코드 식별자 |
| step_id | bigint | Y | — | UNIQUE (practice_example_sets_step_id_revision_key); FK (practice_example_sets_step_id_fkey) | 클래스 단계 식별자 |
| revision | integer | Y | — | UNIQUE (practice_example_sets_id_revision_key); UNIQUE (practice_example_sets_step_id_revision_key) | 내용 revision |
| education_revision_id | bigint | Y | — | FK (practice_example_sets_education_revision_id_fkey) | 교육 내용 revision 식별자 |
| published_at | timestamp with time zone | Y | — | — | 게시 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
practice_example_sets_id_revision_key UNIQUE (id, revision);
practice_example_sets_pkey PRIMARY KEY (id);
practice_example_sets_step_id_revision_key UNIQUE (step_id, revision);
CONSTRAINT practice_example_sets_revision_check CHECK ((revision > 0));
```

트리거:

```sql
CREATE CONSTRAINT TRIGGER example_set_complete AFTER INSERT ON public.practice_example_sets DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validate_practice_example_set();
CREATE TRIGGER example_set_immutable BEFORE DELETE OR UPDATE ON public.practice_example_sets FOR EACH ROW EXECUTE FUNCTION public.prevent_course_revision_mutation();
```

### practice_examples

- 담당 모듈: `practiceexample`. 목적: 예문 순서와 실제 연습 콘텐츠 연결.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | character varying(100) | Y | — | UNIQUE (practice_examples_id_set_id_content_id_key); PK (practice_examples_pkey) | 레코드 식별자 |
| set_id | bigint | Y | — | UNIQUE (practice_examples_id_set_id_content_id_key); UNIQUE (practice_examples_set_id_example_order_key); FK (practice_examples_set_id_fkey) | 예문 세트 식별자 |
| content_id | bigint | Y | — | UNIQUE (practice_examples_content_id_key); UNIQUE (practice_examples_id_set_id_content_id_key); FK (practice_examples_content_id_fkey) | 콘텐츠 식별자 |
| example_order | integer | Y | — | UNIQUE (practice_examples_set_id_example_order_key) | 세트 안의 예문 순서 |
| hint | character varying(500) | N | — | — | 연습 안내 |
| focus | character varying(30) | N | — | — | 학습 초점 |
| locale | character varying(10) | Y | — | — | 언어·지역 코드 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
practice_examples_content_id_key UNIQUE (content_id);
practice_examples_id_set_id_content_id_key UNIQUE (id, set_id, content_id);
practice_examples_pkey PRIMARY KEY (id);
practice_examples_set_id_example_order_key UNIQUE (set_id, example_order);
CONSTRAINT ck_practice_example_order CHECK (((example_order >= 1) AND (example_order <= 10)));
CONSTRAINT practice_examples_id_check CHECK (((id)::text ~ '^[A-Za-z0-9_-]{1,100}$'::text));
CONSTRAINT practice_examples_locale_check CHECK (((locale)::text = 'ko-KR'::text));
```

트리거:

```sql
CREATE TRIGGER example_item_immutable BEFORE DELETE OR UPDATE ON public.practice_examples FOR EACH ROW EXECUTE FUNCTION public.prevent_course_revision_mutation();
CREATE CONSTRAINT TRIGGER example_items_complete AFTER INSERT ON public.practice_examples DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validate_practice_example_set();
CREATE TRIGGER example_tts_created AFTER INSERT ON public.practice_examples FOR EACH ROW EXECUTE FUNCTION public.enqueue_example_tts();
```

### processing_consents

- 담당 모듈: `training / analysis`. 목적: 미디어 처리 동의 증빙.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | nextval('public.processing_consents_id_seq'::regclass) | PK (processing_consents_pkey) | 동의 기록 ID |
| user_id | bigint | Y | — | INDEX idx_processing_consents_user_active | 동의를 제출한 사용자 ID의 감사용 snapshot |
| training_session_id | bigint | Y | — | INDEX idx_processing_consents_session_active | 동의가 적용된 학습 세션 ID의 감사용 snapshot |
| recording_id | bigint | N | — | — | 음성 분석 동의의 정규화 녹음 ID |
| scope | character varying(40) | Y | — | — | `VOICE_ANALYSIS` 또는 `FACE_VIDEO_PROCESSING` |
| policy_revision | character varying(100) | Y | — | — | 사용자가 수락한 정확한 정책 revision |
| subject_sha256 | character varying(64) | Y | — | — | audio digest 또는 원본 object key digest |
| request_event_id | character varying(36) | N | — | INDEX idx_processing_consents_request_event; UNIQUE INDEX uq_processing_consents_request_event | 음성 분석 요청 generation UUID |
| receipt_sha256 | character varying(64) | Y | — | UNIQUE (processing_consents_receipt_sha256_key) | worker grant에도 포함되는 opaque receipt digest |
| granted_at | timestamp with time zone | Y | — | — | 동의 수락 시각(UTC) |
| revoked_at | timestamp with time zone | N | — | INDEX idx_processing_consents_session_active; INDEX idx_processing_consents_user_active | 세션 취소 또는 회원 탈퇴에 따른 철회 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
processing_consents_pkey PRIMARY KEY (id);
processing_consents_receipt_sha256_key UNIQUE (receipt_sha256);
CONSTRAINT ck_processing_consents_policy_revision CHECK (((policy_revision)::text ~ '^[A-Za-z0-9._-]{1,100}$'::text));
CONSTRAINT ck_processing_consents_positive_subject_ids CHECK (((user_id > 0) AND (training_session_id > 0)));
CONSTRAINT ck_processing_consents_receipt_sha256 CHECK (((receipt_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT ck_processing_consents_request_event_uuid CHECK (((request_event_id IS NULL) OR ((request_event_id)::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'::text)));
CONSTRAINT ck_processing_consents_revocation_order CHECK (((revoked_at IS NULL) OR (revoked_at >= granted_at)));
CONSTRAINT ck_processing_consents_scope CHECK (((scope)::text = ANY ((ARRAY['VOICE_ANALYSIS'::character varying, 'FACE_VIDEO_PROCESSING'::character varying])::text[])));
CONSTRAINT ck_processing_consents_scope_binding CHECK (((((scope)::text = 'VOICE_ANALYSIS'::text) AND (recording_id > 0) AND (request_event_id IS NOT NULL)) OR (((scope)::text = 'FACE_VIDEO_PROCESSING'::text) AND (recording_id IS NULL) AND (request_event_id IS NULL))));
CONSTRAINT ck_processing_consents_subject_sha256 CHECK (((subject_sha256)::text ~ '^[0-9a-f]{64}$'::text));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_processing_consents_request_event ON public.processing_consents USING btree (request_event_id);
CREATE INDEX idx_processing_consents_session_active ON public.processing_consents USING btree (training_session_id, revoked_at);
CREATE INDEX idx_processing_consents_user_active ON public.processing_consents USING btree (user_id, revoked_at);
CREATE UNIQUE INDEX uq_processing_consents_request_event ON public.processing_consents USING btree (request_event_id) WHERE (request_event_id IS NOT NULL);
```

### profile_images

- 담당 모듈: `profileimage`. 목적: 프로필 이미지 저장소 메타데이터.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (profile_images_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | UNIQUE (uq_profile_images_user_key); FK (profile_images_user_id_fkey); UNIQUE INDEX uq_profile_images_active_user | 사용자 식별자 |
| object_key | character varying(100) | Y | — | UNIQUE (profile_images_object_key_key) | 객체 저장소 키 |
| image_url | character varying(2048) | Y | — | — | 프로필 이미지 저장소 메타데이터에 사용하는 `image_url` 값 |
| original_file_name | character varying(255) | Y | — | — | 프로필 이미지 저장소 메타데이터에 사용하는 `original_file_name` 값 |
| size_bytes | bigint | Y | — | — | 프로필 이미지 저장소 메타데이터에 사용하는 `size_bytes` 값 |
| key_digest | character varying(64) | N | — | UNIQUE (uq_profile_images_user_key) | 프로필 이미지 저장소 메타데이터에 사용하는 `key_digest` 값 |
| request_digest | character varying(64) | Y | — | — | 프로필 이미지 저장소 메타데이터에 사용하는 `request_digest` 값 |
| state | character varying(20) | Y | — | INDEX idx_profile_images_cleanup; UNIQUE INDEX uq_profile_images_active_user | 프로필 이미지 저장소 메타데이터에 사용하는 `state` 값 |
| created_at | timestamp with time zone | Y | — | — | 생성 시각 |
| activated_at | timestamp with time zone | N | — | — | activated 시각 |
| cleanup_after | timestamp with time zone | Y | — | INDEX idx_profile_images_cleanup | 프로필 이미지 저장소 메타데이터에 사용하는 `cleanup_after` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
profile_images_object_key_key UNIQUE (object_key);
profile_images_pkey PRIMARY KEY (id);
uq_profile_images_user_key UNIQUE (user_id, key_digest);
CONSTRAINT profile_images_size_bytes_check CHECK ((size_bytes > 0));
CONSTRAINT profile_images_state_check CHECK (((state)::text = ANY ((ARRAY['UPLOADING'::character varying, 'ACTIVE'::character varying, 'DELETE_PENDING'::character varying, 'DELETED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_profile_images_cleanup ON public.profile_images USING btree (state, cleanup_after);
CREATE UNIQUE INDEX uq_profile_images_active_user ON public.profile_images USING btree (user_id) WHERE ((state)::text = 'ACTIVE'::text);
```

### push_registration_requests

- 담당 모듈: `notification`. 목적: Push 등록 요청의 멱등성 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (push_registration_requests_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | UNIQUE (uq_push_registration_key); FK (push_registration_requests_user_id_fkey) | 사용자 식별자 |
| key_hash | character varying(64) | Y | — | UNIQUE (uq_push_registration_key) | Push 등록 요청의 멱등성 이력에 사용하는 `key_hash` 값 |
| body_hash | character varying(64) | Y | — | — | Push 등록 요청의 멱등성 이력에 사용하는 `body_hash` 값 |
| subscription_id | bigint | Y | — | — | subscription 식별자 |
| device_name | character varying(100) | N | — | — | Push 등록 요청의 멱등성 이력에 사용하는 `device_name` 값 |
| subscription_created_at | timestamp with time zone | Y | — | — | subscription_created 시각 |
| created_at | timestamp with time zone | Y | — | — | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
push_registration_requests_pkey PRIMARY KEY (id);
uq_push_registration_key UNIQUE (user_id, key_hash);
```

### push_subscriptions

- 담당 모듈: `notification`. 목적: 암호화한 Push 구독 정보.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (push_subscriptions_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | FK (push_subscriptions_user_id_fkey); INDEX idx_push_subscription_owner | 사용자 식별자 |
| endpoint_hash | character varying(64) | Y | — | UNIQUE (push_subscriptions_endpoint_hash_key) | 암호화한 Push 구독 정보에 사용하는 `endpoint_hash` 값 |
| encrypted_payload | text | Y | — | — | 암호화한 Push 구독 정보에 사용하는 `encrypted_payload` 값 |
| device_name | character varying(100) | N | — | — | 암호화한 Push 구독 정보에 사용하는 `device_name` 값 |
| active | boolean | Y | — | — | 암호화한 Push 구독 정보에 사용하는 `active` 값 |
| created_at | timestamp with time zone | Y | — | — | 생성 시각 |
| updated_at | timestamp with time zone | Y | — | — | 수정 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
push_subscriptions_endpoint_hash_key UNIQUE (endpoint_hash);
push_subscriptions_pkey PRIMARY KEY (id);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_push_subscription_owner ON public.push_subscriptions USING btree (user_id);
```

### recording_deletion_outbox

- 담당 모듈: `training`. 목적: 녹음 객체 삭제 요청 및 재시도.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | nextval('public.recording_deletion_outbox_id_seq'::regclass) | PK (recording_deletion_outbox_pkey); INDEX idx_recording_deletion_outbox_dispatch | 삭제 작업 ID |
| user_id | bigint | Y | — | INDEX idx_recording_deletion_outbox_user | key 소유자 ID snapshot |
| training_session_id | bigint | Y | — | — | key 소유 세션 ID snapshot |
| object_key | character varying(1000) | Y | — | UNIQUE (recording_deletion_outbox_object_key_key) | 삭제할 private object key |
| reason | character varying(40) | Y | — | — | 삭제 발생 원인 |
| status | character varying(20) | Y | — | INDEX idx_recording_deletion_outbox_dispatch; INDEX idx_recording_deletion_outbox_user | `PENDING`, `DELETED`, `FAILED` |
| attempt_count | integer | Y | 0 | — | 객체 삭제 시도 횟수(최대 10) |
| next_attempt_at | timestamp with time zone | Y | now() | INDEX idx_recording_deletion_outbox_dispatch | 다음 재시도 시각 |
| last_error_code | character varying(100) | N | — | — | 비민감 안정 오류 코드 |
| created_at | timestamp with time zone | Y | now() | — | 삭제 요청 시각 |
| deleted_at | timestamp with time zone | N | — | — | 객체 삭제 완료 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
recording_deletion_outbox_object_key_key UNIQUE (object_key);
recording_deletion_outbox_pkey PRIMARY KEY (id);
CONSTRAINT ck_recording_deletion_outbox_attempt_count CHECK (((attempt_count >= 0) AND (attempt_count <= 10)));
CONSTRAINT ck_recording_deletion_outbox_reason CHECK (((reason)::text = ANY ((ARRAY['RECORDING_DELETED'::character varying, 'SESSION_CANCELED'::character varying, 'HISTORY_DELETED'::character varying, 'USER_WITHDRAWN'::character varying, 'UPLOAD_EXPIRED'::character varying, 'ANALYSIS_COMPLETED'::character varying])::text[])));
CONSTRAINT ck_recording_deletion_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DELETED'::character varying, 'FAILED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_recording_deletion_outbox_dispatch ON public.recording_deletion_outbox USING btree (status, next_attempt_at, id);
CREATE INDEX idx_recording_deletion_outbox_user ON public.recording_deletion_outbox USING btree (user_id, status);
```

### recording_media_preparations

- 담당 모듈: `training`. 목적: 정규화 미디어 준비 receipt.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| recording_id | bigint | Y | — | PK (recording_media_preparations_pkey); FK (recording_media_preparations_recording_id_fkey) | 녹음 식별자 |
| schema_version | character varying(100) | Y | — | — | 계약 스키마 버전 |
| receipt_bytes | bytea | Y | — | — | 정규화 미디어 준비 receipt에 사용하는 `receipt_bytes` 값 |
| receipt_sha256 | character varying(64) | Y | — | — | 대상 데이터 SHA-256 |
| created_at | timestamp with time zone | Y | CURRENT_TIMESTAMP | — | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
recording_media_preparations_pkey PRIMARY KEY (recording_id);
CONSTRAINT recording_media_preparations_receipt_bytes_check CHECK (((octet_length(receipt_bytes) >= 1) AND (octet_length(receipt_bytes) <= 16384)));
CONSTRAINT recording_media_preparations_receipt_sha256_check CHECK (((receipt_sha256)::text ~ '^[0-9a-f]{64}$'::text));
CONSTRAINT recording_media_preparations_schema_version_check CHECK (((schema_version)::text = 'voice-coaching.media-preparation.v1'::text));
```

트리거:

```sql
CREATE TRIGGER media_preparation_immutable BEFORE UPDATE ON public.recording_media_preparations FOR EACH ROW EXECUTE FUNCTION public.reject_media_preparation_update();
```

### recording_upload_intents

- 담당 모듈: `training`. 목적: 녹음 업로드 URL 발급·소비 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | nextval('public.recording_upload_intents_id_seq'::regclass) | PK (recording_upload_intents_pkey); INDEX idx_recording_upload_intents_expiry | 업로드 intent ID |
| user_id | bigint | Y | — | INDEX idx_recording_upload_intents_user_session | 발급 사용자 ID snapshot |
| training_session_id | bigint | Y | — | INDEX idx_recording_upload_intents_user_session | 발급 세션 ID snapshot |
| object_key | character varying(1000) | Y | — | UNIQUE (recording_upload_intents_object_key_key) | 발급된 private source object key |
| mime_type | character varying(100) | Y | — | — | 요청 MIME |
| file_size_bytes | bigint | Y | — | — | 요청 바이트 크기 |
| expires_at | timestamp with time zone | Y | — | INDEX idx_recording_upload_intents_expiry | presigned URL 만료 시각 |
| status | character varying(20) | Y | — | INDEX idx_recording_upload_intents_expiry; INDEX idx_recording_upload_intents_user_session | `ISSUED`, `CONSUMED`, `EXPIRED` |
| created_at | timestamp with time zone | Y | now() | — | 발급 시각 |
| resolved_at | timestamp with time zone | N | — | — | 소비 또는 만료 처리 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
recording_upload_intents_object_key_key UNIQUE (object_key);
recording_upload_intents_pkey PRIMARY KEY (id);
CONSTRAINT ck_recording_upload_intents_size CHECK ((file_size_bytes > 0));
CONSTRAINT ck_recording_upload_intents_status CHECK (((status)::text = ANY ((ARRAY['ISSUED'::character varying, 'CONSUMED'::character varying, 'EXPIRED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_recording_upload_intents_expiry ON public.recording_upload_intents USING btree (status, expires_at, id);
CREATE INDEX idx_recording_upload_intents_user_session ON public.recording_upload_intents USING btree (user_id, training_session_id, status);
```

### reference_audios

- 담당 모듈: `practicecontent`. 목적: 콘텐츠별 기준 음성 메타데이터.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (reference_audios_pkey) | 참조 음성 ID |
| content_id | bigint | Y | — | FK (practice_contents_to_reference_audios) | 연습 콘텐츠 ID |
| speaker_name | character varying(100) | N | — | — | 화자 이름 |
| speaker_type | character varying(30) | N | — | — | 화자 유형 |
| audio_url | character varying(1000) | N | — | — | 외부 음성 URL 또는 기존 TTS 참조값. `object_key`와 둘 중 하나 필수 |
| duration_ms | integer | N | — | — | 재생 길이(ms) |
| is_primary | boolean | Y | — | — | 대표 음성 여부 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| tts_job_id | bigint | N | — | UNIQUE (reference_audios_tts_job_id_key); FK (reference_audios_tts_job_id_fkey) | tts_job 식별자 |
| object_key | character varying(512) | N | — | — | private S3 기준 음성 object key. `reference-audios/` prefix만 허용 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
reference_audios_pkey PRIMARY KEY (id);
reference_audios_tts_job_id_key UNIQUE (tts_job_id);
CONSTRAINT chk_reference_audios_speaker_type CHECK (((speaker_type IS NULL) OR ((speaker_type)::text = ANY ((ARRAY['ANNOUNCER'::character varying, 'COACH'::character varying, 'TTS'::character varying])::text[]))));
CONSTRAINT ck_reference_audio_source CHECK ((((audio_url IS NOT NULL) AND (length(TRIM(BOTH FROM audio_url)) > 0)) OR ((object_key IS NOT NULL) AND (length(TRIM(BOTH FROM object_key)) > 0))));
```

### refresh_tokens

- 담당 모듈: `auth`. 목적: Refresh Token 해시·세션·만료.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | nextval('public.refresh_tokens_id_seq'::regclass) | PK (refresh_tokens_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | FK (refresh_tokens_user_id_fkey); UNIQUE INDEX uk_refresh_tokens_user | 사용자 식별자 |
| token_hash | character varying(64) | Y | — | UNIQUE INDEX uk_refresh_tokens_hash | Refresh Token 해시; 원문 토큰 아님 |
| session_id | character varying(36) | Y | — | UNIQUE INDEX uk_refresh_tokens_session | 세션 식별자 |
| expires_at | timestamp with time zone | Y | — | — | 만료 시각 |
| updated_at | timestamp with time zone | Y | — | — | 수정 시각 |
| version | bigint | Y | 0 | — | 버전 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
refresh_tokens_pkey PRIMARY KEY (id);
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE UNIQUE INDEX uk_refresh_tokens_hash ON public.refresh_tokens USING btree (token_hash);
CREATE UNIQUE INDEX uk_refresh_tokens_session ON public.refresh_tokens USING btree (session_id);
CREATE UNIQUE INDEX uk_refresh_tokens_user ON public.refresh_tokens USING btree (user_id);
```

### social_accounts

- 담당 모듈: `auth`. 목적: 소셜 계정과 사용자 연결.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (social_accounts_pkey) | 소셜 계정 ID |
| user_id | bigint | Y | — | UNIQUE (uq_social_accounts_user_provider); FK ("user_to_socialAccounts") | 사용자 ID |
| provider | character varying(20) | Y | — | UNIQUE (uq_social_accounts_provider_user); UNIQUE (uq_social_accounts_user_provider); UNIQUE INDEX uk_social_accounts_provider_user | 소셜 제공자 |
| provider_user_id | character varying(255) | Y | — | UNIQUE (uq_social_accounts_provider_user); UNIQUE INDEX uk_social_accounts_provider_user | 제공자 사용자 ID |
| provider_email | character varying(255) | N | — | — | 제공자 이메일 |
| created_at | timestamp with time zone | Y | — | — | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
social_accounts_pkey PRIMARY KEY (id);
uq_social_accounts_provider_user UNIQUE (provider, provider_user_id);
uq_social_accounts_user_provider UNIQUE (user_id, provider);
CONSTRAINT chk_social_accounts_provider CHECK (((provider)::text = ANY ((ARRAY['GOOGLE'::character varying, 'KAKAO'::character varying, 'NAVER'::character varying, 'APPLE'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE UNIQUE INDEX uk_social_accounts_provider_user ON public.social_accounts USING btree (provider, provider_user_id);
```

### title_exam_requests

- 담당 모듈: `title`. 목적: 승급 시험 요청 멱등성 이력.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (title_exam_requests_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | UNIQUE (uq_title_exam_request); FK (title_exam_requests_user_id_fkey) | 사용자 식별자 |
| key_digest | character varying(64) | Y | — | UNIQUE (uq_title_exam_request) | 승급 시험 요청 멱등성 이력에 사용하는 `key_digest` 값 |
| exam_id | bigint | Y | — | FK (title_exam_requests_exam_id_fkey) | exam 식별자 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
title_exam_requests_pkey PRIMARY KEY (id);
uq_title_exam_request UNIQUE (user_id, key_digest);
```

### title_exams

- 담당 모듈: `title`. 목적: 사용자 승급 시험과 평가 결과.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (title_exams_pkey) | 레코드 식별자 |
| user_id | bigint | Y | — | FK (title_exams_user_id_fkey); UNIQUE INDEX uq_title_exam_active_target | 사용자 식별자 |
| previous_rank | character varying(30) | Y | — | — | 사용자 승급 시험과 평가 결과에 사용하는 `previous_rank` 값 |
| target_rank | character varying(30) | Y | — | UNIQUE INDEX uq_title_exam_active_target | 사용자 승급 시험과 평가 결과에 사용하는 `target_rank` 값 |
| practice_content_id | bigint | Y | — | FK (title_exams_practice_content_id_fkey) | practice_content 식별자 |
| required_training_count | bigint | Y | — | — | 사용자 승급 시험과 평가 결과에 사용하는 `required_training_count` 값 |
| passing_score | integer | Y | — | — | 사용자 승급 시험과 평가 결과에 사용하는 `passing_score` 값 |
| training_session_id | bigint | N | — | UNIQUE (title_exams_training_session_id_key) | training_session 식별자 |
| analysis_id | bigint | N | — | UNIQUE (title_exams_analysis_id_key) | 분석 식별자 |
| status | character varying(20) | Y | — | UNIQUE INDEX uq_title_exam_active_target | 처리 또는 게시 상태; 허용 값은 CHECK 제약 참조 |
| score | numeric(5,2) | N | — | — | 사용자 승급 시험과 평가 결과에 사용하는 `score` 값 |
| created_at | timestamp with time zone | Y | — | — | 생성 시각 |
| evaluated_at | timestamp with time zone | N | — | — | evaluated 시각 |
| practice_example_set_id | bigint | N | — | FK (fk_exam_example_revision) | practice_example_set 식별자 |
| practice_example_revision | integer | N | — | FK (fk_exam_example_revision) | 사용자 승급 시험과 평가 결과에 사용하는 `practice_example_revision` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
title_exams_analysis_id_key UNIQUE (analysis_id);
title_exams_pkey PRIMARY KEY (id);
title_exams_training_session_id_key UNIQUE (training_session_id);
CONSTRAINT ck_exam_example_pin CHECK (((practice_example_set_id IS NULL) = (practice_example_revision IS NULL)));
CONSTRAINT title_exams_passing_score_check CHECK (((passing_score >= 0) AND (passing_score <= 100)));
CONSTRAINT title_exams_status_check CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'IN_PROGRESS'::character varying, 'PASSED'::character varying, 'FAILED'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE UNIQUE INDEX uq_title_exam_active_target ON public.title_exams USING btree (user_id, target_rank) WHERE ((status)::text = ANY ((ARRAY['READY'::character varying, 'IN_PROGRESS'::character varying])::text[]));
```

### title_policies

- 담당 모듈: `title`. 목적: 칭호 등급별 승급 정책.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| target_rank | character varying(30) | Y | — | PK (title_policies_pkey) | 칭호 등급별 승급 정책에 사용하는 `target_rank` 값 |
| required_training_count | bigint | Y | — | — | 칭호 등급별 승급 정책에 사용하는 `required_training_count` 값 |
| passing_score | integer | Y | — | — | 칭호 등급별 승급 정책에 사용하는 `passing_score` 값 |
| practice_content_id | bigint | N | — | FK (title_policies_practice_content_id_fkey) | practice_content 식별자 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
title_policies_pkey PRIMARY KEY (target_rank);
CONSTRAINT title_policies_passing_score_check CHECK (((passing_score >= 0) AND (passing_score <= 100)));
CONSTRAINT title_policies_required_training_count_check CHECK ((required_training_count >= 0));
```

### training_sessions

- 담당 모듈: `training`. 목적: 콘텐츠별 학습 세션과 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (training_sessions_pkey); INDEX idx_session_example_revision; INDEX idx_training_course_revision | 학습 세션 ID |
| user_id | bigint | Y | — | FK (training_sessions_user_id_fkey); INDEX idx_session_example_revision; INDEX idx_training_course_revision | 사용자 ID |
| content_id | bigint | Y | — | FK (fk_session_example); FK (training_sessions_content_id_fkey) | 연습 콘텐츠 ID |
| course_step_id | bigint | N | — | FK (training_sessions_course_step_id_fkey); INDEX idx_session_example_revision; INDEX idx_training_course_revision | 코스 단계 ID |
| learning_focus | character varying(20) | Y | — | — | 학습 초점 |
| status | character varying(20) | Y | — | — | 세션 상태 |
| started_at | timestamp with time zone | Y | now() | — | 시작 시각 |
| completed_at | timestamp with time zone | N | — | — | 완료 시각 |
| total_learning_seconds | integer | Y | 0 | — | 총 학습 시간(초) |
| failure_reason | character varying(500) | N | — | — | 실패 사유 |
| course_education_revision_id | bigint | N | — | FK (training_sessions_course_education_revision_id_fkey); INDEX idx_training_course_revision | course_education_revision 식별자 |
| practice_example_id | character varying(100) | N | — | FK (fk_session_example) | practice_example 식별자 |
| practice_example_set_id | bigint | N | — | FK (fk_session_example); FK (fk_session_example_revision); INDEX idx_session_example_revision | practice_example_set 식별자 |
| practice_example_revision | integer | N | — | FK (fk_session_example_revision) | 콘텐츠별 학습 세션과 상태에 사용하는 `practice_example_revision` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
training_sessions_pkey PRIMARY KEY (id);
CONSTRAINT chk_training_sessions_learning_focus CHECK (((learning_focus)::text = ANY ((ARRAY['PRONUNCIATION'::character varying, 'INTONATION'::character varying, 'BOTH'::character varying])::text[])));
CONSTRAINT chk_training_sessions_status CHECK (((status)::text = ANY ((ARRAY['RECORDING'::character varying, 'UPLOADING'::character varying, 'ANALYZING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELED'::character varying])::text[])));
CONSTRAINT ck_session_example_pin CHECK ((((practice_example_id IS NULL) AND (practice_example_set_id IS NULL) AND (practice_example_revision IS NULL)) OR ((practice_example_id IS NOT NULL) AND (practice_example_set_id IS NOT NULL) AND (practice_example_revision IS NOT NULL))));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_session_example_revision ON public.training_sessions USING btree (user_id, course_step_id, id DESC) WHERE (practice_example_set_id IS NOT NULL);
CREATE INDEX idx_training_course_revision ON public.training_sessions USING btree (user_id, course_step_id, id DESC) WHERE (course_education_revision_id IS NOT NULL);
```

### user_course_progress

- 담당 모듈: `course`. 목적: 사용자별 클래스 진도.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (user_course_progress_pkey) | 코스 진행 ID |
| last_step_id | bigint | N | — | FK (user_course_progress_last_step_id_fkey) | 마지막 진행 단계 ID |
| course_id | bigint | Y | — | UNIQUE (uq_user_course_progress_user_course); FK (user_course_progress_course_id_fkey) | 코스 ID |
| user_id | bigint | Y | — | UNIQUE (uq_user_course_progress_user_course); FK (user_course_progress_user_id_fkey) | 사용자 ID |
| status | character varying(20) | Y | — | — | 진행 상태 |
| progress_percent | numeric(5,2) | Y | 0.00 | — | 진행률 |
| started_at | timestamp with time zone | N | now() | — | 시작 시각 |
| completed_at | timestamp with time zone | N | now() | — | 완료 시각 |
| updated_at | timestamp with time zone | Y | now() | — | 수정 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
uq_user_course_progress_user_course UNIQUE (user_id, course_id);
user_course_progress_pkey PRIMARY KEY (id);
CONSTRAINT chk_user_course_progress_status CHECK (((status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying])::text[])));
```

### user_notifications

- 담당 모듈: `notification`. 목적: 사용자에게 전달할 알림과 읽음 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (user_notifications_pkey); INDEX idx_notification_inbox; INDEX idx_notification_unread | 레코드 식별자 |
| user_id | bigint | Y | — | UNIQUE (uq_notification_event); FK (user_notifications_user_id_fkey); INDEX idx_notification_inbox; INDEX idx_notification_unread | 사용자 식별자 |
| type | character varying(50) | Y | — | — | 사용자에게 전달할 알림과 읽음 상태에 사용하는 `type` 값 |
| title | character varying(200) | Y | — | — | 사용자에게 전달할 알림과 읽음 상태에 사용하는 `title` 값 |
| body | character varying(1000) | Y | — | — | 사용자에게 전달할 알림과 읽음 상태에 사용하는 `body` 값 |
| deep_link | character varying(255) | Y | — | — | 사용자에게 전달할 알림과 읽음 상태에 사용하는 `deep_link` 값 |
| deduplication_key | character varying(100) | Y | — | UNIQUE (uq_notification_event) | 사용자에게 전달할 알림과 읽음 상태에 사용하는 `deduplication_key` 값 |
| read_at | timestamp with time zone | N | — | INDEX idx_notification_unread | read 시각 |
| created_at | timestamp with time zone | Y | — | INDEX idx_notification_inbox; INDEX idx_notification_unread | 생성 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
uq_notification_event UNIQUE (user_id, deduplication_key);
user_notifications_pkey PRIMARY KEY (id);
CONSTRAINT user_notifications_deep_link_check CHECK (((deep_link)::text = ANY ((ARRAY['/home'::character varying, '/notifications'::character varying, '/mypage'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE INDEX idx_notification_inbox ON public.user_notifications USING btree (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_unread ON public.user_notifications USING btree (user_id, created_at DESC, id DESC) WHERE (read_at IS NULL);
```

### user_titles

- 담당 모듈: `title`. 목적: 사용자별 현재 칭호.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| user_id | bigint | Y | — | PK (user_titles_pkey); FK (user_titles_user_id_fkey) | 사용자 식별자 |
| rank | character varying(30) | Y | — | — | 사용자별 현재 칭호에 사용하는 `rank` 값 |
| minimum_training_count | bigint | Y | 0 | — | 사용자별 현재 칭호에 사용하는 `minimum_training_count` 값 |
| updated_at | timestamp with time zone | Y | — | — | 수정 시각 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
user_titles_pkey PRIMARY KEY (user_id);
```

### users

- 담당 모듈: `user`. 목적: 사용자 계정·권한·탈퇴 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (users_pkey) | 사용자 ID |
| email | character varying(255) | N | — | UNIQUE (uq_users_email); UNIQUE INDEX uk_users_email_normalized | 이메일 로그인 식별자 |
| password | character varying(255) | N | — | — | 비밀번호 해시 |
| nickname | character varying(50) | Y | — | — | 닉네임 |
| status | character varying(20) | Y | — | — | 회원 상태 |
| terms_agreed_at | timestamp with time zone | Y | — | — | 이용약관 동의 시각 |
| privacy_agreed_at | timestamp with time zone | Y | — | — | 개인정보 처리방침 동의 시각 |
| last_login_at | timestamp with time zone | N | — | — | 마지막 로그인 시각 |
| created_at | timestamp with time zone | Y | — | — | 생성 시각 |
| updated_at | timestamp with time zone | Y | — | — | 수정 시각 |
| deleted_at | timestamp with time zone | N | — | — | 탈퇴/삭제 시각 |
| role | character varying(63) | Y | — | — | 사용자 권한 |
| profile_image_url | character varying(2048) | N | — | — | 사용자 계정·권한·탈퇴 상태에 사용하는 `profile_image_url` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
uq_users_email UNIQUE (email);
users_pkey PRIMARY KEY (id);
CONSTRAINT chk_users_role CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[])));
CONSTRAINT chk_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'WITHDRAWN'::character varying])::text[])));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE UNIQUE INDEX uk_users_email_normalized ON public.users USING btree (lower((email)::text)) WHERE (email IS NOT NULL);
```

### voice_recordings

- 담당 모듈: `training`. 목적: 학습 녹음·미디어 무결성·품질 상태.

| Column | Type | Required | Default | Index / Key | Description |
| --- | --- | --- | --- | --- | --- |
| id | bigint | Y | GENERATED BY DEFAULT AS IDENTITY | PK (voice_recordings_pkey) | 음성 녹음 ID |
| training_session_id | bigint | Y | — | UNIQUE (uq_voice_recordings_session_attempt); FK (voice_recordings_training_session_id_fkey); UNIQUE INDEX uq_voice_recordings_one_selected_per_session | 학습 세션 ID |
| attempt_no | integer | Y | — | UNIQUE (uq_voice_recordings_session_attempt) | 녹음 시도 번호 |
| audio_url | character varying(1000) | Y | — | — | 음성 파일 URL |
| mime_type | character varying(100) | N | — | — | MIME 타입 |
| file_size_bytes | bigint | N | — | — | 파일 크기(byte) |
| duration_ms | integer | N | — | — | 재생 길이(ms) |
| quality_status | character varying(30) | Y | — | — | 품질 검사 상태 |
| volume_score | numeric(5,2) | N | — | — | 볼륨 점수 |
| noise_score | numeric(5,2) | N | — | — | 노이즈 점수 |
| is_selected | boolean | Y | — | UNIQUE INDEX uq_voice_recordings_one_selected_per_session | 분석 대상으로 선택된 녹음 여부 |
| created_at | timestamp with time zone | Y | now() | — | 생성 시각 |
| deleted_at | timestamp with time zone | N | — | — | 삭제 시각 |
| audio_sha256 | character varying(64) | N | — | — | backend-normalized WAV 바이트의 SHA-256. 과거 row는 null 가능 |
| visual_object_key | character varying(1000) | N | — | — | 학습 녹음·미디어 무결성·품질 상태에 사용하는 `visual_object_key` 값 |
| visual_mime_type | character varying(100) | N | — | — | 학습 녹음·미디어 무결성·품질 상태에 사용하는 `visual_mime_type` 값 |
| visual_file_size_bytes | bigint | N | — | — | 학습 녹음·미디어 무결성·품질 상태에 사용하는 `visual_file_size_bytes` 값 |
| visual_sha256 | character varying(64) | N | — | — | 대상 데이터 SHA-256 |
| visual_consent_receipt_sha256 | character varying(64) | N | — | — | 대상 데이터 SHA-256 |
| visual_consent_policy_revision | character varying(100) | N | — | — | 학습 녹음·미디어 무결성·품질 상태에 사용하는 `visual_consent_policy_revision` 값 |

키·CHECK 제약 (enum/허용 상태·범위 포함):

```sql
uq_voice_recordings_session_attempt UNIQUE (training_session_id, attempt_no);
voice_recordings_pkey PRIMARY KEY (id);
CONSTRAINT chk_voice_recordings_quality_status CHECK (((quality_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PASS'::character varying, 'LOW_VOLUME'::character varying, 'TOO_NOISY'::character varying, 'TOO_SHORT'::character varying, 'NO_SPEECH'::character varying, 'FAILED'::character varying])::text[])));
CONSTRAINT chk_voice_recordings_visual_all_or_none CHECK ((((visual_object_key IS NULL) AND (visual_mime_type IS NULL) AND (visual_file_size_bytes IS NULL) AND (visual_sha256 IS NULL) AND (visual_consent_receipt_sha256 IS NULL) AND (visual_consent_policy_revision IS NULL)) OR ((visual_object_key IS NOT NULL) AND ((visual_mime_type)::text = 'video/mp4'::text) AND (visual_file_size_bytes > 0) AND (length((visual_sha256)::text) = 64) AND (length((visual_consent_receipt_sha256)::text) = 64) AND (visual_consent_policy_revision IS NOT NULL))));
CONSTRAINT ck_voice_recordings_audio_sha256 CHECK (((audio_sha256 IS NULL) OR ((audio_sha256)::text ~ '^[0-9a-f]{64}$'::text)));
```

인덱스 (조건·복합 순서 포함):

```sql
CREATE UNIQUE INDEX uq_voice_recordings_one_selected_per_session ON public.voice_recordings USING btree (training_session_id) WHERE (is_selected = true);
```

## FK 관계·카디널리티·삭제 동작

자식→부모는 NULL 허용 여부에 따라 0..1 또는 1이다. 부모→자식은 FK 컬럼 조합 자체에 비조건 UNIQUE/PK가 있으면 0..1, 그 외에는 0..N이다. 부분 UNIQUE는 해당 조건에서만 유효하며 여기서는 일반 관계를 제한하지 않는다. `NO ACTION`은 삭제를 자동 전파하지 않는다.

| 자식 테이블 (컬럼) | 부모 테이블 (컬럼) | 자식→부모 | 부모→자식 | ON DELETE | 제약 |
| --- | --- | --- | --- | --- | --- |
| analysis_canonical_ack_journal (event_id) | analysis_canonical_results (event_id) | 1 | 0..N | NO ACTION | analysis_canonical_ack_journal_event_id_fkey |
| analysis_canonical_archive_artifacts (handoff_id, kind) | analysis_canonical_handoff_artifacts (handoff_id, kind) | 1 | 0..1 | NO ACTION | analysis_canonical_archive_artifacts_handoff_id_kind_fkey |
| analysis_canonical_archive_jobs (handoff_id) | analysis_canonical_handoffs (handoff_id) | 1 | 0..1 | NO ACTION | analysis_canonical_archive_jobs_handoff_id_fkey |
| analysis_canonical_callback_apply (event_id) | analysis_canonical_callback_inbox (event_id) | 1 | 0..1 | NO ACTION | analysis_canonical_callback_apply_event_id_fkey |
| analysis_canonical_callback_inbox (evidence_receipt_id) | analysis_evidence_receipts (receipt_id) | 0..1 | 0..N | NO ACTION | analysis_canonical_callback_inbox_evidence_receipt_id_fkey |
| analysis_canonical_callback_inbox (execution_id) | analysis_canonical_executions (execution_id) | 1 | 0..1 | NO ACTION | analysis_canonical_callback_inbox_execution_id_fkey |
| analysis_canonical_handoff_artifacts (handoff_id) | analysis_canonical_handoffs (handoff_id) | 1 | 0..N | NO ACTION | analysis_canonical_handoff_artifacts_handoff_id_fkey |
| analysis_canonical_handoffs (execution_id) | analysis_canonical_executions (execution_id) | 1 | 0..1 | NO ACTION | analysis_canonical_handoffs_execution_id_fkey |
| analysis_canonical_journals (execution_id) | analysis_canonical_executions (execution_id) | 1 | 0..1 | NO ACTION | analysis_canonical_journals_execution_id_fkey |
| analysis_canonical_result_effects (event_id) | analysis_canonical_results (event_id) | 1 | 0..1 | NO ACTION | analysis_canonical_result_effects_event_id_fkey |
| analysis_canonical_results (evidence_receipt_id) | analysis_evidence_receipts (receipt_id) | 0..1 | 0..N | NO ACTION | analysis_canonical_results_evidence_receipt_id_fkey |
| analysis_canonical_results (execution_id) | analysis_canonical_executions (execution_id) | 1 | 0..1 | NO ACTION | analysis_canonical_results_execution_id_fkey |
| analysis_canonical_results (handoff_id) | analysis_canonical_handoffs (handoff_id) | 0..1 | 0..N | NO ACTION | analysis_canonical_results_handoff_id_fkey |
| analysis_canonical_upload_journal (execution_id) | analysis_canonical_journals (execution_id) | 1 | 0..N | NO ACTION | analysis_canonical_upload_journal_execution_id_fkey |
| analysis_evidence_receipts (execution_id) | analysis_canonical_executions (execution_id) | 1 | 0..1 | NO ACTION | analysis_evidence_receipts_execution_id_fkey |
| analysis_execution_media (execution_id) | analysis_canonical_executions (execution_id) | 1 | 0..1 | NO ACTION | analysis_execution_media_execution_id_fkey |
| analysis_request_outbox (analysis_id) | analysis_results (id) | 1 | 0..N | NO ACTION | analysis_request_outbox_analysis_id_fkey |
| analysis_results (recording_id) | voice_recordings (id) | 1 | 0..1 | NO ACTION | analysis_results_recording_id_fkey |
| analysis_segments (analysis_result_id) | analysis_results (id) | 1 | 0..N | NO ACTION | analysis_segments_analysis_result_id_fkey |
| content_tts_outbox (content_id) | practice_contents (id) | 1 | 0..1 | NO ACTION | content_tts_outbox_content_id_fkey |
| course_step_revisions (step_id) | course_steps (id) | 1 | 0..N | NO ACTION | course_step_revisions_step_id_fkey |
| course_steps (course_id) | courses (id) | 1 | 0..N | NO ACTION | courses_to_course_steps |
| course_steps (practice_content_id) | practice_contents (id) | 0..1 | 0..N | NO ACTION | practice_contend_id_to_course_step |
| custom_content_requests (content_id) | practice_contents (id) | 1 | 0..N | NO ACTION | custom_content_requests_content_id_fkey |
| custom_content_requests (user_id) | users (id) | 1 | 0..N | NO ACTION | custom_content_requests_user_id_fkey |
| direct_analysis_history_links (user_id) | users (id) | 1 | 0..N | CASCADE | direct_analysis_history_links_user_id_fkey |
| example_audio_approvals (job_id) | example_tts_jobs (id) | 1 | 0..N | NO ACTION | example_audio_approvals_job_id_fkey |
| example_tts_jobs (cache_key) | example_audio_cache (id) | 0..1 | 0..N | NO ACTION | example_tts_jobs_cache_key_fkey |
| example_tts_jobs (content_id) | practice_contents (id) | 1 | 0..N | NO ACTION | example_tts_jobs_content_id_fkey |
| example_tts_jobs (example_id) | practice_examples (id) | 0..1 | 0..N | NO ACTION | example_tts_jobs_example_id_fkey |
| example_tts_jobs (profile_revision) | example_tts_profiles (revision) | 1 | 0..N | NO ACTION | example_tts_jobs_profile_revision_fkey |
| example_tts_outbox (example_id) | practice_examples (id) | 1 | 0..1 | NO ACTION | example_tts_outbox_example_id_fkey |
| inquiries (user_id) | users (id) | 1 | 0..N | NO ACTION | inquiries_user_id_fkey |
| notification_preferences (user_id) | users (id) | 1 | 0..1 | NO ACTION | notification_preferences_user_id_fkey |
| practice_contents (owner_id) | users (id) | 0..1 | 0..N | NO ACTION | practice_contents_owner_id_fkey |
| practice_example_sets (education_revision_id) | course_step_revisions (id) | 1 | 0..N | NO ACTION | practice_example_sets_education_revision_id_fkey |
| practice_example_sets (step_id) | course_steps (id) | 1 | 0..N | NO ACTION | practice_example_sets_step_id_fkey |
| practice_examples (content_id) | practice_contents (id) | 1 | 0..1 | NO ACTION | practice_examples_content_id_fkey |
| practice_examples (set_id) | practice_example_sets (id) | 1 | 0..N | NO ACTION | practice_examples_set_id_fkey |
| profile_images (user_id) | users (id) | 1 | 0..N | NO ACTION | profile_images_user_id_fkey |
| push_registration_requests (user_id) | users (id) | 1 | 0..N | NO ACTION | push_registration_requests_user_id_fkey |
| push_subscriptions (user_id) | users (id) | 1 | 0..N | NO ACTION | push_subscriptions_user_id_fkey |
| recording_media_preparations (recording_id) | voice_recordings (id) | 1 | 0..1 | NO ACTION | recording_media_preparations_recording_id_fkey |
| reference_audios (content_id) | practice_contents (id) | 1 | 0..N | NO ACTION | practice_contents_to_reference_audios |
| reference_audios (tts_job_id) | example_tts_jobs (id) | 0..1 | 0..1 | NO ACTION | reference_audios_tts_job_id_fkey |
| refresh_tokens (user_id) | users (id) | 1 | 0..1 | CASCADE | refresh_tokens_user_id_fkey |
| title_exam_requests (exam_id) | title_exams (id) | 1 | 0..N | NO ACTION | title_exam_requests_exam_id_fkey |
| title_exam_requests (user_id) | users (id) | 1 | 0..N | NO ACTION | title_exam_requests_user_id_fkey |
| title_exams (practice_example_set_id, practice_example_revision) | practice_example_sets (id, revision) | 0..1 | 0..N | NO ACTION | fk_exam_example_revision |
| title_exams (practice_content_id) | practice_contents (id) | 1 | 0..N | NO ACTION | title_exams_practice_content_id_fkey |
| title_exams (user_id) | users (id) | 1 | 0..N | NO ACTION | title_exams_user_id_fkey |
| title_policies (practice_content_id) | practice_contents (id) | 0..1 | 0..N | NO ACTION | title_policies_practice_content_id_fkey |
| training_sessions (practice_example_id, practice_example_set_id, content_id) | practice_examples (id, set_id, content_id) | 0..1 | 0..N | NO ACTION | fk_session_example |
| training_sessions (practice_example_set_id, practice_example_revision) | practice_example_sets (id, revision) | 0..1 | 0..N | NO ACTION | fk_session_example_revision |
| training_sessions (content_id) | practice_contents (id) | 1 | 0..N | NO ACTION | training_sessions_content_id_fkey |
| training_sessions (course_education_revision_id) | course_step_revisions (id) | 0..1 | 0..N | NO ACTION | training_sessions_course_education_revision_id_fkey |
| training_sessions (course_step_id) | course_steps (id) | 0..1 | 0..N | NO ACTION | training_sessions_course_step_id_fkey |
| training_sessions (user_id) | users (id) | 1 | 0..N | NO ACTION | training_sessions_user_id_fkey |
| user_notifications (user_id) | users (id) | 1 | 0..N | NO ACTION | user_notifications_user_id_fkey |
| user_course_progress (course_id) | courses (id) | 1 | 0..N | NO ACTION | user_course_progress_course_id_fkey |
| user_course_progress (last_step_id) | course_steps (id) | 0..1 | 0..N | NO ACTION | user_course_progress_last_step_id_fkey |
| user_course_progress (user_id) | users (id) | 1 | 0..N | NO ACTION | user_course_progress_user_id_fkey |
| voice_recordings (training_session_id) | training_sessions (id) | 1 | 0..N | NO ACTION | voice_recordings_training_session_id_fkey |
| user_titles (user_id) | users (id) | 1 | 0..1 | NO ACTION | user_titles_user_id_fkey |

## 보관·인증·마이그레이션 주의사항

- 인증 계정은 `users`·`social_accounts`, Refresh Token은 `refresh_tokens`에 저장한다. 기존 문서의 별도 token 테이블이 없다는 설명은 이 백업과 일치하지 않는다. 토큰 원문이 아닌 해시와 만료·세션 정보를 관리한다.
- 사용자 상태·role·provider 등의 DB 허용 값은 해당 CHECK를 따른다. 애플리케이션 상태 전이 검증은 service/domain에서 수행하며 SQL CHECK만으로 상태 전이 순서가 보장되지는 않는다.
- `practice_contents.owner_id`가 있는 원고는 비공개·암호문 제약을 따른다. 일반 목록은 소유자 없는 PUBLISHED 콘텐츠 중 게시 시각이 NULL 또는 현재 이전인 데이터만 반환한다.
- V45 이후 예문 순서·세트 수량은 5~10개 정책이다. 세트 revision, 교육 revision, 연속 순서, 불변 조건은 위 CHECK와 constraint trigger 및 해당 Flyway 함수 본문을 함께 따른다.
- Canonical 결과·인계 원본·실행 snapshot은 불변 트리거가 적용된다. FK의 ON DELETE만 보고 삭제 가능성을 판단하지 않는다. 관련 함수 원본은 V33~V43 SQL에 있다.
- Canonical 결과 커밋과 아카이브는 분리된다. `analysis_canonical_archive_jobs`·`analysis_canonical_archive_artifacts`의 상태와 재시도를 따르며 archive 실패로 검증된 원본을 삭제하지 않는다.
- `direct_analysis_history_links`의 사용자 연결 삭제와 분석 원본 보관은 별도 책임이다. 실제 cascade 범위는 위 FK 표와 불변 트리거를 따른다.
- 백업은 현재 저장 구조의 근거다. 신규 설치·업그레이드는 저장소 Flyway 순서를 사용하고 이미 적용된 migration을 이 문서 갱신을 위해 수정하지 않는다.

## 확인 방법

PostgreSQL 18의 `pg_restore --schema-only --no-owner --no-privileges --file=<임시파일> <백업>`으로 DDL을 추출했다. 콘텐츠·분류·적용 이력은 `--data-only --table=practice_contents --table=content_categories --table=flyway_schema_history`로 별도 추출하여 COPY 행을 분석했다. `--dbname`을 사용하지 않아 DB에 접속하거나 SQL을 실행하지 않았다.

- 테이블 60개, 컬럼 612개, FK 64개, 명시적 인덱스 41개, 트리거 33개를 백업 DDL과 대조했다.
- 문서의 행 집계는 백업 시점의 데이터다. 현재 운영 DB나 인증된 HTTP 요청 결과를 직접 확인한 것으로 해석하지 않는다.
