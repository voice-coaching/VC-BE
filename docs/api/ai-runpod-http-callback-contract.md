# Backend-AI RunPod HTTP Callback 계약

## 상태

- 작성일: 2026-09-10
- 프로젝트: voice
- 전송 방식: Backend -> RunPod HTTP 요청, RunPod -> Backend HTTP callback
- Redis 사용: 기존 애플리케이션 cache 용도로 유지한다. 이번 단계의 Backend-AI 분석 전송에는 Redis Stream MQ를 사용하지 않는다.
- 공개 API 호환성: 클라이언트가 호출하는 기존 분석 API는 유지한다.
- 운영 기준: Backend PostgreSQL job/outbox를 작업 원장으로 두고, RunPod 실행은 claim/heartbeat/executionId로 관리한다.

## 배경

기존 Backend-AI 계약은 Redis Stream을 MQ처럼 사용하는 구조였다.

```text
Backend -> Redis Stream analysis:request:v1
AI worker -> Redis Stream analysis:result:v1
Backend consumer -> PostgreSQL
```

하지만 현재 AI worker는 RunPod에 배포되어 있고, Backend EC2의 private network 밖에 있다.
EC2 내부 Docker Redis를 RunPod에서 접근하게 만들려면 Redis public 노출, TLS, 고정 outbound IP allowlist,
보안그룹, pending/retry 운영, 추가 모니터링이 필요하다.

초기 RunPod 연동은 운영 복잡도를 줄이기 위해 HTTP 방식으로 진행한다.

```text
Client -> Backend public API
Backend -> RunPod HTTP endpoint
RunPod -> Backend internal claim/heartbeat API
RunPod -> Backend internal callback API
Client -> Backend status/result API
```

## 범위

이 문서는 Backend-AI 내부 연동 계약만 다룬다. 공개 클라이언트 API 계약이 아니다.

- Backend는 사용자 인증, 세션 소유권, 녹음 선택, DB 상태, public API DTO를 소유한다.
- RunPod은 AI 추론을 소유한다.
- Backend-AI request/response DTO는 public API 응답 DTO로 재사용하지 않는다.
- Backend는 원본 media byte를 직접 보내지 않고 S3 object key 또는 짧은 만료 시간을 가진 media 접근 정보를 보낸다.
- Backend는 현재 분석 DB/model에 저장 가능한 검증된 결과 필드만 저장한다.

## 공개 API 흐름

클라이언트가 사용하는 공개 API 흐름은 유지한다.

```text
POST /api/training-sessions/{sessionId}/recordings/upload-url
POST /api/training-sessions/{sessionId}/recordings
PATCH /api/training-sessions/{sessionId}/recordings/{recordingId}/select
POST /api/training-sessions/{sessionId}/analyze
GET /api/training-sessions/{sessionId}/analysis/status
GET /api/analyses/{analysisId}
GET /api/analyses/{analysisId}/segments
```

`POST /api/training-sessions/{sessionId}/analyze`는 Backend의 분석 상태를 생성하거나 갱신한 뒤
RunPod에 분석 작업을 보낸다. 클라이언트는 RunPod을 직접 호출하지 않는다.

## 운영형 작업 수명 원칙

실제 사용자 요청을 안정적으로 처리하기 위해 Backend DB를 최종 작업 원장으로 사용한다.
RunPod의 메모리, 로컬 파일, 임시 큐만으로 작업 상태를 판단하지 않는다.

| 장치 | 의미 | 목적 |
| --- | --- | --- |
| Backend outbox | Backend DB에 RunPod으로 보내야 할 분석 요청을 먼저 저장 | Backend가 요청 전송 전후에 종료되어도 재전송 가능 |
| `requestId` | 사용자의 논리적 분석 요청 ID | 같은 분석 요청의 전달 재시도와 사용자 재요청을 구분 |
| `executionId` | 실제 RunPod 실행 세대 ID | Pod 재시작, 재배정, 늦은 결과를 구분 |
| claim | RunPod이 해당 `executionId` 실행을 점유했다고 Backend에 기록 | 같은 작업의 중복 실행 방지 |
| heartbeat | RunPod이 처리 중임을 주기적으로 Backend에 알림 | Pod 종료나 멈춤 감지 |
| cancel | 사용자가 삭제/취소한 작업을 RunPod 실행에 전달 | 늦은 결과가 다시 저장되는 문제 방지 |
| retry | 일시적인 네트워크/5xx/429 실패 재시도 | 단기 장애로 사용자 작업이 유실되는 문제 방지 |
| stale execution 차단 | 현재 `executionId`와 다른 결과를 거절 | 예전 Pod의 늦은 callback 저장 방지 |

