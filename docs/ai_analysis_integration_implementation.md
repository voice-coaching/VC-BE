# intelligentAI 음성·영상 분석 연동 구현 현황

- 측정일: 2026-09-03
- 구현 기준 브랜치: `AI-API`
- 문서화 시점 HEAD: `2235d2015d1c764081105d3d3a7351ed0c088ade`

아래 동의 원장 보강은 아직 커밋되지 않은 현재 작업 트리를 기준으로 한다. 운영 release는
검토·커밋·전체 검증을 마친 깨끗한 SHA에서만 생성해야 한다.

## 문서 목적

이 문서는 VC-BE가 intelligentAI 비공개 워커와 연결하기 위해 구현한 API, 저장,
media normalization, Redis Stream, 취소·삭제·관측 및 배포 경계를 요약한다. 필드별
정본은 [Backend-AI Redis Stream 계약](api/ai-redis-stream-contract.md)이며, HTTP 필드와
상태 코드는 [API 명세](api/specification.md)를 따른다. 이 문서는 계약을 재정의하지
않는다.

현재 교차 저장소 계약은 다음 버전으로 고정돼 있다.

- request: `voice-coaching.analysis-request.v4`
- authorization: `voice-coaching.analysis-authorization.v3`
- result: `voice-coaching.analysis-result.v3`
- visual supplement: `voice-coaching.visual-supplement.v1`

## Backend 책임

VC-BE는 다음 실서비스 책임을 소유한다.

- Bearer 인증과 user/session/recording ownership을 검증한다.
- 업로드 URL을 발급하고 업로드 완료 시 실제 container·stream·codec을 검사한다.
- 원본을 canonical WAV와, 얼굴 처리 동의가 있는 경우 canonical MP4로 변환한다.
- canonical object의 digest·크기·MIME·duration과 opaque consent receipt를 DB에 저장한다.
- request generation마다 짧은 수명의 HMAC authorization을 발급하고 durable outbox를
  transaction 안에서 기록한다.
- request를 Redis Stream에 전달하고 result-v3를 PostgreSQL에 반영한 뒤 ACK한다.
- session 취소, timeout, 기록 삭제, 동의 철회 시 영속 cancellation outbox와 tombstone을
  발행한다.
- 원본·canonical media 삭제를 outbox로 재시도하고 queue/PEL/DLQ/삭제 지연을 관측한다.
- 인증된 사용자에게 DB에 확정된 결과만 public API DTO로 반환한다.

Backend는 발음 오류나 selected phone을 직접 판정하지 않는다. 이 판단은 Seungun이
소유한다. Backend는 result-v3의 pronunciation evidence와 선택적 same-attempt visual
supplement를 검증·저장·표현할 뿐 새 진단을 만들지 않는다.

## 동의 발급의 실제 경계

동의 발급은 외부 시스템을 기다리는 contract-only stub이 아니다. authenticated owner의
analyze/retry 요청이 `accepted=true`와 정확한 active policy revision을 제출하면,
`TrainingAnalysisRequestService`가 같은 transaction 안에서 새 request event를 만들고
`JpaProcessingConsentLedger`가 다음 evidence를 PostgreSQL에 `saveAndFlush`한다.

- `VOICE_ANALYSIS`: positive user/session/recording, request event UUID, canonical audio digest,
  policy revision, grant time과 opaque random receipt digest
- `FACE_VIDEO_PROCESSING`: positive user/session, source object-key digest, video consent policy,
  grant time과 별도의 opaque random receipt digest

raw consent 본문과 entropy는 Stream이나 public DTO에 노출하지 않는다. receipt 생성에는
`SecureRandom` 32 bytes와 SHA-256을 사용하고 voice request event는 원장에서 유일하다.
session 취소·timeout·사용자 철회는 active receipt를 revoke하며, revocation time은 grant
time보다 빠를 수 없다.

V14 migration은 positive subject ID, 안전한 policy 문자집합, scope별 nullable binding,
request UUID 형식, revocation 순서와 voice request-event uniqueness를 DB에서도 강제한다.
기존 데이터가 위반하면 migration을 우회하지 않고 fail-closed하므로 운영 적용 전에
aggregate count 기반 preflight와 정식 데이터 정정이 필요하다.

운영 DBA는 V14 적용 전에 식별자를 출력하지 않는 다음 read-only 집계로 위반 건수만
확인한다. 결과가 모두 0이 아니면 migration을 실행하지 말고 별도 승인된 데이터 정정
절차를 사용한다.

