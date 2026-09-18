# 백엔드 AI API 명세서

작성일: 2026-09-16 · 코드 기준: `VC-BE` 커밋 `bb7ae92`

이 문서는 현재 백엔드에 구현된 사용자용 AI API 8개와 워커용 내부 API 4개를 설명한다. 운영 배포 완료나 실제 추론 성공을 보장하는 문서는 아니다. URL 기준은 `https://api.voice-coaching.site`이며, 아래 경로를 이어 붙인다.

## 1. 공통 규칙

### 사용자 API

- 인증: `Authorization: Bearer <사용자 Access JWT>`.
- JSON 본문이 있는 요청: `Content-Type: application/json`.
- 세션·분석은 로그인한 사용자의 소유권 검사를 받는다.
- 아래 8개 API의 정상 HTTP 상태는 모두 **200**이다. 분석 요청의 200은 비동기 작업 접수이며 분석 완료가 아니다.
- ID는 JSON 정수, 날짜는 ISO 8601 오프셋 날짜·시간이다.
- 아래 사용자 API의 응답 표와 예시는 별도 표시가 없으면 공통 응답의 **`data`**에 해당한다.

성공 응답:

```json
{
  "result": true,
  "message": "음성 분석을 요청했습니다.",
  "data": { "analysisId": 123, "status": "PENDING", "requestedAt": "2026-09-16T15:00:00+09:00" }
}
```

일반 오류 응답:

```json
{ "result": false, "message": "이미 분석이 진행 중입니다.", "data": null }
```

오류 표에 기재한 `ANALYSIS_*` 등의 이름은 코드 내부 식별자다. 사용자 API 응답에 별도 `errorCode` 필드로 제공되지는 않는다.

### API 목록

| 구분 | 메서드 | 경로 | 기능 |
| --- | --- | --- | --- |
| 사용자 | GET | `/api/analysis-capabilities` | 지원 형식·정책·설정 상태 |
| 사용자 | POST | `/api/training-sessions/{sessionId}/analyze` | 분석 요청 |
| 사용자 | GET | `/api/training-sessions/{sessionId}/analysis/status` | 분석 진행 상태 |
| 사용자 | POST | `/api/training-sessions/{sessionId}/analysis/retry` | 실패한 분석 재시도 |
| 사용자 | GET | `/api/training-sessions/{sessionId}/analysis` | 세션 분석 요약 |
| 사용자 | GET | `/api/analyses/{analysisId}` | 완료된 분석 상세 |
| 사용자 | GET | `/api/analyses/{analysisId}/segments` | 구간별 분석 조회 |
| 사용자 | POST | `/api/analyses/{analysisId}/feedback/regenerate` | 피드백 재구성 |
| 워커 | GET | `/api/internal/ai/worker-readiness` | 백엔드 콜백 준비 상태 |
| 워커 | POST | `/api/internal/ai/analyses/{analysisId}/claim` | 실행 소유권 획득 |
| 워커 | POST | `/api/internal/ai/analyses/{analysisId}/heartbeat` | 실행 임대 갱신 |
| 워커 | POST | `/api/internal/ai/analyses/{analysisId}/result` | 결과 전달 |

## 2. 지원 조건 조회

`GET /api/analysis-capabilities`

요청 본문과 쿼리 파라미터 없음.

| 응답 필드 | 타입 | 의미 |
| --- | --- | --- |
| recordingUpload | string | `CONFIGURED` / `NOT_CONFIGURED` |
| analysisRequests | string | `CONFIGURED` / `NOT_CONFIGURED` |
| supportedLearningFocuses | string[] | 현재 `["PRONUNCIATION"]` |
| acceptedAudioMimeTypes | string[] | `audio/webm`, `audio/mpeg`, `audio/wav` |
| acceptedVideoMimeTypes | string[] | `video/mp4`, `video/quicktime`, `video/webm` |
| maximumAudioUploadBytes | integer | 오디오 최대 바이트. 정책 상한 20 MiB와 설정값 중 작은 값 |
| maximumVideoUploadBytes | integer | 영상 최대 바이트. 정책 상한 100 MiB와 설정값 중 작은 값 |
| minimumDurationMs / maximumDurationMs | integer | 설정에 따른 최소·최대 길이 |
| videoRequiresAudioTrack | boolean | 현재 `true` |
| voiceProcessingConsentRequired | boolean | 현재 `true` |
| videoProcessingConsentRequired | boolean | 현재 `true` |
| consentPolicyRevision | string 또는 null | 음성 처리 동의 정책 버전 |
| videoProcessingConsentPolicyRevision | string | 현재 `voice-video-processing-consent-v1` |

