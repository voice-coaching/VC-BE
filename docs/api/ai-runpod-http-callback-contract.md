# Backend-AI RunPod HTTP Callback 계약

## 상태

- 작성일: 2026-09-10
- 프로젝트: voice
- 전송 방식: Backend -> RunPod HTTP 요청, RunPod -> Backend HTTP callback
- Redis 사용: 기존 애플리케이션 cache 용도로 유지한다. 이번 단계의 Backend-AI 분석 전송에는 Redis Stream MQ를 사용하지 않는다.
- 공개 API 호환성: 클라이언트가 호출하는 기존 분석 API는 유지한다.

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

## Backend -> RunPod 요청

### Request

```http
POST {RUNPOD_ENDPOINT_URL}
Authorization: Bearer {RUNPOD_API_KEY}
Content-Type: application/json
```

### Body

```json
{
  "schemaVersion": "voice-coaching.runpod-analysis-request.v1",
  "requestId": "String",
  "analysisId": "Long",
  "recordingId": "Long",
  "contentId": "Long",
  "learningFocus": "PRONUNCIATION",
  "scriptText": "String",
  "audio": {
    "objectKey": "recordings/analysis-audio/{uuid}.wav",
    "mimeType": "audio/wav",
    "sha256": "String",
    "durationMs": "Integer"
  },
  "video": {
    "objectKey": "recordings/analysis-video/{uuid}.mp4",
    "mimeType": "video/mp4",
    "sha256": "String"
  },
  "callback": {
    "url": "https://{backend-host}/api/internal/ai/analyses/{analysisId}/result",
    "authorizationScheme": "Bearer"
  }
}
```

### 필드 규칙

| Field | Required | Description |
| --- | --- | --- |
| `schemaVersion` | Y | 고정값: `voice-coaching.runpod-analysis-request.v1` |
| `requestId` | Y | Backend가 생성한 요청 멱등성 key |
| `analysisId` | Y | Backend 분석 ID |
| `recordingId` | Y | 선택된 녹음 ID |
| `contentId` | Y | 학습 콘텐츠 ID |
| `learningFocus` | Y | 현재는 `PRONUNCIATION` |
| `scriptText` | Y | 선택된 세션/콘텐츠의 기준 script |
| `audio` | Y | Backend가 생성한 canonical WAV 입력 |
| `video` | N | source media가 video일 때 Backend가 생성한 canonical MP4 입력. audio-only 분석이면 생략하거나 `null` |
| `callback.url` | Y | 결과를 받을 Backend callback endpoint |
| `callback.authorizationScheme` | Y | 현재는 `Bearer` |

RunPod은 `video`가 있다고 해서 별도 사용자 업로드로 해석하면 안 된다.
`video`는 같은 source media에서 Backend가 파생한 canonical 입력이다.

## RunPod -> Backend Callback 요청

### Request

```http
POST /api/internal/ai/analyses/{analysisId}/result
Authorization: Bearer {AI_CALLBACK_TOKEN}
Content-Type: application/json
```

### Body

```json
{
  "schemaVersion": "voice-coaching.runpod-analysis-result.v1",
  "requestId": "String",
  "analysisId": "Long",
  "recordingId": "Long",
  "status": "COMPLETED",
  "transcript": "String",
  "overallScore": 82.5,
  "pronunciationScore": 80.0,
  "intonationScore": 85.0,
  "speedWpm": 120.0,
  "speedStatus": "NORMAL",
  "stressScore": 78.0,
  "pauseScore": 81.0,
  "strengthsText": "String",
  "weaknessesText": "String",
  "summaryFeedback": "String",
  "segments": [
    {
      "sequence": 1,
      "targetUnit": "String",
      "label": "String",
      "score": 80.0,
      "errorType": "String",
      "feedback": "String"
    }
  ],
  "failureReason": null
}
```

### 필드 규칙

| Field | Required | Description |
| --- | --- | --- |
| `schemaVersion` | Y | 고정값: `voice-coaching.runpod-analysis-result.v1` |
| `requestId` | Y | Backend 요청의 `requestId`와 일치해야 한다. 멱등성 검증에 사용한다. |
| `analysisId` | Y | path variable의 `analysisId`와 일치해야 한다. |
| `recordingId` | Y | 해당 분석의 선택된 recording과 일치해야 한다. |
| `status` | Y | `COMPLETED` 또는 `FAILED` |
| `transcript` | N | AI가 제공하는 경우 저장할 음성 인식 텍스트 |
| score fields | N | AI metric 숫자 값. Backend는 현재 DB/model에서 지원하는 필드만 저장한다. |
| `speedStatus` | N | 저장 전에 Backend가 지원하는 enum 값이어야 한다. |
| `strengthsText` | N | 강점 요약 |
| `weaknessesText` | N | 약점 요약 |
| `summaryFeedback` | N | 사용자에게 보여줄 주요 피드백 |
| `segments` | N | 선택적인 세그먼트 단위 피드백. 없으면 생략하거나 빈 배열 |
| `failureReason` | N | `status=FAILED`이면 필요하고, `COMPLETED`이면 `null` |

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
| `404 Not Found` | 분석 결과 대상이 존재하지 않음 |
| `409 Conflict` | 이미 terminal 상태이거나 stale `requestId` |
| `422 Unprocessable Entity` | 현재 DB/model 제약에 결과를 매핑할 수 없음 |
| `503 Service Unavailable` | 일시적인 의존성 문제로 callback 결과 저장 실패 |

## 멱등성과 Retry

- Backend는 RunPod 요청에 `requestId`를 포함한다.
- RunPod은 callback에 같은 `requestId`를 포함해야 한다.
- Backend는 같은 terminal 결과 callback이 중복으로 와도 DB row를 중복 생성하지 않아야 한다.
- RunPod이 2xx가 아닌 응답을 받으면 같은 `requestId`로 재시도할 수 있다.
- HTTP 전송 단계의 retry timing은 RunPod이 소유한다.

## 환경변수

```env
ANALYSIS_STREAM_ENABLED=false

RUNPOD_ENDPOINT_URL=
RUNPOD_API_KEY=
AI_CALLBACK_TOKEN=
PUBLIC_BACKEND_BASE_URL=
```

`RUNPOD_API_KEY`와 `AI_CALLBACK_TOKEN`은 secret이다.
Git, 로그, API 응답에 노출하지 않는다.

## Redis Stream 상태

Redis Stream은 향후 내부 worker 또는 managed private Redis 전환을 위한 보류 전송안으로 남긴다.
현재 RunPod HTTP callback 경로는 `analysis:request:v1`에 publish하지 않고,
`analysis:result:v1`을 consume하지 않는다.