초기 운영 기본값은 실행 슬롯 1개, Backend outbox 기반 재전송, RunPod callback 재전송을 권장한다.
처리량 측정 후 실행 슬롯, heartbeat 주기, timeout, retry 횟수를 조정한다.

## Backend -> RunPod 요청

### Request

```http
POST {RUNPOD_ENDPOINT_URL}/v1/analysis-jobs
Authorization: Bearer {AI_ANALYSIS_API_TOKEN}
Content-Type: application/json
```

### Body

```json
{
  "schemaVersion": "voice-coaching.runpod-analysis-request.v1",
  "requestId": "String",
  "executionId": "String",
  "analysisId": "Long",
  "recordingId": "Long",
  "contentId": "Long",
  "learningFocus": "PRONUNCIATION",
  "promptRevision": "String",
  "scriptText": "String",
  "scriptSha256": "String",
  "audio": {
    "objectKey": "recordings/analysis-audio/{uuid}.wav",
    "mimeType": "audio/wav",
    "sha256": "String",
    "fileSizeBytes": "Long",
    "durationMs": "Integer"
  },
  "video": {
    "objectKey": "recordings/analysis-video/{uuid}.mp4",
    "mimeType": "video/mp4",
    "sha256": "String",
    "fileSizeBytes": "Long",
    "durationMs": "Integer | null"
  },
  "deadlineAt": "2026-09-10T12:00:00Z"
}
```

### 필드 규칙

| Field | Required | Description |
| --- | --- | --- |
| `schemaVersion` | Y | 고정값: `voice-coaching.runpod-analysis-request.v1` |
| `requestId` | Y | Backend가 생성한 논리적 분석 요청 UUID. 같은 전달 재시도에서는 유지 |
| `executionId` | Y | Backend가 생성한 실행 세대 UUID. Pod 장애로 새 실행을 배정하면 교체 |
| `analysisId` | Y | Backend 분석 ID |
| `recordingId` | Y | 선택된 녹음 ID |
| `contentId` | Y | 학습 콘텐츠 ID |
| `learningFocus` | Y | 현재는 `PRONUNCIATION` |
| `promptRevision` | Y | 분석 prompt/콘텐츠 규칙 revision |
| `scriptText` | Y | 선택된 세션/콘텐츠의 기준 script |
| `scriptSha256` | Y | `scriptText` digest. RunPod 접수 전 대본 변조 검증에 사용 |
| `audio` | Y | Backend가 생성한 canonical WAV 입력. `objectKey`, `mimeType`, `sha256`, `fileSizeBytes`, `durationMs` 포함 |
| `video` | N | source media가 video일 때 Backend가 생성한 canonical MP4 입력. audio-only 분석이면 생략하거나 `null`. 현재 Backend 모델에는 video 전용 duration 필드가 없어 `video.durationMs`는 생략될 수 있다. |
| `deadlineAt` | Y | UTC 작업 만료 시각. 오래된 작업을 실행하지 않기 위한 작업 수명 정보 |

RunPod은 `video`가 있다고 해서 별도 사용자 업로드로 해석하면 안 된다.
`video`는 같은 source media에서 Backend가 파생한 canonical 입력이다.
RunPod은 요청 JSON의 임의 callback URL을 신뢰하지 않는다. Backend callback base URL은 RunPod 환경변수로 고정하고,
`analysisId`를 사용해 정해진 callback 경로를 조합한다.

### 접수 응답

RunPod은 인증, schema, media metadata, 처리 여력, Backend claim이 성공한 뒤 `202 Accepted`를 반환한다.
모델 준비 전이면 claim하지 않고 `503 Service Unavailable`을 반환한다.
실행 슬롯이 꽉 찼으면 `429 Too Many Requests`와 `Retry-After`를 반환한다.

```json
{
  "requestId": "String",
  "executionId": "String",
  "status": "ACCEPTED"
}
```

### Backend -> RunPod 제어 API

Backend는 같은 `AI_ANALYSIS_API_TOKEN`으로 RunPod의 분석 접수, 상태 확인, 취소 API를 호출한다.
RunPod API는 public client API가 아니며 Backend 외부 호출을 허용하지 않는다.

