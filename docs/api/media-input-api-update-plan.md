# Media Input API Update Plan

- 작성일: 2026-09-09
- 기준 브랜치: `AI-API`
- 목적: 음성 기본 입력과 선택적 영상 입력을 AI 분석 흐름에 맞게 정리한다.

## 결정 사항

Public API는 음성과 영상을 별도 필드로 동시에 받지 않는다. 하나의 media input을 받고,
그 media의 MIME에 따라 Backend가 AI 입력을 만든다.

```text
audio media
  -> canonical WAV 생성
  -> AI request: audio fields only

video media
  -> video에서 canonical WAV 추출
  -> canonical MP4 생성
  -> AI request: audio fields + visualInput
```

Backend-AI RunPod HTTP request에서는 `audioObjectKey`가 항상 필수다. `visualInput`은
업로드 media가 영상이고 영상 분석 입력이 만들어졌을 때만 포함한다.

## 수정 대상 API 명세

### 1. `POST /api/training-sessions/{sessionId}/recordings/upload-url`

수정 위치:

- `docs/api/specification.md`
- `docs/api/endpoints.md`

현재 명세 방향은 유지한다.

```json
{
  "fileName": "source.mp4",
  "mimeType": "video/mp4",
  "fileSizeBytes": 999999
}
```

수정해야 할 설명:

- 하나의 업로드 media URL을 발급한다.
- `mimeType`이 audio 계열이면 음성-only 분석 후보가 된다.
- `mimeType`이 video 계열이면 Backend가 등록 단계에서 음성과 영상을 파생할 수 있다.
- 영상 파일을 받더라도 AI speech analysis 기준 입력은 영상에서 추출한 canonical WAV다.
- 허용 MIME과 크기 제한을 명시한다.

허용 MIME:

```text
audio/webm
audio/mpeg
audio/wav
video/mp4
video/quicktime
video/webm
```

### 2. `POST /api/training-sessions/{sessionId}/recordings`

수정 위치:

- `docs/api/specification.md`
- `docs/api/endpoints.md`

현재 단일 media 등록 request 구조를 유지한다.

```json
{
  "objectKey": "recordings/users/{userId}/sessions/{sessionId}/source.mp4",
  "mimeType": "video/mp4",
  "fileSizeBytes": 999999,
  "durationMs": 3200,
  "videoProcessingConsentAccepted": true,
  "videoProcessingConsentPolicyRevision": "voice-video-processing-consent-v1"
}
```

수정해야 할 설명:

- `objectKey`, `mimeType`, `fileSizeBytes`, `durationMs`는 업로드된 단일 media의 정보다.
- audio MIME이면 Backend는 canonical WAV만 생성한다.
- video MIME이면 Backend는 같은 source object에서 canonical WAV와 canonical MP4를 생성한다.
- video MIME일 때는 현재 정책상 `videoProcessingConsentAccepted=true`와
  `videoProcessingConsentPolicyRevision=voice-video-processing-consent-v1`이 필요하다.
- 등록 성공 후 DB에는 원본 source object가 아니라 canonical WAV 정보와, 영상이 있으면
  canonical MP4 정보를 저장한다.
- 원본 업로드 객체는 성공/실패와 관계없이 처리 후 삭제된다.

### 3. `POST /api/training-sessions/{sessionId}/analyze`

수정 위치:

- `docs/api/specification.md`
- 필요 시 `docs/api/endpoints.md`

수정해야 할 설명:

- 이 API는 파일을 직접 받지 않는다.
- 선택된 recording에 canonical video가 없으면 audio-only AI request를 발행한다.
- 선택된 recording에 canonical video가 있으면 audio+video AI request를 발행한다.
- 클라이언트가 analyze 요청에서 audio/video 구분을 다시 보내지 않는다.

## RunPod HTTP 계약 확인

수정 위치:

- `docs/api/ai-runpod-http-callback-contract.md`

현재 방향:

```text
audioObjectKey: required
audioSha256: required
mimeType: audio/wav
fileSizeBytes: required
durationMs: required
visualInput: optional
```

이 구조는 유지한다. 단, 설명은 public API가 `audio required + video optional`를 직접 받는
구조처럼 보이지 않도록 `single media input -> derived AI inputs`로 표현해야 한다.

## 구현 시 확인할 코드

공개 API DTO:

- `src/main/java/org/example/voice/training/controller/dto/RecordingUploadUrlRequestDto.java`
- `src/main/java/org/example/voice/training/controller/dto/RecordingRegisterRequestDto.java`

Application 흐름:

- `src/main/java/org/example/voice/training/application/VoiceRecordingService.java`
- `src/main/java/org/example/voice/training/application/TrainingAnalysisRequestService.java`

Media normalization:

- `src/main/java/org/example/voice/training/infrastructure/storage/FfmpegS3RecordingMediaNormalizer.java`
- `src/main/java/org/example/voice/training/domain/model/NormalizedRecordingData.java`
- `src/main/java/org/example/voice/training/domain/model/NormalizedVisualData.java`

Backend-AI request model:

- `src/main/java/org/example/voice/analysis/domain/model/AnalysisWorkerRequest.java`
- `src/main/java/org/example/voice/analysis/domain/model/AnalysisWorkerVisualInput.java`

## 이번 문서화에서 변경하지 않는 것

- RunPod HTTP request/result schema version은 변경하지 않는다.
- 영상 처리 동의 필드는 유지한다.
- DB 컬럼 삭제나 migration 추가는 하지 않는다.
- public API endpoint path는 새로 만들지 않는다.

## 완료 기준

- `docs/api/specification.md`에서 upload-url, recordings, analyze 설명이 위 결정 사항과 일치한다.
- `docs/api/endpoints.md`의 요약 문구가 "단일 media 입력"과 "영상이면 음성+영상 파생"을 반영한다.
- `docs/api/ai-runpod-http-callback-contract.md`가 Backend-AI 내부 payload 기준으로 audio 필수,
  visualInput 선택을 명확히 설명한다.
- 구현 시 Controller DTO와 RunPod HTTP 내부 DTO를 재사용하지 않는다.