**설정 유무를 알려주는 API다.** 팟의 가동 상태, 모델 준비, 저장소 접근, 실제 분석 성공 여부는 검사하지 않는다. 클라이언트는 업로드 제한과 동의 버전을 응답에서 읽어 사용한다.

## 3. 분석 요청

`POST /api/training-sessions/{sessionId}/analyze`

경로: `sessionId` — 사용자 소유 학습 세션 ID.

```json
{ "accepted": true, "policyRevision": "<capabilities에서 받은 consentPolicyRevision>" }
```

| 요청 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| accepted | boolean | O | 반드시 `true` |
| policyRevision | string | O | 공백 불가, 최대 100자, 현재 동의 정책 사용 |

녹음 파일이나 S3 키, 대본을 이 API에 직접 보내지 않는다. 서버가 세션의 **최종 선택된 녹음**과 대본을 조회하여 작업을 생성한다. 녹음은 음질 검사 `PASS`, 학습 유형은 `PRONUNCIATION`이어야 한다. 동일 녹음의 진행 중 작업 또는 기존 분석 이력이 있으면 새 요청을 거절한다.

```json
{ "analysisId": 123, "status": "PENDING", "requestedAt": "2026-09-16T15:00:00+09:00" }
```

주요 오류: 400 동의 누락, 404 세션 없음, 409 선택 녹음 없음·중복 요청·정책 불일치·세션 상태 충돌, 422 음질/원본 정보/학습 유형 부적합, 429 동시 분석 제한, 503 연동 사용 불가.

## 4. 진행 상태 조회

`GET /api/training-sessions/{sessionId}/analysis/status`

현재 선택된 녹음의 최신 분석 상태를 조회한다. 요청 본문 없음.

```json
{
  "analysisId": 123,
  "status": "PROCESSING",
  "stage": "PRONUNCIATION_ANALYSIS",
  "progressPercent": 70,
  "failureReason": null,
  "updatedAt": "2026-09-16T15:00:05+09:00"
}
```

| status | stage | progressPercent |
| --- | --- | --- |
| PENDING | WAITING | 0 |
| PROCESSING | PRONUNCIATION_ANALYSIS | 70 |
| COMPLETED | COMPLETED | 100 |
| FAILED | FAILED | 0 |

`failureReason`은 nullable 문자열이다. **stage와 progressPercent는 상태에서 계산하는 고정값이며 실제 모델 진행률이 아니다.** 세션이나 분석이 없으면 404를 반환한다.

## 5. 실패한 분석 재시도

`POST /api/training-sessions/{sessionId}/analysis/retry`

요청 본문은 분석 요청과 동일한 `accepted`, `policyRevision`이다. 선택 녹음의 실패한 분석을 새 요청 ID로 다시 접수한다. 기존 analysisId를 유지하며 재시도 횟수를 증가시킨다. 최대 재시도는 3회다.

```json
{ "analysisId": 123, "status": "PENDING", "retryCount": 1, "requestedAt": "2026-09-16T15:05:00+09:00" }
```

분석 요청의 동의·사용 가능 여부 검사 외에, 409 `ANALYSIS_NOT_FAILED`, `MAX_RETRY_EXCEEDED` 또는 세션 상태 충돌이 발생할 수 있다. 발음을 다시 녹음하고 이전 시도와 비교하는 학습 기능이 아니라 **실패 작업의 재실행**이다.

## 6. 세션 분석 요약 조회

`GET /api/training-sessions/{sessionId}/analysis`

요청 본문 없음. 세션에 연결된 최신 분석 요약을 반환하며, 완료 상태만으로 제한하지 않는다.

```json
{
  "sessionId": 10,
  "analysisId": 123,
  "status": "COMPLETED",
  "outcome": "COACHING_READY",
  "overallScore": null,
  "pronunciationScore": null,
  "intonationScore": null
}
```

세 점수는 nullable number다. 세션 분석이 없으면 404 `SESSION_ANALYSIS_NOT_FOUND`다. 전체 피드백과 근거는 다음 상세 API에서 조회한다.

## 7. 완료된 분석 상세 조회

`GET /api/analyses/{analysisId}`

본인 소유이며 `COMPLETED`인 분석만 조회 가능하다. 없으면 404, 미완료면 409다.