| API | 역할 |
| --- | --- |
| `POST {RUNPOD_ENDPOINT_URL}/v1/analysis-jobs` | 분석 작업 접수 |
| `GET {RUNPOD_ENDPOINT_URL}/v1/analysis-jobs/{requestId}` | 현재 Pod가 알고 있는 실행 상태 확인. Backend DB가 최종 상태 |
| `POST {RUNPOD_ENDPOINT_URL}/v1/analysis-jobs/{requestId}/cancel` | 특정 `executionId` 실행 취소 요청 |

## Backend 내부 작업 제어 API

이 API들은 public client API가 아니다. RunPod과 Backend 사이의 서버 간 내부 계약이다.

| API | 역할 |
| --- | --- |
| `POST /api/internal/ai/analyses/{analysisId}/claim` | `requestId`/`executionId`를 현재 분석 작업과 비교하고 단일 RunPod 실행 점유를 기록 |
| `POST /api/internal/ai/analyses/{analysisId}/heartbeat` | 같은 실행의 점유 만료 시각을 갱신하고 취소 여부를 반환 |
| `POST /api/internal/ai/analyses/{analysisId}/result` | terminal 분석 결과 callback 수신 |

claim과 heartbeat 요청은 `Authorization: Bearer {AI_ANALYSIS_CALLBACK_TOKEN}`으로 인증한다.
Backend는 claim과 결과 적용을 DB 조건부 갱신 또는 row lock으로 처리한다.
HTTP 호출 중에는 DB transaction이나 row lock을 길게 유지하지 않는다.

## RunPod -> Backend Callback 요청

### Request

```http
POST /api/internal/ai/analyses/{analysisId}/result
Authorization: Bearer {AI_ANALYSIS_CALLBACK_TOKEN}
Content-Type: application/json
```

### Body

```json
{
  "schemaVersion": "voice-coaching.runpod-analysis-result.v1",
  "eventId": "String",
  "requestId": "String",
  "executionId": "String",
  "analysisId": "Long",
  "recordingId": "Long",
  "status": "COMPLETED",
  "outcome": "COACHING_READY",
  "summaryFeedback": "선택 음소의 발음 피드백",
  "pronunciationEvidence": {
    "schemaVersion": "voice-coaching.pronunciation-evidence.v1",
    "selectedPhone": "String",
    "selectedExpectedIndex": 0,
    "selectedStartMs": 120,
    "selectedEndMs": 260,
    "detectorScore": 0.82,
    "operatingThreshold": 0.7,
    "scoreSemantics": "detector_ranking_score_not_calibrated_correctness_confidence",
    "evidenceState": "frozen_detector_threshold_passed"
  },
  "visualSupplement": null,
  "audioSha256": "String",
  "workerRevision": "String",
  "pipelineRevision": "String",
  "failureCode": null,
  "failureReason": null
}
```

### 필드 규칙

| Field | Required | Description |
| --- | --- | --- |
| `schemaVersion` | Y | 고정값: `voice-coaching.runpod-analysis-result.v1` |
| `eventId` | Y | 동일 callback 전달 재시도에서 고정하는 결과 이벤트 UUID |
| `requestId` | Y | Backend 요청의 `requestId`와 일치해야 한다. 멱등성 검증에 사용한다. |
| `executionId` | Y | 현재 Backend가 유효하다고 보는 실행 세대와 일치해야 한다. |
| `analysisId` | Y | path variable의 `analysisId`와 일치해야 한다. |
| `recordingId` | Y | 해당 분석의 선택된 recording과 일치해야 한다. |
| `status` | Y | `COMPLETED` 또는 `FAILED` |
| `outcome` | N | 완료 시 `COACHING_READY` 또는 `COMPLETED_NO_ISSUE` |
| `summaryFeedback` | Y when `outcome=COACHING_READY` | 사용자에게 보여줄 주요 피드백 |
| `pronunciationEvidence` | Y when `outcome=COACHING_READY` | 선택 음소, 위치, detector score, threshold 등 검증된 발음 근거 |
| `visualSupplement` | N | 같은 source video에서 파생한 시각 보완 근거. 없으면 `null` 또는 생략 |
| transcript/score/segments fields | N | 현재 RunPod HTTP 운영 경로에서는 저장하지 않는다. 제공하려면 DB/model 매핑을 먼저 확정한다. |
| `audioSha256` | Y when completed | 등록된 canonical audio SHA-256과 비교 |
| `workerRevision` | Y when completed | 실제 실행 worker revision |
| `pipelineRevision` | Y when completed | 실제 실행 pipeline/model revision |
| `failureCode` | Y when failed | 실패 시 고정 실패 코드 |
| `failureReason` | Y when failed | 실패 시 사용자 표시 가능한 설명. `COMPLETED`이면 `null` |

