# DB 예문 기반 프론트 전환 및 RunPod TTS 자동 생성 계획

> 최신 적용: 2026-09-19 16:46 KST, AWS 토큰·nginx·워커 활성화 및 예문 5건 생성 완료. 실제 환경 변수 이름과 검증 결과는 [운영 연결 기록](example-tts-operations-20260919.md)을 따른다. 본문의 미활성 설명은 계획 시점 기록이다.

> 실제 실행 상태는 [배포·설치 진행 기록](db-example-tts-deployment-20260919.md)을 따른다. FE/BE 배포·운영 DB 예문 5개 등록·TTS 설치를 완료했고, AWS 전용 토큰 전송/프록시 적용 승인 전이라 생성 워커는 비활성이다. 아래 미구현 설명은 계획 작성 당시 기준이다.

작성: 2026-09-19 KST. 프론트 변경은 로컬 구현·검증을 완료했으며, 아래 TTS 작업 테이블·워커·RunPod 설치·배포는 **구현할 계획**이다. 운영 DB에 예문을 삽입하거나 음성을 생성하지 않았다.

## 1. 범위와 결정

사용자가 확정한 범위는 **예문·연습 데이터 전체를 DB로 전환**하는 것이다. 개발자 모드와 별도 프로토타입의 가상 사용자·학습 기록은 이번 제거 대상이 아니다.

- 서비스의 코스 예문은 `GET /api/courses/{courseId}/steps/{stepId}/practice-examples`에서 읽는다. 화면에 표시한 예문의 `practiceContentId`를 녹음·분석에 사용한다.
- 일반 문장·뉴스·아나운서 콘텐츠는 기존 `practice-contents` API 경로를 유지한다. API 실패 시 정적 예문으로 대체하지 않는다.
- ‘내 문장’은 `POST /api/practice-contents/custom`으로 저장한 후 조회한 콘텐츠로 연습한다. `SESSION_HISTORY`, 사용자 소유권과 서버 암호화 정책을 그대로 적용한다. 비공개 사용자 문장은 이번 공개 예문 TTS 자동 생성 대상에서 제외한다.
- 새 **발행 예문 revision의 DB 트랜잭션이 커밋되면 자동으로 TTS 작업을 생성·실행**한다. 사용자의 듣기 클릭이나 수동 배치 시작을 생성 조건으로 삼지 않는다.
- 기존 TTS 계획의 모델·전용 프로세스·서비스 토큰·MP3 계약을 재사용한다. 자동 생성과 공개 승인은 구분한다. 첫 공개는 기존 계획대로 검수된 음원만 제공한다. 사람의 검수를 자동 생성 작업의 lease 안에서 기다리지 않는다.

## 2. 코드에서 확인한 제약

확인 기준: BE `4d6d2c0c0f57db652cf1a63a426e7ca4f9a9d19c`, FE 작업 기준 `b59135afcd38d9b2842ccb26f0b5fa1f37c864a2`. 운영 배포 리비전이나 실제 DB의 예문 수를 뜻하지 않는다.

| 기존 구현 | 설계에 반영할 사항 |
| --- | --- |
| V25의 `practice_example_sets` + `practice_examples` | 한 세트는 정확히 5개이며 commit 시 지연 제약 검증. 기존 발행 세트·예문은 수정/삭제 불가 |
| 예문은 `practice_contents`를 참조 | 본문을 FE 복사본으로 합성하지 않고 서버 snapshot에서 읽음 |
| `published_at <= now`인 revision을 조회 | 미래 예약 발행은 공개 전 합성할 수 있지만 예약 시각 전에는 음성 API로 노출하지 않음 |
| 세션은 예문 세트/revision을 고정 | 새 예문 발행 시 진행 중 연습의 문장·음성을 교체하지 않음 |
| `example_audio_cache` BYTEA, lease 5분 | 작은 MP3 저장을 재사용하되 작업 재시도·승인 상태는 신규 테이블로 관리 |
| 현재 Google 캐시 미스 동기 합성 | RunPod는 자동 생성 워커가 담당하고 공개 GET은 승인된 캐시 조회만 수행 |
| 사용자별 quota와 전역 quota | 관리 워커용 제한을 별도로 구현. 임의 userId=0으로 사용자 요청을 가장하지 않음 |