| 응답 필드 | 타입 | 의미 |
| --- | --- | --- |
| id | integer | 분석 ID |
| status | string | `COMPLETED` |
| outcome | string 또는 null | 아래 결과 분류 참고 |
| transcript | string 또는 null | 기존 STT 필드 |
| sttConfidence | number 또는 null | 기존 STT 신뢰도 필드 |
| overallScore / pronunciationScore / intonationScore | number 또는 null | 기존 점수 필드 |
| speedWpm | number 또는 null | 기존 분당 발화 속도 필드 |
| speedStatus | string 또는 null | `TOO_SLOW`, `NORMAL`, `TOO_FAST` |
| stressScore / pauseScore | number 또는 null | 기존 강세·쉼 점수 필드 |
| strengths / weaknesses | string[] | 저장된 장점·개선점. 없으면 빈 배열 |
| summaryFeedback | string 또는 null | 승인된 발음 코칭 문구 |
| pronunciationEvidence | object 또는 null | 선택된 교정 음소의 근거 |
| visualSupplement | object 또는 null | 해당 음소를 보조하는 영상 근거 |
| analyzedAt | ISO 8601 string 또는 null | 분석 처리 시각 |

현재 RunPod HTTP 결과는 STT·사용자 점수·속도·강세·쉼 점수를 제공하지 않는다. 이 필드들의 존재를 해당 기능의 제공으로 해석하거나, null을 0점으로 표시하면 안 된다.

### outcome

- 현재 HTTP 완료 결과: `COACHING_READY`(교정 근거 있음), `COMPLETED_NO_ISSUE`(선택할 교정 근거 없음).
- 백엔드 enum에는 `RERECORD_REQUIRED`, `UNCERTAIN`, `FAILED_CLOSED`도 있지만 현재 HTTP 결과 스키마에서 받는 완료 outcome은 위 두 가지다.
- `COMPLETED_NO_ISSUE`는 summaryFeedback·발음 근거·영상 근거가 null인 정상 완료다. 만점이나 모든 발음이 정확하다는 보증은 아니다.

### pronunciationEvidence

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| schemaVersion | string | `voice-coaching.pronunciation-evidence.v1` |
| selectedPhone | string | 선택된 음소 |
| selectedExpectedIndex | integer | 기대 음소열의 0부터 시작하는 인덱스 |
| selectedStartMs / selectedEndMs | integer 또는 null | 음소 구간. 없으면 둘 다 null |
| detectorScore | number | 0~1 검출기 순위 점수 |
| operatingThreshold | number | 0~1 운영 임계값 |
| scoreSemantics | string | `detector_ranking_score_not_calibrated_correctness_confidence` |
| evidenceState | string | `frozen_detector_threshold_passed` |

detectorScore는 사용자의 발음 점수나 정확도 확률이 아니다. 구간이 있으면 `0 <= start < end <= 오디오 길이`, 임계값은 detectorScore 이하이어야 한다.

### visualSupplement

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| schemaVersion | string | `voice-coaching.visual-supplement.v1` |
| selectedExpectedIndex | integer | 발음 근거와 같은 선택 인덱스 |
| evidenceRelation | string | `supports_upstream` |
| approvedClaimId / rendererKey | string | 승인된 영상 주장·렌더러 식별자 |
| upstreamPhoneAnchorRef | string | 음소 근거 앵커 SHA-256 |
| supplementSha256 | string | 승인된 내부 영상 보조 근거 SHA-256 |
| closedBetaLipObservation | object 또는 null | 공개 DTO에는 있으나 현재 HTTP 계약으로는 수신하지 않음 |

영상 근거는 nullable이다. 현재 응답만으로 영상 미제공·분석 제외·분석 실패 원인을 구별할 수 없다. Clova 실제 생성 여부와 대체 문구 사용 여부도 별도 필드로 제공하지 않는다.

## 8. 구간별 결과 조회

`GET /api/analyses/{analysisId}/segments?page=0&size=100`

| 쿼리 | 타입 | 기본값 | 범위 |
| --- | --- | --- | --- |
| page | integer | 0 | 0 이상 |
| size | integer | 100 | 1~200 |

응답: `{ "items": [], "page": 0, "size": 100, "totalElements": 0 }`.

items 요소의 필드:

| 필드 | 타입 |
| --- | --- |
| id | integer |
| sequenceNo | integer |
| expectedText / recognizedText | string 또는 null |
| startMs / endMs | integer 또는 null |
| matchType | `MATCH` / `SUBSTITUTION` / `OMISSION` / `ADDITION` |
| resultStatus | `NORMAL` / `CAUTION` / `NEEDS_IMPROVEMENT` |
| targetUnit / errorType | string 또는 null |
| pronunciationScore / intonationScore | number 또는 null |
| feedback | string 또는 null |

본인 소유의 완료된 분석만 조회한다. 잘못된 페이지 조건은 400, 분석 없음은 404, 미완료는 409다. **현재 RunPod HTTP 결과는 segments를 전달하지 않으므로 새 HTTP 분석에서 구간 목록이 채워진다고 보장할 수 없다.**

## 9. 피드백 재구성

`POST /api/analyses/{analysisId}/feedback/regenerate`

```json
{ "feedbackStyle": "COACHING" }
```

`feedbackStyle`은 필수이며 현재 지원 enum은 `COACHING` 하나다.

```json
{
  "analysisId": 123,
  "strengths": [],
  "weaknesses": [],
  "summaryFeedback": "저장된 승인 피드백 문구",
  "regeneratedAt": "2026-09-16T15:10:00+09:00"
}
```

조건: 완료된 본인 분석, `COACHING_READY`, 승인 문구·선택 음소·인덱스·근거 상태·파이프라인 버전 존재. 최대 3회다.

주요 오류: 400 잘못된 스타일/본문, 404 분석 없음, 409 미완료 또는 `FEEDBACK_EVIDENCE_UNAVAILABLE`, 429 `FEEDBACK_REGENERATION_LIMIT`.

**현재 기본 provider는 AI 모델을 다시 호출하지 않고 기존 승인 summaryFeedback을 반환한다.** 성공 시에도 횟수와 재구성 시각은 갱신되므로, 다른 문구를 생성하는 기능으로 안내하면 안 된다.

## 10. 워커용 내부 API 공통

- 인증: `Authorization: Bearer <AI_ANALYSIS_CALLBACK_TOKEN>` 헤더 정확히 하나.
- 사용자 JWT나 RunPod 계정 API 키를 사용하지 않는다.
- 응답은 공통 `result/message/data` 포장 없이 평평한 JSON이다.
- POST는 `application/json`, Content-Encoding은 생략 또는 `identity`.
- claim/heartbeat 본문 최대 65,536 bytes, result 최대 1,048,576 bytes.
- 알 수 없는 필드, 중복 JSON 키, 잘못된 UTF-8, 필수 필드 누락을 거절한다.
- nullable 필드도 스키마에서 필수로 선언되었으면 **명시적으로 null**을 보낸다.
- UUID는 소문자 정규 문자열, ID는 1~9,007,199,254,740,991, SHA-256은 소문자 16진수 64자리.
- 내부 시각은 UTC 밀리초 3자리 형식: `2026-09-16T06:00:00.000Z`.
- 정확한 검증 규칙의 원본: [제어 JSON Schema](../contracts/runpod_http_control_v1.schema.json), [결과 JSON Schema](../contracts/runpod_result_v1.schema.json).

### 10.1 백엔드 준비 상태

`GET /api/internal/ai/worker-readiness`

본문 없음. 준비 시 200, 준비되지 않았으면 503:

```json
{ "status": "ready", "contractVersion": "voice-coaching.runpod-http.v1.1", "serverTime": "2026-09-16T06:00:00.000Z" }
```

503일 때 status는 `not_ready`다. 백엔드 설정과 필요한 DB 접근을 확인한다. 팟의 모델·저장소 상태까지 확인하는 API는 아니다.

### 10.2 실행 소유권 획득

`POST /api/internal/ai/analyses/{analysisId}/claim`

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "workerInstanceId": "33333333-3333-4333-8333-333333333333",
  "requestPayloadSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

네 필드 모두 필수. hash는 백엔드가 보낸 불변 분석 요청 전체에 **RFC 8785 JCS → UTF-8 → SHA-256**을 적용한 값이어야 한다. 예시의 hash는 형식 설명용이다.

200 응답:

```json
{
  "analysisId": 123,
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "workerInstanceId": "33333333-3333-4333-8333-333333333333",
  "serverTime": "2026-09-16T06:00:00.000Z",
  "leaseExpiresAt": "2026-09-16T06:01:30.000Z",
  "continueProcessing": true
}
```

