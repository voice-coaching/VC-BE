# Backend-AI Redis Stream Contract

이 문서는 백엔드와 AI Worker가 Redis Stream으로 음성 분석 요청과 분석 결과를 주고받는 내부 연동 계약을 정의한다.

HTTP callback API는 사용하지 않는다. AI Worker는 Redis Stream에서 분석 요청을 읽고, 분석 결과를 다시 Redis Stream에 발행한다.

## Overview

```text
Client
-> Backend API: POST /api/training-sessions/{sessionId}/analyze
-> Backend: analysis_results row를 PENDING으로 생성
-> Backend: analysis:request Stream에 분석 요청 메시지 발행
-> AI Worker: analysis:request 메시지 소비
-> AI Worker: 음성 분석 수행
-> AI Worker: analysis:result Stream에 분석 결과 메시지 발행
-> Backend Consumer: analysis:result 메시지 소비
-> Backend: analysis_results, analysis_segments 저장
-> Client
   -> GET /api/training-sessions/{sessionId}/analysis/status
   -> GET /api/analyses/{analysisId}
```

## Redis Connection

Redis 접속 정보는 메시지 본문에 넣지 않는다. 백엔드와 AI Worker는 각자 환경 변수 또는 설정 파일로 Redis에 접속한다.

| Config | Example | Description |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | `password` | Redis password |
| `ANALYSIS_REQUEST_STREAM` | `analysis:request` | 백엔드가 분석 요청을 발행하는 Stream |
| `ANALYSIS_RESULT_STREAM` | `analysis:result` | AI Worker가 분석 결과를 발행하는 Stream |

## Redis Streams

| Stream | Direction | Producer | Consumer | Consumer Group |
| --- | --- | --- | --- | --- |
| `analysis:request` | Backend -> AI Worker | Backend | AI Worker | `analysis-workers` |
| `analysis:result` | AI Worker -> Backend | AI Worker | Backend | `backend-analysis-result-workers` |

## 1. Analysis Request Stream

Stream: `analysis:request`  
Direction: Backend -> AI Worker  
Purpose: 백엔드가 AI Worker에게 선택된 녹음 파일 분석을 요청한다.

### Message Body

```json
{
  "analysisId": "Long",
  "sessionId": "Long",
  "recordingId": "Long",
  "userId": "Long",
  "audioUrl": "String",
  "scriptText": "String",
  "learningFocus": "String"
}
```

### Message Fields

| Field | Type | Required | Source | Description |
| --- | --- | --- | --- | --- |
| `analysisId` | Long | Y | `analysis_results.id` | 백엔드가 생성한 분석 결과 ID |
| `sessionId` | Long | Y | `training_sessions.id` | 학습 세션 ID |
| `recordingId` | Long | Y | `voice_recordings.id` | 선택된 녹음 ID |
| `userId` | Long | Y | `training_sessions.user_id` | 분석 요청 사용자 ID |
| `audioUrl` | String | Y | `voice_recordings.audio_url` | AI Worker가 접근 가능한 녹음 파일 URL 또는 object key |
| `scriptText` | String | Y | `practice_contents.script_text` | 사용자가 읽은 기준 문장 |
| `learningFocus` | String | Y | `training_sessions.learning_focus` | 분석 초점. `PRONUNCIATION`, `INTONATION`, `BOTH` |

### Message Example

```json
{
  "analysisId": 35,
  "sessionId": 12,
  "recordingId": 50,
  "userId": 14,
  "audioUrl": "https://storage.example.com/recordings/50.wav",
  "scriptText": "안녕하세요. 오늘 날씨가 좋습니다.",
  "learningFocus": "PRONUNCIATION"
}
```

## 2. Analysis Result Stream

Stream: `analysis:result`  
Direction: AI Worker -> Backend  
Purpose: AI Worker가 분석 완료 또는 실패 결과를 백엔드에 전달한다.

AI 응답은 현재 DB의 `analysis_results`, `analysis_segments`에 저장 가능한 필드만 사용한다. 새 DB 컬럼을 전제로 하지 않는다.

### Message Body

```json
{
  "analysisId": "Long",
  "status": "String",
  "transcript": "String",
  "sttConfidence": "Number",
  "sttModelName": "String",
  "overallScore": "Number",
  "pronunciationScore": "Number",
  "intonationScore": "Number",
  "speedWpm": "Number",
  "speedStatus": "String",
  "stressScore": "Number",
  "pauseScore": "Number",
  "strengthsText": "String",
  "weaknessesText": "String",
  "summaryFeedback": "String",
  "failureReason": "String",
  "segments": [
    {
      "sequenceNo": "Integer",
      "expectedText": "String",
      "recognizedText": "String",
      "startMs": "Integer",
      "endMs": "Integer",
      "matchType": "String",
      "resultStatus": "String",
      "targetUnit": "String",
      "errorType": "String",
      "pronunciationScore": "Number",
      "intonationScore": "Number",
      "feedback": "String"
    }
  ]
}
```