‘DB에 문장 하나 추가’는 현 스키마에서는 **유효한 5개 예문 세트를 새 revision으로 발행**하는 작업이다. 임의 단독 INSERT는 기존 제약에 실패한다. 개수 제한을 바꾸려면 별도 계약 변경이 필요하다. 이번 계획은 제약을 유지한다.

## 3. 생성 이벤트를 빠뜨리지 않는 방법

신규 Flyway migration에서 `example_tts_outbox`를 만들고 `practice_examples AFTER INSERT` 트리거로 `example_id` 이벤트만 기록한다. 트리거 안에서 HTTP·파일 생성·합성은 실행하지 않는다. 예문과 outbox가 같은 트랜잭션에서 커밋/롤백되므로 SQL 발행 도구와 애플리케이션 발행 경로 모두 포착한다.

1. 정식 발행 경로가 콘텐츠와 5개 예문을 한 트랜잭션으로 저장한다.
2. 각 예문 INSERT가 outbox 행을 기록한다. 세트 완전성 제약 실패 시 이벤트도 함께 롤백한다.
3. AWS의 전용 TTS scheduler가 커밋된 이벤트를 작은 배치로 잠근다(`FOR UPDATE SKIP LOCKED`).
4. 선택한 불변 합성 profile별 작업을 UNIQUE 키로 UPSERT하고 outbox 처리 완료를 **같은 DB 트랜잭션**으로 기록한다.
5. DB 트랜잭션을 종료한 뒤 워커가 RunPod를 호출한다. 원격 호출 동안 DB 행 잠금을 유지하지 않는다.

설정이 disabled이면 이벤트를 소모하지 않고 대기한다. 활성화 시 쌓인 예문을 순차 처리한다. 모델 revision 변경은 과거 이벤트의 재소비에 의존하지 않고 새 profile 대상으로 reconciliation 작업을 만든다.

보완책으로 5분 주기 reconciliation을 둔다. 지원되는 공개 코스 예문 중 현재 profile 작업이 없는 항목을 UNIQUE UPSERT한다. 초기 기존 데이터 보충, migration 경계, 임시 trigger 비활성화 등 운영 누락을 복구한다. 원문이나 비밀값은 outbox에 복제하지 않는다. 기록은 소량씩 만료·정리하고 미처리 이벤트는 보존한다.

## 4. 신규 DB 모델과 상태

migration 번호는 구현 시 최신 Flyway 번호 다음으로 결정한다. V25를 직접 수정하지 않는다.

| 테이블 | 핵심 필드·제약 |
| --- | --- |
| `example_tts_outbox` | id, example_id FK, event_type, created_at, processed_at; `(example_id,event_type)` UNIQUE |
| `example_tts_profiles` | immutable revision PK, synthesis_fingerprint UNIQUE, provider/model/전처리/encoder/voice/rate/장치·seed 설정의 manifest digest. 토큰 저장 금지 |
| `example_tts_jobs` | id, example_id, profile_revision, text_sha256, cache_key, state, attempt, next_attempt_at, lease_owner, lease_until, fencing_version, request_id, last_error_code, timestamps; `(example_id,profile_revision)` UNIQUE |
| `example_audio_approvals` | job_id, MP3 SHA-256, reviewer_id, decision, reviewed_at. 검토자별 중복 승인 금지; 승인 시 실제 바이트 digest와 대조 |

생성 상태는 `PENDING → RUNNING → GENERATED`, 일시 실패는 `RETRY_WAIT → RUNNING`, 영구 오류/재시도 소진은 `FAILED`다. 품질 상태는 `PENDING_REVIEW / APPROVED / REJECTED`로 별도 관리한다. `GENERATED`는 파일 생성·기술 검증 완료를 뜻하며 곧바로 사용자 공개를 뜻하지 않는다.

