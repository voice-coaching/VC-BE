> 실행 진행: 전체 공개 콘텐츠 TTS 작업·S3 저장·기준 음성 연결을 구현했다. 아래 초기 계획과 로컬 상태 기록은 작성 시점의 이력이며 최신 결과는 [구현·배포 기록](all-practice-content-tts-deployment-20260919.md)을 따른다.

# 전체 학습 문장 TTS 생성·재생 수정계획

작성: 2026-09-19 KST. 사용자는 **2인 검수 승인 전 재생 금지 조건을 폐지**했다. 이 문서가 이전 TTS 계획의 검수·공개 조건보다 우선한다.

상태: 검수 승인 수 제한 제거는 별도 BE 작업 브랜치의 로컬 코드에 반영했다. 전체 학습자료 자동 생성 확장은 아래 계획이며 아직 구현·배포하지 않았다. 운영 환경의 기존 제한도 이 코드가 배포되기 전에는 유지된다.

## 1. 확정 정책

- 발행된 학습 문장에 대해 TTS 생성과 자동 파일 검증이 성공하면 바로 재생 대상으로 등록한다. 사람의 사전 청취·승인 수·승인 manifest는 공개 조건이 아니다.
- MP3 전체 decode, 크기·길이·SHA-256, 문장 버전·합성 설정 일치, 사용자 접근 권한 검사는 유지한다. 기술적 검증 통과를 발음 품질 인증으로 표현하지 않는다.
- 잘못된 음성을 운영자가 명시적으로 REJECTED 처리한 경우는 차단한다. 이것은 사전 검수 의무가 아닌 사후 차단이다. 기존 검수 기록은 삭제하거나 가짜 APPROVED 행으로 채우지 않는다.
- 코스 예문·일반 문장·뉴스·아나운서 학습자료 모두 같은 생성·저장 정책을 사용한다. 개인 소유 문장, DRAFT/HIDDEN 및 빈 대본은 이번 공개 학습자료 범위에서 제외한다.

## 2. 확인한 누락과 우선 복구

직전 운영 DB 조회에서 공개 공용 학습자료는 8개: CLASS_PRACTICE 5개, SENTENCE 1개, NEWS 2개였다. 숨김 공용 문장 1개는 별도다. 이 수치는 관측 당시 기준으로 배포 전에 다시 조회한다.

코스 예문 5개는 GENERATED·캐시 저장 완료, 승인 기록 0개였다. 일반 문장101에는 TTS가 없으며 기준 음성201/202는 cdn.example.com 주소로 DNS 조회에 실패했다. 뉴스 2개에는 기준 음성 연결이 없었다.

1차로 검수 제한 제거 코드를 배포하면 기존 5개 캐시를 재합성 없이 제공할 수 있다. 단, 접근 권한·현재 profile·digest·REJECTED 여부는 계속 확인한다. 이 변경만으로 문장101이나 뉴스 음성이 생성되지는 않는다.

## 3. 이미 작성한 제한 제거 코드

격리 체크아웃: `C:/Users/Public/Documents/ESTsoft/CreatorTemp/tts-publication-be-20260919`, 브랜치 `fix/tts-playback-without-review`, 기준 develop `f778e96`.

- ExampleTtsPersistence: APPROVED count >= 2 SQL 조건 제거. GENERATED, 요청 profile, 명시적 거절 차단, 바이트 digest/ETag 검사는 유지.
- ExampleTtsStore와 ExampleAudioService: approved 메서드를 playable로 바꿔 실제 의미를 표현.
- 관련 기존 테스트: 승인 기록 0건의 생성 음성 제공, 다른 profile 미제공, 거절 음성 차단, digest 변조 거부, 인증·voice 검사·GET에서 합성하지 않는 동작 확인으로 갱신.
- DB migration이나 기존 승인 행 수정은 필요 없다. 현재 저장된 5개 음성은 새 reader의 조건을 만족하면 즉시 제공된다.

## 4. 전체 문장 생성 기준

생성의 기준은 practice_examples 가입 여부가 아니라 `practice_contents`의 발행 상태와 대본이다. 예문 ID는 해당 content_id를 찾는 별칭으로 사용한다.

새 스키마 제안:

| 대상 | 역할 |
| --- | --- |
| 콘텐츠 TTS revision | 최초값 및 대본 변경 시 증가. 발행/숨김 상태도 작업 완료 시 재확인 |
| content_tts_jobs | content_id, text_revision, text_sha256, profile_revision, 상태, attempt, next_attempt_at, lease_owner/until, 오류 코드 |
| tts_audio_assets | 실제 S3 key, SHA-256, 길이·바이트 수·MIME, synthesis fingerprint, 사후 차단 상태 |
| 콘텐츠-음성 연결 | content_id와 현재 text_revision/profile에 대응하는 asset; 유일 제약으로 중복 primary 방지 |
| content_tts_outbox | 발행 가능한 INSERT·대본 변경·재발행과 동일 DB 트랜잭션에 기록 |

컬럼·테이블명은 신규 제안이다. 기존 스키마와 인덱스 검토 후 Flyway migration 번호를 확정한다. 합성 식별자는 텍스트 hash, locale, 모델·전처리·voice·speed·encoder revision을 포함한다. 다른 문장/설정의 음성을 재사용하지 않는다.

## 5. 자동화 경로

DB 발행 → outbox → AWS worker lease 획득 → RunPod 기존 TTS API → MP3 검증 → AWS가 S3 저장 → 현재 문장 revision 재확인 → DB 음성 연결 → 재생 가능.