```sql
SELECT
    count(*) FILTER (WHERE user_id <= 0 OR training_session_id <= 0)
        AS invalid_subject_ids,
    count(*) FILTER (WHERE policy_revision !~ '^[A-Za-z0-9._-]{1,100}$')
        AS invalid_policy_revisions,
    count(*) FILTER (WHERE NOT (
        (scope = 'VOICE_ANALYSIS' AND recording_id > 0 AND request_event_id IS NOT NULL)
        OR (scope = 'FACE_VIDEO_PROCESSING' AND recording_id IS NULL AND request_event_id IS NULL)
    )) AS invalid_scope_bindings,
    count(*) FILTER (WHERE request_event_id IS NOT NULL AND request_event_id !~
        '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
        AS invalid_request_events,
    count(*) FILTER (WHERE revoked_at IS NOT NULL AND revoked_at < granted_at)
        AS invalid_revocation_order
FROM processing_consents;

SELECT count(*) AS duplicate_request_event_groups
FROM (
    SELECT request_event_id
    FROM processing_consents
    WHERE request_event_id IS NOT NULL
    GROUP BY request_event_id
    HAVING count(*) > 1
) duplicates;
```

동의 receipt가 생성되면 `HmacAnalysisAuthorizationIssuer`가 같은 transaction의 media와
request binding을 active key ID/policy에 묶어 서명한다. key ID와 policy는 intelligentAI
deployment attestation에도 동일하게 기록해야 하고, 실제 secret bytes는 VC-BE secret과
AI keyring secret에만 주입한다.

## HTTP와 비동기 처리 흐름

관련 public API는 다음과 같다.

| Method | Path | 역할 |
| --- | --- | --- |
| `POST` | `/api/training-sessions/{sessionId}/recordings/upload-url` | owner-bound private upload URL 발급 |
| `POST` | `/api/training-sessions/{sessionId}/recordings` | 업로드 검증, canonicalization, recording 등록 |
| `PATCH` | `/api/training-sessions/{sessionId}/recordings/{recordingId}/select` | 분석 대상 녹음 선택 |
| `POST` | `/api/training-sessions/{sessionId}/analyze` | 명시적 동의와 새 request generation으로 분석 요청 |
| `POST` | `/api/training-sessions/{sessionId}/analysis/retry` | 실패한 분석을 새 동의·grant로 재요청 |
| `GET` | `/api/training-sessions/{sessionId}/analysis/status` | durable 분석 상태 조회 |
| `GET` | `/api/analyses/{analysisId}` | 완료된 소유자 분석 결과 조회 |

```text
authenticated upload
  -> S3 source object
  -> ffprobe/ffmpeg sandbox and technical QC
  -> canonical WAV + optional canonical MP4
  -> PostgreSQL recording/consent/request outbox transaction
  -> analysis:request:v1
  -> intelligentAI request-v4 worker
  -> analysis:result:v1
  -> active-generation validation and PostgreSQL commit
  -> result XACK + XDEL
  -> restricted media deletion outbox
```

## 영상 codec과 포맷 처리

영상 container/codec parsing과 AI 입력 canonicalization은 Backend에서 수행한다.
구현은
[`FfmpegS3RecordingMediaNormalizer`](../src/main/java/org/example/voice/training/infrastructure/storage/FfmpegS3RecordingMediaNormalizer.java)에
있으며 route/controller에는 ffmpeg 로직이 없다.

| 업로드 container | 허용 video | 허용 audio | AI 전달 형식 |
| --- | --- | --- | --- |
| MP4/QuickTime | H.264 또는 HEVC | AAC | 단일 H.264/HEVC + AAC MP4 |
| WebM | VP8 또는 VP9 | Opus 또는 Vorbis | 단일 H.264/HEVC + AAC MP4로 변환 |

모든 허용 입력에서 16 kHz mono signed PCM WAV를 별도로 생성한다. 다중·누락·모호한
stream, 허용되지 않은 codec, duration/size 범위 위반, digest drift는 등록 전에
fail-closed한다. source GET과 DELETE는 직전에 확인한 ETag와 선택적 VersionId에
조건부로 묶이며, 교체된 객체를 처리하거나 삭제하지 않는다. DB rollback 시 등록되지
않은 canonical object도 best-effort로 정리한다.

ffmpeg/ffprobe는 JAR에 포함된 Python/libseccomp launcher 뒤에서 실행된다. launcher는
`no_new_privs`, 네트워크·process escape·`io_uring` 거부와 CPU, address-space,
output-file, file-descriptor, core-dump 제한을 적용한다. production에서는 VC-BE를
non-root/read-only 환경으로 실행하고 media workspace만 쓰기 가능하게 제공해야 한다.