연속 등록·다중 scheduler·재기동에도 하나의 예문/profile 작업만 존재한다. 같은 텍스트가 서로 다른 예문에 있더라도 첫 구현은 별도 provenance를 보존하고 자동 합치지 않는다.

동적 추가에 맞춰 **기존 계획의 배포 시 고정 승인 manifest를 DB 승인 테이블로 대체**한다. 음원 추가마다 백엔드 재배포가 필요하지 않게 한다. 모델/전처리 manifest는 여전히 불변 revision으로 유지한다.

## 5. 워커, 중복 실행과 오류 처리

초기 설정 제안: poll 1초, 합성 동시 실행 1, 작업 lease 90초, 내부 갱신 15초, 한 시도 전체 deadline 30초. 기존 캐시 lease 5분을 그대로 별도로 잡아 두지 말고 자동 작업의 소유권과 완료 CAS를 단일 경계로 통합한다. Google 기존 경로는 분리 보존한다.

- 작업 claim 시 `fencing_version`을 증가시키고 owner/version이 일치할 때만 성공·재시도 상태를 저장한다. lease가 만료된 이전 worker의 늦은 응답은 현재 상태를 덮어쓰지 못한다.
- 발행 snapshot의 text hash와 profile을 고정한다. `requestId`는 개별 시도마다 발급하고 해당 전송 재확인에는 같은 값을 사용한다.
- RunPod의 bounded requestId 중복 방지와 BE의 영속 UNIQUE/lease를 함께 쓴다. 원격에서 성공했으나 응답이 소실되면 이후 복구 시 중복 합성이 발생할 수 있다. **exactly-once 추론을 보장한다고 표현하지 않는다.** 공개 결과 확정은 owner/version CAS로 1회만 허용한다.
- DNS/연결 장애·429·503·504는 backoff+jitter로 최대 5회 시도한다. 초기 지연 후보는 10초, 30초, 2분, 10분이며 Retry-After는 최대 10분 안에서 존중한다. RunPod 모델 재기동 중에는 워커 접수를 일시 중단한다.
- 내부 401 또는 profile mismatch는 개별 문장을 반복 재시도하지 않고 해당 profile dispatch를 중단·알림한다. 400/413/415/422와 손상된 출력은 제한된 오류 코드로 실패 처리하고 원인을 수정한 뒤 재개한다.
- 완료 CAS 전에 캐시 바이트·etag와 생성 metadata를 저장하는 하나의 DB 트랜잭션을 사용한다. 프로세스가 중간에 죽으면 ‘완료인데 파일 없음’ 상태가 생기지 않도록 한다.
- 사용자 GET의 캐시 미스가 작업을 새로 만들거나 재시도를 폭증시키지 않는다. 수동 재시도는 운영 권한, 기록, 동시성 상한을 갖는다.

## 6. RunPod와 파일 제공

호출은 기존 계획의 `POST /v1/tts/synthesize` JSON → `200 audio/mpeg` 계약을 쓴다. AWS→RunPod 전용 토큰, TLS/redirect 제한, request ID/revision/digest 검증을 유지한다. MP3는 2MB 이하, 전체 decode 및 duration/sample rate/channel 검증을 통과해야 후보 캐시에 저장한다.

초기 저장소는 **AWS DB BYTEA 캐시**로 결정한다. 파일을 영구 RunPod `/tmp`에 두거나 브라우저에 RunPod URL을 반환하지 않는다. RunPod는 합성 결과 전송 후 임시 파일을 정리한다. 용량 증가 시 S3 별도 prefix+객체 metadata 테이블로 이관하는 작업을 별도로 잡는다. 이번 단계에 RunPod S3 쓰기 권한은 필요하지 않다.

