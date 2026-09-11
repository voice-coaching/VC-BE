# RunPod HTTP Callback 구현 결과

작성일: 2026-09-11

## 작업 유형

- 외부 연동 구현
- API 설계 및 내부 API 구현
- 서비스 로직 작성
- DB schema/migration 반영
- 문서 최신화

## 구현 요약

Redis Stream MQ 대신 RunPod HTTP request + Backend callback 방식으로 분석 전송 경로를 추가했다.
Redis는 기존 cache와 보류된 Stream 전송안으로 남기고, RunPod 전송은 `AI_ANALYSIS_TRANSPORT=runpod_http`일 때만 활성화한다.

공개 사용자 API는 유지한다. 사용자는 기존처럼 분석 요청, 상태 조회, 결과 조회 API만 호출한다.
RunPod 전송과 callback은 Backend-AI 내부 계약으로 분리했다.

## 추가된 Backend 내부 API

| Method | Path | 역할 |
| --- | --- | --- |
| POST | `/api/internal/ai/analyses/{analysisId}/claim` | RunPod worker가 특정 `requestId`/`executionId` 실행을 점유 |
| POST | `/api/internal/ai/analyses/{analysisId}/heartbeat` | 처리 중인 실행의 claim 만료 시각 갱신 |
| POST | `/api/internal/ai/analyses/{analysisId}/result` | RunPod terminal 분석 결과 callback 수신 |

세 API는 `Authorization: Bearer {AI_ANALYSIS_CALLBACK_TOKEN}`으로 보호한다.
Spring Security에서는 `/api/internal/ai/**`를 JWT 인증에서 제외하고, controller 경계에서 전용 callback token을 검증한다.

## Backend -> RunPod 전송

- `AnalysisJobPublisher`의 RunPod HTTP 구현체를 추가했다.
- 분석 요청 생성 transaction 안에서 `analysis_results`와 `analysis_request_outbox`를 저장한다.
- RunPod 전송 payload는 `analysis_request_outbox.payload`에 저장한다.
- `RunPodAnalysisRequestOutboxDispatcher`가 pending outbox를 읽어 `POST {RUNPOD_ENDPOINT_URL}/v1/analysis-jobs`로 전달한다.
- 일시 실패는 재시도하고, 재시도 소진 시 active request에 해당하는 분석을 실패 처리한다.

## 운영 안정화 장치

| 장치 | 구현 상태 |
| --- | --- |
| Backend outbox | 기존 `analysis_request_outbox` 확장 재사용 |
| `requestId` | 기존 `active_request_event_id`와 outbox `event_id` 재사용 |
| `executionId` | `analysis_results.active_execution_id`, `analysis_request_outbox.execution_id` 추가 |
| claim | 내부 API와 `analysis_results` 점유 필드 추가 |
| heartbeat | 내부 API와 `last_heartbeat_at`, `claim_expires_at` 갱신 |
| retry | RunPod request outbox dispatcher 재시도 |
| stale execution 차단 | callback에서 `executionId` 검증 |
| callback 중복 차단 | `last_result_event_id`, `last_result_payload_sha256` 저장 |
| cancel | 기존 `analysis_cancellation_outbox`를 RunPod cancel dispatcher에 연결 |
| authorization issuer | RunPod HTTP transport에서는 Redis Stream HMAC issuer가 아니라 RunPod 전용 내부 issuer 사용 |

## DB 변경

Flyway migration `V16__add_runpod_http_analysis_execution_state.sql`을 추가했다.

추가 컬럼:

- `analysis_results.active_execution_id`
- `analysis_results.worker_instance_id`
- `analysis_results.claim_expires_at`
- `analysis_results.last_heartbeat_at`
- `analysis_results.last_result_event_id`
- `analysis_results.last_result_payload_sha256`
- `analysis_request_outbox.transport`
- `analysis_request_outbox.execution_id`
- `analysis_request_outbox.delivery_reference`

추가 index:

- `idx_analysis_results_active_execution_id`
- `idx_analysis_results_claim_expires_at`
- `idx_analysis_request_outbox_transport_dispatch`

추가 check constraint:

- `chk_analysis_request_outbox_transport`
- `chk_analysis_request_outbox_execution_id`
- `chk_analysis_results_active_execution_id`
- `chk_analysis_results_last_result_event_id`
- `chk_analysis_results_last_result_payload_sha256`

## 설정

RunPod HTTP 전송 활성화 예:

```env
AI_ANALYSIS_TRANSPORT=runpod_http
ANALYSIS_STREAM_ENABLED=false
RUNPOD_ENDPOINT_URL=
AI_ANALYSIS_API_TOKEN=
AI_ANALYSIS_CALLBACK_TOKEN=
AI_ANALYSIS_CALLBACK_BASE_URL=
```

`AI_ANALYSIS_TRANSPORT=disabled`이거나 RunPod 설정이 비어 있으면 public 분석 요청은 fail-closed로 거절된다.

## 검증 결과

- `gradlew.bat compileJava compileTestJava --no-daemon`: 성공
- `gradlew.bat test --no-daemon`: 실패

`test` 실패는 모든 테스트 클래스에 대한 `ClassNotFoundException` 형태로 발생했다.
`compileTestJava` 후 실제 `build/classes/java/test`에는 class 파일이 생성되어 있었기 때문에,
이번 코드의 특정 테스트 assertion 실패라기보다 Gradle/JVM 테스트 실행 환경 문제로 보인다.

## 운영 전 확인 필요

- RunPod의 `POST /v1/analysis-jobs`, `GET /v1/analysis-jobs/{requestId}`, `POST /v1/analysis-jobs/{requestId}/cancel` 구현
- RunPod이 Backend claim/heartbeat/result API를 호출하도록 연결
- 실제 S3 canonical audio/video object key와 SHA-256 대조
- callback token과 RunPod API token을 secret manager 또는 프로세스 환경변수로 주입
- Pod 종료, callback 재전송, stale execution, 사용자 취소/삭제 시나리오 QA