현재 실행·요청 hash·녹음·세션·마감시각을 검사한다. 첫 claim이 owner를 정하며 다른 워커는 탈취할 수 없다. 같은 워커의 유효한 claim 재전달은 허용한다. lease는 서버 시각과 설정 TTL로 계산하고 작업 deadline을 넘기지 않는다. 기본 TTL은 90초다. 현재 백엔드 설정에는 90초 상한 검증이 없으므로 팟의 최대 90초 규칙과 맞춰 운영해야 한다.

### 10.3 실행 임대 갱신

`POST /api/internal/ai/analyses/{analysisId}/heartbeat`

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "workerInstanceId": "33333333-3333-4333-8333-333333333333"
}
```

세 필드 모두 필수. 응답은 claim과 같은 200 lease 응답이다. claim owner만 갱신할 수 있다. 최초 claim을 대신하거나 이미 만료된 lease를 되살릴 수 없다. 워커가 보내는 `claimedUntil`, `heartbeatAt`, `schemaVersion` 필드는 이 요청에 허용하지 않는다.

### 10.4 분석 결과 전달

`POST /api/internal/ai/analyses/{analysisId}/result`

다음 **17개 필드는 모두 존재해야 한다.**

| 필드 | 타입/제약 |
| --- | --- |
| schemaVersion | `voice-coaching.runpod-analysis-result.v1` |
| eventId / requestId / executionId / workerInstanceId | UUID string |
| analysisId / recordingId | 양의 정수. 경로·현재 작업과 일치 |
| status | `COMPLETED` / `FAILED` |
| outcome | `COACHING_READY` / `COMPLETED_NO_ISSUE` / null |
| summaryFeedback | string(1~20,000자, 공백만 불가) 또는 null |
| pronunciationEvidence | 7절의 발음 근거 object 또는 null |
| visualSupplement | 7절의 영상 근거 object에서 closedBetaLipObservation을 제외한 형태 또는 null |
| audioSha256 | SHA-256 또는 null |
| workerRevision | string(1~100자, 공백만 불가) |
| pipelineRevision | string(1~100자, 공백만 불가) 또는 null |
| failureCode | 아래 enum 또는 null |
| failureReason | string(1~500자, 공백만 불가) 또는 null |

상태별 규칙:

| 구분 | 필수 값 | 반드시 null |
| --- | --- | --- |
| COMPLETED + COACHING_READY | summaryFeedback, pronunciationEvidence, audioSha256, pipelineRevision | failureCode, failureReason |
| COMPLETED + COMPLETED_NO_ISSUE | audioSha256, pipelineRevision | summaryFeedback, pronunciationEvidence, visualSupplement, failureCode, failureReason |
| FAILED | failureCode, failureReason | outcome, summaryFeedback, pronunciationEvidence, visualSupplement, audioSha256 |

COACHING_READY의 visualSupplement는 선택적으로 null이며, FAILED의 pipelineRevision도 null 가능하다. workerRevision은 모든 경우 필수다.

failureCode enum: `audio_metadata_mismatch`, `audio_integrity_mismatch`, `restricted_audio_unavailable`, `audio_decode_unsupported`, `analysis_focus_not_supported`, `seungun_backend_unavailable`, `seungun_detector_failed`, `analysis_execution_failed_closed`.

정상 완료 예시(교정 대상 없음):

```json
{
  "schemaVersion": "voice-coaching.runpod-analysis-result.v1",
  "eventId": "44444444-4444-4444-8444-444444444444",
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "workerInstanceId": "33333333-3333-4333-8333-333333333333",
  "analysisId": 123,
  "recordingId": 456,
  "status": "COMPLETED",
  "outcome": "COMPLETED_NO_ISSUE",
  "summaryFeedback": null,
  "pronunciationEvidence": null,
  "visualSupplement": null,
  "audioSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "workerRevision": "example-worker-revision",
  "pipelineRevision": "example-pipeline-revision",
  "failureCode": null,
  "failureReason": null
}
```

ID・revision・hash는 설명용이며 실제 요청과 일치하는 값으로 바꿔야 한다. STT·점수·segments·debug·closedBetaLipObservation을 추가하면 거절된다.

200 ACK:

```json
{
  "eventId": "44444444-4444-4444-8444-444444444444",
  "analysisId": 123,
  "requestId": "11111111-1111-4111-8111-111111111111",
  "executionId": "22222222-2222-4222-8222-222222222222",
  "status": "APPLIED",
  "serverTime": "2026-09-16T06:00:30.000Z"
}
```

DB 반영 후 `APPLIED`를 응답한다. 같은 eventId와 같은 canonical content의 재전달은 `DUPLICATE`다. 기존 승인 결과의 동일 재전달은 lease 만료 후에도 ACK 가능하지만, 새로운 결과는 유효한 owner·lease·deadline을 만족해야 한다. 재전달 시 eventId와 내용을 변경하지 않는다.

### 내부 오류

```json
{ "reasonCode": "LEASE_EXPIRED", "message": "LEASE_EXPIRED", "requestId": null, "executionId": null, "retryable": false }
```

| HTTP | 주요 reasonCode |
| --- | --- |
| 400 | INVALID_JSON |
| 401 | UNAUTHENTICATED |
| 404 | TARGET_NOT_FOUND |
| 409 | STALE_EXECUTION, PAYLOAD_DIGEST_MISMATCH, WORKER_CONFLICT, CLAIM_REQUIRED, LEASE_EXPIRED, DEADLINE_EXCEEDED, ANALYSIS_CANCELLED, ANALYSIS_TERMINAL, RESULT_EVENT_CONFLICT, RESULT_ALREADY_FINALIZED |
| 413 | PAYLOAD_TOO_LARGE |
| 415 | UNSUPPORTED_MEDIA_TYPE |
| 422 | VALIDATION_FAILED |
| 500 | INTERNAL_ERROR |
| 503 | DEPENDENCY_UNAVAILABLE |

내부 예외 처리기는 429/502/503/504에 retryable=true와 `Retry-After: 1`을 제공한다. 오류 응답은 `Cache-Control: no-store`, 401은 `WWW-Authenticate: Bearer`를 제공한다. readiness의 503은 앞서 설명한 readiness 본문을 사용한다.

## 11. 연관 API와 호출 순서

다음은 AI 추론 API 자체는 아니지만 분석 입력 준비·취소에 사용되는 백엔드 API다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| POST | `/api/training-sessions` | 학습 세션 생성 |
| POST | `/api/training-sessions/{sessionId}/recordings/upload-url` | 녹음 업로드 URL 발급 |
| POST | `/api/training-sessions/{sessionId}/recordings` | 업로드 녹음 등록·정규화 |
| GET | `/api/training-sessions/{sessionId}/recordings` | 녹음 목록 |
| PATCH | `/api/training-sessions/{sessionId}/recordings/{recordingId}/select` | 분석할 녹음 선택 |
| POST | `/api/training-sessions/{sessionId}/cancel` | 세션 취소 및 연관 분석 취소 처리 |

일반 호출 순서: 지원 조건 조회 → 세션 생성 → 업로드 URL 발급 → 파일 업로드 → 녹음 등록 → 녹음 선택 → 동의와 함께 analyze → status 조회 → 완료 후 analyses/{analysisId} 조회. 실패한 분석은 retry API를 사용한다.

백엔드가 외부 팟으로 보내는 `/v1/analysis-jobs` 및 취소 호출은 **백엔드가 제공하는 엔드포인트가 아니다.** 그 계약은 [Backend–RunPod HTTP 계약](ai-runpod-http-callback-contract.md)을 참고한다.

## 12. 구현 근거

- [TrainingAnalysisController](../../src/main/java/org/example/voice/training/controller/TrainingAnalysisController.java)
- [AnalysisController](../../src/main/java/org/example/voice/analysis/controller/AnalysisController.java)
- [SessionAnalysisController](../../src/main/java/org/example/voice/analysis/controller/SessionAnalysisController.java)
- [AnalysisCapabilitiesController](../../src/main/java/org/example/voice/analysis/controller/AnalysisCapabilitiesController.java)
- [InternalRunPodAnalysisController](../../src/main/java/org/example/voice/analysis/controller/InternalRunPodAnalysisController.java)
- [공개·내부 응답 DTO](../../src/main/java/org/example/voice/analysis/controller/dto)
- [TrainingAnalysisRequestService](../../src/main/java/org/example/voice/training/application/TrainingAnalysisRequestService.java)
- [FeedbackRegenerationService](../../src/main/java/org/example/voice/analysis/application/FeedbackRegenerationService.java)
- [ErrorCode](../../src/main/java/org/example/voice/common/exception/ErrorCode.java)

이 문서는 코드와 JSON Schema를 대조하여 작성했다. 문서 작성 과정에서 운영 API 호출이나 실제 음성 추론은 수행하지 않았다.