## Task별 구현 기록

| Task | VC-BE 구현 | 커밋 |
| --- | --- | --- |
| 1 | canonical visual object, 얼굴 동의 receipt, request-v4와 public supplement 저장 | `2810098` |
| 2 | DB cancellation outbox와 무기한 Redis tombstone | `c8613f9` |
| 3 | DB terminal evidence 기반 Stream/outbox/index/tombstone retention | `ec6efc3` |
| 4 | ETag/VersionId 조건부 원본 처리와 canonical object 확정/rollback cleanup | `50d2686` |
| 5 | request/result UTF-8 byte bound와 bounded result DLQ | `2c4171b` |
| 6 | ffmpeg/ffprobe seccomp 격리, per-object 삭제 transaction, 운영 metric | `f10a221` |
| 7 | H.264/HEVC/VP8/VP9 codec matrix와 비공개 표본 opt-in test | `77a22f1` |
| 8 | loopback Actuator+public health gate와 이전 release 자동 rollback | `2235d20` |

## 주요 구현 위치

| 경로 | 책임 |
| --- | --- |
| [`TrainingAnalysisRequestService.java`](../src/main/java/org/example/voice/training/application/TrainingAnalysisRequestService.java) | owner/consent/admission 검증과 durable request 생성 |
| [`FfmpegS3RecordingMediaNormalizer.java`](../src/main/java/org/example/voice/training/infrastructure/storage/FfmpegS3RecordingMediaNormalizer.java) | 객체 identity 검증, codec parsing, WAV/MP4 canonicalization |
| [`HmacAnalysisAuthorizationIssuer.java`](../src/main/java/org/example/voice/analysis/infrastructure/authorization/HmacAnalysisAuthorizationIssuer.java) | request-v4 처리 권한 canonical HMAC 발급 |
| [`ProcessingConsent.java`](../src/main/java/org/example/voice/consent/domain/entity/ProcessingConsent.java) | scope별 동의 evidence와 binding 불변식 |
| [`JpaProcessingConsentLedger.java`](../src/main/java/org/example/voice/consent/infrastructure/JpaProcessingConsentLedger.java) | opaque voice/face receipt 발급과 session/user 철회 |
| [`V14__harden_processing_consent_invariants.sql`](../src/main/resources/db/migration/V14__harden_processing_consent_invariants.sql) | 기존 원장에 fail-closed DB constraint와 request-event unique index 추가 |
| [`AnalysisRequestOutboxDispatcher.java`](../src/main/java/org/example/voice/analysis/infrastructure/stream/AnalysisRequestOutboxDispatcher.java) | DB outbox에서 request Stream으로 전달 |
| [`RedisAnalysisResultConsumer.java`](../src/main/java/org/example/voice/analysis/infrastructure/stream/RedisAnalysisResultConsumer.java) | result-v3 bound/parse, DB handoff 뒤 ACK |
| [`AnalysisResultIngestionService.java`](../src/main/java/org/example/voice/analysis/application/AnalysisResultIngestionService.java) | active request generation의 상태·evidence·supplement 반영 |
| [`AnalysisCancellationOutboxDispatcher.java`](../src/main/java/org/example/voice/analysis/infrastructure/stream/AnalysisCancellationOutboxDispatcher.java) | durable cancellation tombstone 전달 |
| [`AnalysisStreamRetentionSweeper.java`](../src/main/java/org/example/voice/analysis/infrastructure/stream/AnalysisStreamRetentionSweeper.java) | terminal DB evidence 기반 안전한 retention |
| [`AnalysisOperationsObserver.java`](../src/main/java/org/example/voice/analysis/infrastructure/stream/AnalysisOperationsObserver.java) | Stream/PEL/DLQ/outbox/deletion aggregate 관측 |
| [`scripts/media_sandbox.py`](../scripts/media_sandbox.py) | ffmpeg/ffprobe process resource·syscall 제한 |
| [`scripts/deploy.sh`](../scripts/deploy.sh) | commit JAR 활성화, 내부/외부 health, rollback |

DB 변경은 `V10`부터 `V13` migration에 visual input/supplement, cancellation outbox,
request Stream identity와 retention 근거를 추가했고, `V14`에 processing-consent invariant를
보강했다. 기존 migration을 수정하지 않고 새 migration으로만 확장했다.

## 전달·취소·삭제 불변식