프론트는 `GET /api/backend/api/practice-examples/{id}/audio`를 호출한다. 승인된 바이트는 MP3로 반환하고, 아직 생성/검수 중이면 기존 `503 TTS_UNAVAILABLE` 계약을 유지한다. 프론트가 오류 JSON을 오디오로 재생하거나 브라우저 TTS로 자동 대체하지 않는다. 실패·미등록 안내와 재시도 버튼을 제공한다.

관리 화면/CLI는 작업별 생성 상태, 제한된 오류 코드, 실제 MP3 미리 듣기, 승인/반려를 제공한다. 일반 JWT만으로 승인할 수 없으며 역할 기반 운영 권한을 검사한다. 2인 청취 검수 규칙을 유지하되 **합성은 DB 등록 후 사람의 조작 없이 진행**한다. 향후 자동 공개 정책은 별도 품질 평가 후 변경한다.

## 7. 프론트 전환 구현과 데이터 이관

FE 작업 위치: `C:\Users\Public\Documents\ESTsoft\CreatorTemp\db-practice-fe-20260919`, 브랜치 `feat/db-practice-examples-tts`.

- `api.examples.list/getAudio`와 인증 refresh를 공유하는 바이너리 요청을 추가한다.
- `CourseLesson`은 DB 예문·개수·hint를 표시하고 로딩/빈 목록/실패/재조회 상태를 구분한다.
- 선택된 예문 ID, revision, `practiceContentId`를 전달한다. 연습 진입 시 코스/단계/예문/본문 일치를 확인하며 이전 정적 `consonant-1` URL을 다른 문장으로 조용히 변환하지 않는다.
- `local-script` 가짜 ID를 제거하고 custom content 생성·조회 후 실제 세션 생성 경로를 사용한다. 동일 본문 재시도에는 동일 Idempotency-Key를 재사용한다.
- 기존 FE `/api/tts`는 정적 텍스트/Google 직접 호출을 제거하고 **DB ID용 백엔드 프록시 adapter**로 전환한다. 기존 무인증 정적 ID 클라이언트와 호환되지 않는다. 새 프론트 배포·클라이언트 새로고침을 함께 안내한다.
- 정적 35개 원문은 FE `docs/migration/legacy-practice-examples.ts`에 등록 후보로 보존한다. 런타임에서 import하지 않는다. 기존 ‘강조’ 단어와 BE의 focus enum은 의미가 다르므로 강조 문구를 hint로 옮길지 검토하고 임의 enum 매핑을 하지 않는다.

배포 전 실제 DB의 course/step/교육 revision/발행 예문을 읽기 전용으로 대조한다. 기존 DB 예문이 있으면 그 내용을 우선한다. 없는 세트만 명시적 매핑표로 등록하며 course title 정규식으로 자동 배정하지 않는다. dry-run에서 신규 콘텐츠·세트·예문 수와 중복 text hash를 제시하고 정식 발행 도구가 하나의 트랜잭션으로 적용한다. 운영 데이터 등록은 이번 프론트 변경에서 실행하지 않았다.

코스 예문 API가 미등록이면 현재 서버는 503을 반환한다. 따라서 **프론트 코드 전환 완료와 운영 예문 등록·음성 준비 완료는 별개**다. DB 준비 없이 프론트만 배포하면 기존 정적 문장 대신 명시적 미등록 오류가 보인다.

## 8. 구현·배포 순서 및 검증

