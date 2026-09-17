# Backend–RunPod HTTP 계약 v1.1

2026-09-17 갱신. 제어 계약은 `voice-coaching.runpod-http.v1.1`이며 결과는 기존 v1과 CLOVA 점수를 포함하는 v2를 수신한다. 공개 상세 API는 기존 `overallScore` 필드에 검증된 점수를 반환한다. 아래 2026-09-16 검증 절은 당시 기록이다.

## 비교와 결정

| 기준 | 기존 백엔드 명세 | 현재 팟 v1.1 | 결정 |
| --- | --- | --- | --- |
| 작업 상태의 권위 | PostgreSQL + outbox | Backend DB를 최종 권위로 사용 | 기존 DB/outbox 유지 |
| 실행 임대 | 워커의 claimedUntil/heartbeatAt 사용 | 서버 시간, deadline으로 제한된 lease | v1.1 |
| 전달 무결성 | 요청/실행 ID 검사 | ID + RFC8785 요청 digest | v1.1 |
| 실행 소유권 | 재claim 시 워커를 덮어쓸 수 있음 | claim 소유자만 heartbeat/result 가능 | v1.1 |
| 중복 결과 | 공개 envelope, 단순 완료 상태 | 동일 event/content는 DUPLICATE, 변경된 내용은 충돌 | v1.1 |
| 준비 상태 | 설정 문자열 존재 여부 | 인증된 backend readiness 및 worker dependencies | v1.1 |
| JSON 경계 | 일부 optional 필드, 공용 DTO 포함 | 명시적 null, unknown/duplicate 필드 거부, 크기 제한 | v1.1 |
| 구현 비용 | 단순하지만 장애/시간/소유권 의미가 모호함 | 스키마 검증과 canonicalization 의존성 필요 | 운영 무결성 이점이 더 큼 |

**결론:** HTTP 내부 계약은 팟 v1.1로 통일한다. 백엔드 DB와 공개 API는 유지한다. 단순히 버전 문자열이나 응답 모양만 맞추지 않고 실행 소유권과 만료 조건을 함께 적용한다.

## 단일 스키마 기준

- [제어/요청 JSON Schema](../contracts/runpod_http_control_v1.schema.json)
- [결과 JSON Schema](../contracts/runpod_result_v1.schema.json)

두 파일의 최초 기준은 worker revision `b4057a008ad45c89f540e4cb6e19b6f29cd53aed`다. 이후 결과 스키마에 v2 점수와 상세 기준표 근거를 추가했다. Gradle이 같은 파일을 JAR의 `contracts/`에 포함하고 백엔드 런타임이 직접 검증한다. 점수의 의미와 산식은 [상세 CLOVA 기준표](clova-detailed-scoring-v2.md)를 따른다.