- DB trigger는 outbox만 적재하며 HTTP를 호출하지 않는다. 기존 데이터 backfill과 주기적인 누락 보정 작업도 동일한 job 생성 경로를 사용한다.
- 기존 코스 전용 worker와 신규 worker가 같은 문장을 중복 합성하지 않게 content/profile 작업 소유권을 통일한다. 기존 5개 캐시를 digest 검증 후 신규 asset으로 이관하고 연결한다.
- 초기 합성 동시성1. 기존 RunPod 대기열·deadline·재시도 한도를 유지한다. 일시 실패만 지수 지연으로 재시도하고 영구 오류/소진은 FAILED로 남긴다.
- 외부 합성은 DB 트랜잭션 밖에서 수행한다. 완료 연결은 lease 소유자와 content의 최신 revision·발행 상태를 같은 짧은 트랜잭션에서 검사한다. 오래된 작업은 최신 음성을 덮어쓰지 못한다.
- S3 저장 후 DB 실패 시 재시도에서 digest로 재사용하고, 연결되지 않은 객체는 유예기간 후 정리한다. 사용 중인 음성과 기존 캐시는 삭제하지 않는다.
- RunPod에 추가 S3 쓰기 권한을 주지 않는다. AWS 역할의 전용 TTS prefix 쓰기·조회 권한을 확인한다.

## 6. 저장·재생 API 통합

생성 원본은 AWS 비공개 S3의 전용 TTS prefix에 저장한다. AWS의 기존 코스 `/api/practice-examples/{id}/audio`는 인증 후 연결된 asset을 `200 audio/mpeg`로 제공한다. 성공 형식을 audioUrl JSON으로 바꾸지 않는다.

일반 문장 ‘기준 발음 듣기’는 reference audio 목록과 playback URL API를 유지한다. TTS asset을 speakerType=TTS로 연결하고 AWS가 실제 presigned URL을 발급한다. expiresAt은 서명 만료와 일치시킨다. 현재 단순 DB URL + 임의 10분 시각 반환을 수정한다.

- 실제 유효한 아나운서/코치 음성은 보존한다. 문장101의 placeholder201/202는 새 TTS가 재생되는 것을 확인한 뒤 공개 목록에서 제외한다. 이력과 의존 FK를 먼저 조사하고 무조건 삭제하지 않는다.
- 최초 등록·교체 시 reference 목록 Redis 캐시를 무효화한다. FE는 문장 전환 시 이전 오디오를 멈추고 URL을 초기화하며, 서명 만료 시 새 URL을 받는다.
- 목록에 audioStatus(READY/GENERATING/FAILED/UNAVAILABLE) 계약을 추가할 경우 BE·FE를 함께 반영한다. 생성 중과 재생 장애를 구분하고 무한 재시도하지 않는다.
- 재생 GET이 즉시 합성하거나 사용자를 검수 대기 상태로 보내지 않는다. 생성은 발행/outbox가 시작한다.

## 7. 단계별 적용

1. 검수 제한 제거 BE 코드 검증·배포: 기존 캐시5개 재생 API 확인.
2. 새 스키마·공통 asset reader·outbox/worker를 비활성 설정으로 배포.
3. 운영 발행자료 snapshot 재조회. 기존5개는 검증·이관, 미생성 문장/뉴스는 제한된 backfill. 개인·숨김 자료 제외.
4. 문장101의 실제 TTS 저장·서명 URL·재생 확인 후 placeholder 공개 노출 종료.
5. 코스·일반 문장 reader를 공통 asset에 전환하고 중복된 기존 worker를 중지.
6. 신규 발행/대본 수정 자동화를 활성화. 기존 분석 GPU 지연·TTS 실패율을 확인.

단계별 변경 플래그로 되돌릴 수 있게 한다. 초기 rollback은 새 worker/reader를 비활성화하고 기존 캐시를 보존한다. 폐지된 2인 승인 조건을 fallback으로 다시 강제하지 않는다. S3/DB migration은 추가 방식으로 적용하고 되돌릴 때 사용자 데이터를 삭제하지 않는다.

## 8. 완료 조건과 검증

- 공개 학습자료 수와 현재 revision의 재생 가능 asset 수를 대조해 누락을 0으로 만든다. 사후 차단·실패는 별도 수치로 공개한다.
- 승인 기록0건으로 생성 음성 재생 성공. 손상·다른 profile·명시적 거절 음성은 제공하지 않음.
- 문장101, 뉴스, 코스 예문의 사용자 API→실제 MP3 재생 확인.
- 새 문장 발행 자동 생성, 중복 이벤트, lease 만료, 재시도, 문장 변경 중 완료 경합, 숨김 전환, S3 저장 후 DB 실패 복구 검증.
- 인증401, 권한403, 없는 자료404, 미준비503, 서명 만료와 FE 문장 전환·캐시 무효화 확인.
- 로그는 content/job/revision, cache hit, 생성시간, 상태·제한된 오류코드만 기록한다. 토큰·서명 URL·대본 전체를 기록하지 않는다.

이번 요청에서 전체 backfill·새 파이프라인·운영 배포는 실행하지 않았다. 이는 구현 순서와 검증 범위를 정한 계획이다.

## 이번 로컬 검증 결과

Java21 compileJava/compileTestJava 및 ExampleTtsPublicAudioTest 2개 통과. ExampleTtsPostgresTest는 VC_BE_TEST_POSTGRES_URL 미설정으로 1개 skipped이며, 승인0건·REJECTED·digest에 대한 실제 PostgreSQL 쿼리 검증은 남아 있다. git diff --check 통과. 커밋·push·운영 배포·DB 변경·추가 합성은 하지 않았다.