1. FE 변경을 검토·테스트하고 실제 DB 매핑 및 서버 API 가용성을 확인한다. 개발자·프로토타입 데이터와 일반 서비스 DB 경로를 구분한다.
2. RunPod의 실제 GPT 의존성·CLOVA 임시 backoff·영속 마운트·분석 부하를 확인한다. TTS 전용 환경·manager를 구성하고 GPU/CPU 품질·속도·간섭을 비교한다.
3. BE에 profile/outbox/job/approval 테이블·INSERT 트리거·reconciliation을 추가한다. provider/dispatch disabled로 배포하며 기존 분석과 Google 설정은 유지한다.
4. BE worker, RunPod adapter, cache reader, 관리 검수 경로를 구현한다. 테스트 profile과 제한된 예문으로 자동 생성→기술 검증→승인→공개 API를 확인한다.
5. 기존 예문을 reconciliation으로 backfill한다. 신규 revision 발행 이벤트와 기존 backfill이 겹쳐도 중복 작업이 없어야 한다.
6. 제한 대상의 새 예문을 발행하고 **듣기 API를 호출하지 않아도 생성됨**을 확인한다. 생성 후 프론트가 동일 본문·revision의 MP3를 재생하고 실제 콘텐츠 ID로 분석하는지 검증한다.
7. 서비스 코스 전체의 DB 예문과 승인 음성을 준비한 후 새 FE를 배포한다. 새 세트 등록부터 생성 시작까지 warm 조건 5초 이내를 초기 목표로 두되, backlog/모델 미준비 시간과 검수 대기 시간을 따로 보고한다.

필수 검증은 다음과 같다.

- 5개 세트 정상 commit 시 이벤트 5개, 제약 실패/rollback 시 0개; 재발행 revision과 예약 발행 처리.
- 다중 scheduler, worker 강제 종료, lease 만료, 늦은 응답, 응답 유실, 재시도 소진, profile 전환.
- DB 저장 실패 시 완료 노출 금지, 승인 후 digest 변경/미승인 MP3 제공 금지, 과거 세션의 고정 revision 유지.
- 잘못된 MIME·빈 오디오·2MB 초과·손상된 MP3·401/409/429/503/504 처리; 토큰·원문 로그 미노출.
- FE 예문·녹음 대본 일치, 이전 정적 URL 오류, 인증 refresh, 문장 변경 중 취소, 재생 종료/실패, object URL 회수.
- 동일 팟 동거 시 분석 단독 대비 p95 증가 10% 이내라는 기존 후보 기준과 OOM/누락 callback/분석 lease 만료 0건을 실제로 측정한다.

관측 지표: oldest pending age, backlog, generated/failed/retry counts, 승인 대기 수, profile별 합성 p50/p95, MP3 크기·길이, 분석 지연, RAM/VRAM peak. model load와 warm 합성 시간을 분리한다.

rollback은 dispatch를 먼저 중지하고 진행 중 lease를 정리한 뒤 검증된 provider/profile/cache 조합으로 되돌린다. 테이블·이벤트·기존 승인 음원을 삭제하지 않는다. FE 데이터 소스를 mock으로 복구하지 않으며 API 장애를 안내한다. TTS 때문에 CLOVA를 다시 기동하지 않는다.

## 9. 이번 작업 검증 결과

FE API·인증·예문 연결·분석 polling·코스 진도 테스트 44개, 기존 API 계약 검사 62개 및 refresh 검사, TypeScript, 변경 파일 ESLint, Next production build(`--webpack`), git diff 공백 검사를 통과했다. 실제 운영 DB 조회/등록과 브라우저 오디오·새 녹음 E2E, TTS 자동화 구현·설치·배포는 수행하지 않았다. 원래 FE dev 작업 디렉터리는 유지하고 별도 worktree에서 변경했다.

## 10. 관련 근거

- [기존 RunPod TTS 런타임·바이너리 계약 계획](runpod-tts-integration-plan-20260919.md)
- [발행 세트·캐시 스키마 V25](../../src/main/resources/db/migration/V25__add_practice_examples_and_audio.sql)
- [예문 조회·세션 revision 선택](../../src/main/java/org/example/voice/practiceexample/infrastructure/PracticeExamplePersistence.java)
- [기존 음성 캐시 서비스](../../src/main/java/org/example/voice/practiceexample/application/ExampleAudioService.java)
- [사용자 문장 생성·중복 방지](../../src/main/java/org/example/voice/practicecontent/application/CustomContentService.java)