RFC8785 JCS를 적용한 JSON의 UTF-8 bytes에 SHA-256을 계산한다. 요청 digest는 DB outbox의 **불변 요청 payload**와 비교한다. 단순 문자열 hash나 언어별 기본 JSON 정렬을 사용하지 않는다. 구현 근거: [Java JCS](https://github.com/erdtman/java-json-canonicalization), [JSON Schema validator](https://github.com/networknt/json-schema-validator/tree/1.5.9).

## 인증과 JSON 규칙

- Backend → Pod: `Authorization: Bearer {AI_ANALYSIS_API_TOKEN}`
- Pod → Backend: `Authorization: Bearer {AI_ANALYSIS_CALLBACK_TOKEN}`
- RunPod 계정 API 키는 서비스 토큰으로 사용하지 않는다.
- 내부 AI 경로는 사용자 JWT 필터를 통과하지 않으며 별도 서비스 토큰 검증을 받는다.
- Content-Type은 application/json, Content-Encoding은 미지정 또는 identity.
- 제어/요청 본문 최대 65,536 bytes, 결과 최대 1,048,576 bytes. chunked 본문에도 동일 적용.
- duplicate key, unknown field, 잘못된 UTF-8/Unicode, 비유한 숫자, 필수 필드 누락은 거절한다.
- null 허용 필드는 생략하지 않고 null로 보낸다.
- UUID는 소문자 정규 형식. ID는 1..9,007,199,254,740,991.
- 시간은 UTC, 정확히 밀리초 세 자리: `2026-09-16T06:00:00.000Z`.
- 내부 응답은 아래의 평평한 JSON이다. 공개 API의 result/message/data envelope를 사용하지 않는다.

## 경로

| 방향 | 경로 | 정상 응답 |
| --- | --- | --- |
| Backend → Pod | POST /v1/analysis-jobs | 202 또는 동일 실행 재전달 시 200, jobAccepted |
| Backend → Pod | GET /v1/analysis-jobs/{requestId}?executionId={executionId} | jobStatus |
| Backend → Pod | POST /v1/analysis-jobs/{requestId}/cancel | cancelResponse |
| Pod → Backend | GET /api/internal/ai/worker-readiness | workerReadiness |
| Pod → Backend | POST /api/internal/ai/analyses/{analysisId}/claim | leaseResponse |
| Pod → Backend | POST /api/internal/ai/analyses/{analysisId}/heartbeat | leaseResponse |
| Pod → Backend | POST /api/internal/ai/analyses/{analysisId}/result | resultAck |

worker-readiness는 인증, transport/configuration, 필요한 DB 컬럼 접근을 확인한다. 팟의 readiness를 다시 호출하지 않아 의존성 순환이 없다. ready=200, not_ready=503. 이 응답은 실제 추론이나 S3 준비 완료를 대신하지 않는다.

## 요청·claim·heartbeat

analysisRequest는 기존 requestId/executionId/analysisId/recordingId/contentId, 대본·SHA256, canonical WAV/선택 MP4 metadata, deadlineAt을 사용한다. audio-only도 `video:null`을 보낸다. MP4 duration을 모르면 `durationMs:null`이다. 소유자 식별 정보, 원본 미디어, 임의 callback URL은 보내지 않는다.

claimRequest:

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "workerInstanceId": "33333333-3333-4333-8333-333333333333",
  "requestPayloadSha256": "64 lowercase hex characters"
}
```

heartbeatRequest는 위 ID 세 개만 포함한다. 이전 schemaVersion, claimedUntil, heartbeatAt 필드는 금지된다.

leaseResponse는 analysisId, requestId, executionId, workerInstanceId, serverTime, leaseExpiresAt, continueProcessing=true이다.

- analysis row를 잠근 상태에서 실행 ID, 취소/terminal 상태, 등록 녹음, deadline, 요청 digest와 owner를 검사한다.
- 최초 claim만 소유자를 정한다. 다른 워커는 같은 실행을 탈취할 수 없다.
- 같은 워커의 살아 있는 claim 재전달은 허용한다.
- heartbeat는 최초 claim을 만들거나 만료 lease를 되살릴 수 없다.
- leaseExpiresAt=min(서버 현재 시간+claimTTL, 불변 deadline).
- 만료 작업은 sweeper가 FAILED로 종료하고 취소 outbox를 기록한다. 이번 구현은 자동 실행 세대 재할당을 하지 않으며 공개 retry API로 새 작업을 요청한다.

## 결과와 중복 처리

결과 스키마에 있는 필드만 전송한다. workerInstanceId는 필수다. STT·segments·debug·closedBetaLipObservation은 HTTP 계약에서 허용하지 않는다.

- `voice-coaching.runpod-analysis-result.v1`은 점수 필드가 없는 기존 계약이다.
- `voice-coaching.runpod-analysis-result.v2`는 `overallScore`, `scoringEvidence`가 모두 필수이며, COMPLETED에서는 유효한 값, FAILED에서는 둘 다 null이다.
- `scoringEvidence.rubricRevision`으로 기존 3항목 v1과 상세 9항목 v2를 구분한다. 상세 v2는 항목별 근거 개수·단계와 기준표/프롬프트 SHA-256을 저장한다.
- 워커와 백엔드가 근거 개수, 단계, 제외 항목 및 최종 합산을 검증한다. V27의 `clova_score_evidence`와 기존 `overall_score`를 함께 저장하며, 기존 결과에 점수를 소급 생성하지 않는다.
- 프론트는 analyze의 접수 응답을 받은 뒤 status를 polling하고, COMPLETED일 때 `GET /api/analyses/{analysisId}`로 피드백과 `overallScore`를 조회한다. 승급 submit은 같은 DB 점수를 사용한다.

- 새 결과는 현재 request/execution, claim 소유자, lease/deadline, recording, audio hash와 근거 관계를 만족해야 한다.
- 같은 eventId와 같은 canonical content는 DB에 이미 저장된 경우 DUPLICATE이다. 전달 재시도는 lease/deadline 만료 후에도 승인 가능하다.
- 같은 eventId의 변경된 내용은 RESULT_EVENT_CONFLICT, 다른 eventId로 이미 확정된 결과를 바꾸려면 RESULT_ALREADY_FINALIZED.
- 취소되거나 삭제된 녹음은 늦은 결과로 복원하지 않는다.
- 결과 transaction이 commit된 후에만 APPLIED를 응답한다. DB 실패는 재시도 가능한 503이다.

resultAck:

```json
{
  "eventId": "44444444-4444-4444-8444-444444444444",
  "analysisId": 1,
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "status": "APPLIED",
  "serverTime": "2026-09-16T06:00:00.000Z"
}
```

status는 APPLIED 또는 DUPLICATE이다.

## 에러와 재전달

에러는 reasonCode, message, requestId, executionId, retryable을 가진다. 신뢰할 수 있는 요청 식별자가 없으면 두 ID는 null이다. 내부 예외/토큰/미디어를 message에 노출하지 않는다.

| HTTP | 주요 reasonCode |
| --- | --- |
| 400 | INVALID_JSON |
| 401 | UNAUTHENTICATED |
| 404 | TARGET_NOT_FOUND |
| 409 | STALE_EXECUTION, PAYLOAD_DIGEST_MISMATCH, WORKER_CONFLICT, CLAIM_REQUIRED, LEASE_EXPIRED, DEADLINE_EXCEEDED, ANALYSIS_CANCELLED, ANALYSIS_TERMINAL, RESULT_EVENT_CONFLICT, RESULT_ALREADY_FINALIZED |
| 413 | PAYLOAD_TOO_LARGE |
| 415 | UNSUPPORTED_MEDIA_TYPE |
| 422 | VALIDATION_FAILED |
| 503 | DEPENDENCY_UNAVAILABLE |

429/502/503/504 에러는 retryable=true이며 Retry-After를 제공한다. 동일 eventId/content로 재전송한다.

요청 dispatcher는 짧은 DB 예약 → transaction 밖 HTTP → 별도 완료 transaction 순서다. 프로세스 중단 시 예약 만료 후 재전송한다. 이미 claim된 작업은 접수 응답 유실만으로 실패 처리하지 않는다. 취소도 같은 방식이며 현재 분석의 새 executionId가 아니라 원래 outbox의 executionId를 사용한다.

## 최초 v1.1 도입의 배포 및 검증 범위 — 2026-09-16 기록

- V17 migration은 execution_deadline_at을 추가하고 기존 HTTP outbox에서 deadline을 가져온다.
- 이 변경은 이전 워커 시간 필드를 사용하는 callback과 호환되지 않는 내부 계약 변경이다.
- 배포 전 이전 형식의 미처리 outbox/진행 작업을 drain하거나 명시적으로 실패 종료하고 새 요청으로 재시도해야 한다. 기존 payload를 다른 내용으로 덮어쓰지 않는다.
- 팟은 이미 이 스키마를 사용하므로 워커 코드 변경이 필요하지 않다. 백엔드 배포와 V17 적용이 필요하다.
- 팟의 저장소/readiness 환경 설정 누락은 별도 운영 문제다. 이 코드 변경만으로 실제 음성 추론이 준비되었다고 판단하지 않는다.

## 검증 결과 — 2026-09-16

- `gradlew.bat test bootJar --no-problems-report`: BUILD SUCCESSFUL. 전체 172개 중 163개 통과, 9개 skipped, 실패/오류 0.
- 테스트는 claim digest/owner 충돌, 미점유 heartbeat, 만료 lease, 완료/실패 결과, 중복/변조 이벤트, 인증·JSON 경계, DB 실패 응답, 만료 종료, HTTP 중 transaction 해제, 접수 응답 유실, 원래 실행 취소를 포함한다.
- 테스트가 생성한 analysisRequest/workerReadiness/leaseResponse/resultAck/error JSON을 현재 A40 팟의 JSON Schema validator로 검증하여 모두 통과했다.
- 동일 analysisRequest의 Java JCS SHA-256과 팟 Python `rfc8785` SHA-256이 일치했다.
- 배포 JAR 생성은 확인했다. 운영 백엔드 배포, 운영 V17 migration 적용, 실제 음성 추론 종단 간 검증은 수행하지 않았다.