## Callback 응답

### 성공

```http
200 OK
```

```json
{
  "result": true,
  "message": "AI 분석 결과를 수신했습니다.",
  "data": {
    "analysisId": 1,
    "status": "COMPLETED"
  }
}
```

### 에러

| Status | Case |
| --- | --- |
| `400 Bad Request` | schema version 오류, 잘못된 body, `analysisId` 불일치, 지원하지 않는 enum 값 |
| `401 Unauthorized` | callback token 누락 또는 불일치 |
| `403 Forbidden` | 서버 간 호출 권한 없음 |
| `404 Not Found` | 분석 결과 대상이 존재하지 않음 |
| `409 Conflict` | 이미 terminal 상태, stale `requestId`, stale `executionId`, 동일 `eventId`의 다른 결과 |
| `429 Too Many Requests` | 일시적인 처리 제한 |
| `422 Unprocessable Entity` | 현재 DB/model 제약에 결과를 매핑할 수 없음 |
| `503 Service Unavailable` | 일시적인 의존성 문제로 callback 결과 저장 실패 |

## 멱등성, Retry, 복구

- Backend는 분석 요청과 RunPod 전달 outbox를 같은 DB transaction에 저장한다.
- Backend dispatcher는 outbox를 읽어 RunPod에 전달하고, 일시 실패 시 같은 `requestId`/`executionId`로 재시도한다.
- RunPod은 callback에 같은 `requestId`, `executionId`, `eventId`를 포함해야 한다.
- Backend는 같은 terminal 결과 callback이 중복으로 와도 DB row를 중복 생성하지 않아야 한다.
- Backend는 현재 실행 세대와 다른 `executionId` 결과를 stale callback으로 보고 저장하지 않는다.
- RunPod이 callback에서 `429`, 일시적 `5xx`, 네트워크 오류를 받으면 같은 `eventId`로 재전송한다.
- `400`, `401`, `403`, `404`, `409`, `422`는 자동 재전송을 종료하고 운영 오류로 기록한다.
- heartbeat가 끊기면 Backend는 점유 만료 후 기존 `executionId`를 무효화하고 새 `executionId`로 재배정할 수 있다.
- 사용자 취소 또는 녹음 삭제가 발생하면 Backend는 먼저 작업을 terminal/canceled 상태로 만들고 RunPod cancel을 전송한다. 늦은 결과는 저장하지 않는다.
- 위 상태 관리를 위해 DB migration이 필요하다. 실행 세대, worker instance, heartbeat/claim 만료 시각,
  전달 재시도 횟수, 마지막 callback `eventId` 또는 결과 digest를 저장할 수 있어야 한다.

## 환경변수

```env
AI_ANALYSIS_TRANSPORT=runpod_http
ANALYSIS_STREAM_ENABLED=false

RUNPOD_ENDPOINT_URL=
AI_ANALYSIS_API_TOKEN=
AI_ANALYSIS_CALLBACK_TOKEN=
AI_ANALYSIS_CALLBACK_BASE_URL=
AI_ANALYSIS_EXECUTION_TIMEOUT=PT15M
AI_ANALYSIS_HEARTBEAT_INTERVAL=PT15S
AI_ANALYSIS_CLAIM_TTL=PT90S
AI_ANALYSIS_DISPATCH_RETRY_MAX_ATTEMPTS=5
AI_ANALYSIS_CALLBACK_RETRY_MAX_ATTEMPTS=5
```

`AI_ANALYSIS_API_TOKEN`과 `AI_ANALYSIS_CALLBACK_TOKEN`은 secret이다.
Git, 로그, API 응답에 노출하지 않는다.

## Redis Stream 상태

Redis Stream은 향후 내부 worker 또는 managed private Redis 전환을 위한 보류 전송안으로 남긴다.
현재 RunPod HTTP callback 경로는 `analysis:request:v1`에 publish하지 않고,
`analysis:result:v1`을 consume하지 않는다.