### Result Fields

| Field | Type | Required | Target | Description |
| --- | --- | --- | --- | --- |
| `analysisId` | Long | Y | `analysis_results.id` | 분석 ID |
| `status` | String | Y | `analysis_results.status` | 분석 상태. `PROCESSING`, `COMPLETED`, `FAILED` |
| `transcript` | String | N | `analysis_results.transcript` | AI가 인식한 전체 음성 텍스트 |
| `sttConfidence` | Number | N | `analysis_results.stt_confidence` | STT 신뢰도 |
| `sttModelName` | String | N | `analysis_results.stt_model_name` | STT 모델명 |
| `overallScore` | Number | N | `analysis_results.overall_score` | 종합 점수 |
| `pronunciationScore` | Number | N | `analysis_results.pronunciation_score` | 발음 점수 |
| `intonationScore` | Number | N | `analysis_results.intonation_score` | 억양 점수 |
| `speedWpm` | Number | N | `analysis_results.speed_wpm` | 분당 발화 속도 |
| `speedStatus` | String | N | `analysis_results.speed_status` | 발화 속도 상태. `TOO_SLOW`, `NORMAL`, `TOO_FAST` |
| `stressScore` | Number | N | `analysis_results.stress_score` | 강세 점수 |
| `pauseScore` | Number | N | `analysis_results.pause_score` | 쉼 점수 |
| `strengthsText` | String | N | `analysis_results.strengths_text` | 전체 강점 요약 |
| `weaknessesText` | String | N | `analysis_results.weaknesses_text` | 전체 약점 요약 |
| `summaryFeedback` | String | N | `analysis_results.summary_feedback` | 종합 피드백 |
| `failureReason` | String | N | `analysis_results.failure_reason` | 실패 사유. `FAILED` 상태에서 사용 |
| `segments` | Array | N | `analysis_segments` | 음절, 단어, 발음 단위별 상세 분석 결과 |

### Segment Fields

| Field | Type | Required | Target | Description |
| --- | --- | --- | --- | --- |
| `sequenceNo` | Integer | Y | `analysis_segments.sequence_no` | 분석 구간 순서 |
| `expectedText` | String | N | `analysis_segments.expected_text` | 기준 텍스트 |
| `recognizedText` | String | N | `analysis_segments.recognized_text` | 인식된 텍스트 |
| `startMs` | Integer | N | `analysis_segments.start_ms` | 구간 시작 시각(ms) |
| `endMs` | Integer | N | `analysis_segments.end_ms` | 구간 종료 시각(ms) |
| `matchType` | String | Y | `analysis_segments.match_type` | 매칭 유형. `MATCH`, `SUBSTITUTION`, `OMISSION`, `ADDITION` |
| `resultStatus` | String | Y | `analysis_segments.result_status` | 구간 결과. `NORMAL`, `CAUTION`, `NEEDS_IMPROVEMENT` |
| `targetUnit` | String | N | `analysis_segments.target_unit` | 발음 집계 대상 단위 |
| `errorType` | String | N | `analysis_segments.error_type` | 오류 유형 |
| `pronunciationScore` | Number | N | `analysis_segments.pronunciation_score` | 구간 발음 점수 |
| `intonationScore` | Number | N | `analysis_segments.intonation_score` | 구간 억양 점수 |
| `feedback` | String | N | `analysis_segments.feedback` | 구간 피드백 |

### Success Message Example

```json
{
  "analysisId": 35,
  "status": "COMPLETED",
  "transcript": "안녕하세요. 오늘 날씨가 좋습니다.",
  "sttConfidence": 0.9342,
  "sttModelName": "whisper-large-v3",
  "overallScore": 82.5,
  "pronunciationScore": 80.0,
  "intonationScore": 85.0,
  "speedWpm": 132.4,
  "speedStatus": "NORMAL",
  "stressScore": 79.5,
  "pauseScore": 76.0,
  "strengthsText": "발화 속도가 안정적입니다.",
  "weaknessesText": "일부 받침 발음이 약하게 들립니다.",
  "summaryFeedback": "전체적으로 안정적인 발화였지만 받침 발음 개선이 필요합니다.",
  "failureReason": null,
  "segments": [
    {
      "sequenceNo": 1,
      "expectedText": "안녕",
      "recognizedText": "안녕",
      "startMs": 0,
      "endMs": 700,
      "matchType": "MATCH",
      "resultStatus": "NORMAL",
      "targetUnit": "FINAL_CONSONANT_NG",
      "errorType": null,
      "pronunciationScore": 91.2,
      "intonationScore": 86.0,
      "feedback": "받침 발음이 명확합니다."
    }
  ]
}
```

### Failure Message Example