- request는 PostgreSQL outbox commit 전 Redis에 쓰지 않는다.
- AI result는 active `requestEventId`와 일치할 때만 DB 상태를 바꾼다.
- result Stream entry는 DB transaction commit 뒤에만 `XACK`+`XDEL`한다.
- 취소 tombstone에는 user/session/recording/consent 값이 없고 TTL도 없다.
- request/result Stream에는 무조건적인 `MAXLEN`을 적용하지 않는다.
- terminal DB 상태, 최소 보존 시간, indexed Stream entry 부재가 모두 증명된 뒤에만
  outbox/index/tombstone을 삭제한다.
- 정상/취소/실패/timeout/사용자 삭제 뒤 canonical media 삭제를 idempotent outbox로
  전달한다.

## 검증 결과

다음은 2026-09-03 로컬 실행 근거다.

| 검증 | 결과 | 범위 |
| --- | --- | --- |
| `./gradlew clean test bootJar --no-daemon` with Java 21 | `BUILD SUCCESSFUL` | 전체 Backend unit/integration-context와 executable JAR |
| synthetic codec matrix | 모두 통과 | H.264/AAC MP4, HEVC/AAC MOV, VP8/Vorbis WebM, VP9/Opus WebM |
| private device sample opt-in | 통과 | 승인된 HEVC/AAC portrait 표본 1개, 파일은 Git 미포함 |
| `bash -n scripts/deploy.sh` | exit 0 | deployment script syntax |
| workflow YAML parse와 `git diff --check` | exit 0 | deploy workflow와 변경 형식 |
| staged 보호 데이터·secret scan | 통과 | media/model/env/credential 미포함 |
| consent/issuer 관련 Java 21 tests | `BUILD SUCCESSFUL` | entity, JPA ledger, HMAC issuer, analyze/retry service, recording face consent |
| PostgreSQL 16 migration/startup smoke | exit 0 | V0~V14 적용, Hibernate validate, consent check constraints와 unique index 확인 |
| intelligentAI attested CUDA composition | exit 0 | 실제 local model artifact preload와 synthetic key/policy binding; Redis/S3 호출 없음 |

codec matrix와 비공개 표본은 mock S3 경계에서 실제 host ffmpeg를 실행해 probe,
canonical media 생성, digest와 cleanup을 검사했다. 비공개 표본 한 건의 통과는 전체
iOS/Android 기기·OS·촬영 설정 호환성을 의미하지 않는다.

## 배포 상태

- `implemented`: API admission, media normalization, PostgreSQL 동의 발급·철회 원장,
  HMAC grant, outbox, Redis 전달·취소·retention, result-v3 저장, 삭제와 관측 코드가 존재한다.
- `verified`: 전체 Gradle build/test, 합성 codec matrix, 제한된 비공개 표본,
  deployment script, 동의 관련 Java 테스트, PostgreSQL 16 V14 migration과 local AI CUDA
  attestation 조합이 통과했다.
- `contract-only`: 동의 issuer 자체와 local GPU model/artifact 조합은 더 이상
  contract-only가 아니다. 실제 production S3/TLS Redis, secret manager와 배포 target을
  사용한 사용자 요청의 end-to-end 실행은 아직 확인하지 않았다.
- `running`: 이 문서 작성 시 production deployment가 실행 중이라고 주장하지 않는다.

production workflow는 빌드한 JAR와 같은 commit의 `scripts/deploy.sh`를 함께 전송한다.
새 release는 systemd active, loopback `/internal/actuator/health`, public HTTPS OpenAPI를
모두 통과해야 한다. 실패하면 이전 symlink를 복원하고 이전 release health도 다시
확인한다. 자세한 운영 설정은 [Deployment Notes](architecture/deployment.md)를 따른다.

## 통합 배포 전 남은 gate

1. 실제 S3-compatible provider의 `If-Match`/VersionId GET·DELETE 동작과 least-privilege role
2. dedicated private TLS Redis의 Backend/AI ACL과 CA trust
3. VC-BE active key ID/policy와 worker keyring/deployment attestation 일치 및 secret 주입
4. intelligentAI의 전용 group read-only artifact/manifest와 GPU preload health
5. 새 분석 admission 중지와 기존 request/result Stream·PEL drain 또는 명시적 종결
6. Backend와 worker 배포 후 end-to-end request-v4/result-v3 DB 반영 및 media 삭제
7. 실패 시 양쪽 이전 release 복구와 동일 health gate 재통과

이 gate를 통과하기 전에는 synthetic/local 검증을 production readiness로 확대해석하지
않는다. 설정이 불완전하면 `ANALYSIS_STREAM_ENABLED` 경계와 production configuration
guard가 분석 요청을 fail-closed해야 한다.