```json
{
  "analysisId": 35,
  "status": "FAILED",
  "transcript": null,
  "sttConfidence": null,
  "sttModelName": null,
  "overallScore": null,
  "pronunciationScore": null,
  "intonationScore": null,
  "speedWpm": null,
  "speedStatus": null,
  "stressScore": null,
  "pauseScore": null,
  "strengthsText": null,
  "weaknessesText": null,
  "summaryFeedback": null,
  "failureReason": "음성 인식에 실패했습니다.",
  "segments": []
}
```

## Processing Rule

1. 백엔드는 `POST /api/training-sessions/{sessionId}/analyze` 요청을 받는다.
2. 백엔드는 선택된 녹음과 사용자 소유권을 검증한다.
3. 백엔드는 `analysis_results`에 `PENDING` 상태 row를 생성한다.
4. 백엔드는 `analysis:request` Stream에 메시지를 발행한다.
5. AI Worker는 `XREADGROUP`으로 `analysis:request` 메시지를 읽는다.
6. AI Worker는 분석을 수행하고 `analysis:result` Stream에 결과 메시지를 발행한다.
7. AI Worker는 결과 발행 성공 후 `analysis:request` 메시지를 `XACK` 처리한다.
8. 백엔드 Consumer는 `analysis:result` 메시지를 읽는다.
9. 백엔드는 `analysisId`로 기존 `analysis_results` row를 조회한다.
10. `status`가 `COMPLETED`이면 `analysis_results`를 갱신하고 `segments`를 `analysis_segments`에 저장한다.
11. `status`가 `FAILED`이면 `analysis_results.status`, `failure_reason`을 갱신한다.
12. DB 저장 성공 후 백엔드는 `analysis:result` 메시지를 `XACK` 처리한다.

## Status Transition

| Event | Status |
| --- | --- |
| 백엔드가 분석 요청 row 생성 | `PENDING` |
| AI Worker가 처리 시작 메시지 발행 | `PROCESSING` |
| AI Worker가 성공 결과 발행 | `COMPLETED` |
| AI Worker가 실패 결과 발행 | `FAILED` |

## ACK Policy

| Message | ACK Owner | ACK Timing |
| --- | --- | --- |
| `analysis:request` message | AI Worker | `analysis:result` 메시지 발행 성공 후 |
| `analysis:result` message | Backend Consumer | `analysis_results`, `analysis_segments` DB 저장 성공 후 |

ACK 전에 처리 실패가 발생하면 메시지를 ACK하지 않는다. ACK되지 않은 메시지는 Redis Stream pending entry로 남으며 재처리 대상이 된다.

## Retry Policy

| Item | Policy |
| --- | --- |
| Pending timeout | 5분 이상 ACK되지 않은 메시지를 재처리 대상으로 본다 |
| Max retry count | 3회 |
| Request retry target | AI Worker가 `analysis:request` pending 메시지를 claim 후 재처리 |
| Result retry target | Backend Consumer가 `analysis:result` pending 메시지를 claim 후 재처리 |
| Retry exceeded | 기존 `analysis_results.status`를 `FAILED`로 갱신하고 `failure_reason`에 사유 기록 |

`analysis_results.recording_id`에는 UNIQUE 제약이 있으므로 retry 과정에서 같은 녹음에 대한 분석 row를 새로 생성하지 않는다.

## Message Storage Rule

- Redis Stream에는 음성 blob을 직접 저장하지 않는다.
- Redis Stream에는 `audioUrl` 또는 object key처럼 AI Worker가 음성 파일을 가져올 수 있는 참조값만 저장한다.
- 분석 결과의 영구 저장소는 PostgreSQL이다.
- 요약 분석 결과는 `analysis_results`에 저장한다.
- 구간별 상세 분석 결과는 `analysis_segments`에 저장한다.
- `analysis_segments.target_unit`, `analysis_segments.error_type`은 강점/약점 집계 API에서 사용할 수 있으므로 AI Worker가 가능한 한 채워서 보낸다.

## Related Backend APIs

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/training-sessions/{sessionId}/analyze` | 분석 요청 생성 및 `analysis:request` 메시지 발행 |
| GET | `/api/training-sessions/{sessionId}/analysis/status` | 분석 진행 상태 조회 |
| GET | `/api/analyses/{analysisId}` | 분석 결과 조회 |

## Related DB Tables

| Table | Purpose |
| --- | --- |
| `training_sessions` | 학습 세션과 분석 진행 상태 연결 |
| `voice_recordings` | 선택된 녹음 파일 메타데이터와 `audioUrl` 저장 |
| `analysis_results` | 분석 상태, STT 결과, 점수, 전체 피드백 저장 |
| `analysis_segments` | 음절, 단어, 발음 단위별 상세 분석 결과 저장 |
| `practice_contents` | 기준 문장 `scriptText` 조회 |
| `users` | 분석 요청 사용자 조회 |
