# Media Input API Update Result

- 작성일: 2026-09-09
- 기준 문서: [`media-input-api-update-plan.md`](media-input-api-update-plan.md)
- 작업 유형: API 명세 수정, OpenAPI 설명 수정, 결과 문서화

## 적용한 결정

Public API는 음성과 영상을 별도 필드로 동시에 받지 않고, 하나의 source media를 받는다.

```text
audio source media
  -> Backend canonical WAV 생성
  -> AI request는 audio-only

video source media
  -> Backend가 같은 영상에서 canonical WAV 추출
  -> Backend가 canonical MP4 생성
  -> AI request는 audio + visualInput
```

Backend-AI Redis Stream payload는 기존 방향을 유지한다.

```text
audioObjectKey: required
audioSha256: required
mimeType: audio/wav
fileSizeBytes: required
durationMs: required
visualInput: optional
```

## 수정한 API 명세

### 1. `POST /api/training-sessions/{sessionId}/recordings/upload-url`

수정 파일:

- [`specification.md`](specification.md)
- [`endpoints.md`](endpoints.md)

변경 내용:

- "음성 또는 영상 녹음 업로드 URL" 표현을 "단일 media 업로드 URL"로 정리했다.
- request body는 기존 단일 media 구조를 유지했다.
- audio MIME이면 음성-only 분석 후보가 되고, video MIME이면 등록 단계에서 Backend가
  음성과 영상을 파생할 수 있음을 명시했다.
- 영상 파일을 업로드하더라도 AI speech analysis의 기준 입력은 영상에서 추출한
  canonical WAV라고 명시했다.

현재 request body:

```json
{
  "fileName": "String",
  "mimeType": "String",
  "fileSizeBytes": "Integer"
}
```

### 2. `POST /api/training-sessions/{sessionId}/recordings`

수정 파일:

- [`specification.md`](specification.md)
- [`endpoints.md`](endpoints.md)

변경 내용:

- "업로드 객체 등록" 설명을 단일 source media 기준으로 정리했다.
- `objectKey`, `mimeType`, `fileSizeBytes`, `durationMs`가 하나의 source media에 대한
  값임을 명시했다.
- audio MIME이면 canonical WAV만 생성한다고 명시했다.
- video MIME이면 같은 source media에서 canonical WAV와 canonical MP4를 만든다고 명시했다.
- video MIME일 때 현재 정책상 영상 처리 동의 필드가 필요하다는 설명은 유지했다.
- DB에는 원본 source object가 아니라 canonical WAV 정보와, 영상 입력이 있으면
  canonical MP4 정보를 저장한다고 명시했다.

현재 request body:

```json
{
  "objectKey": "String",
  "mimeType": "String",
  "fileSizeBytes": "Integer",
  "durationMs": "Integer",
  "videoProcessingConsentAccepted": "Boolean | null",
  "videoProcessingConsentPolicyRevision": "String | null"
}
```

### 3. `POST /api/training-sessions/{sessionId}/analyze`

수정 파일:

- [`specification.md`](specification.md)
- [`endpoints.md`](endpoints.md)
- `src/main/java/org/example/voice/common/config/OpenApiKoreanDocumentation.java`

변경 내용:

- "음성 분석 요청"을 "AI 분석 요청"으로 정리했다.
- 이 API는 파일을 직접 받지 않는다고 명시했다.
- 선택된 recording이 audio media 기반이면 audio-only Redis Stream request를 발행한다고
  명시했다.
- 선택된 recording이 video media 기반이면 canonical WAV와 canonical MP4를 함께 담은
  Redis Stream request를 발행한다고 명시했다.
- 클라이언트는 analyze 요청에서 audio/video 구분을 다시 보내지 않는다고 명시했다.

현재 request body:

```json
{
  "accepted": true,
  "policyRevision": "String"
}
```

## 수정한 코드

### `OpenApiKoreanDocumentation.java`

Swagger/OpenAPI 표시 문구만 수정했다. Controller path, request DTO, response DTO, service
흐름은 변경하지 않았다.

변경 내용:

- `fileName`, `mimeType`, `fileSizeBytes`, `durationMs` 필드 설명을 음성 전용 표현에서
  source media 표현으로 바꿨다.
- 업로드 URL 발급 operation 이름을 "단일 media 업로드 URL 발급"으로 바꿨다.
- 업로드 완료 등록 operation 이름을 "단일 media 업로드 완료 등록"으로 바꿨다.
- 분석 요청 operation 이름을 "AI 분석 요청"으로 바꿨다.

## 새로 만들지 않은 API

다음 API는 만들지 않았다.

```text
POST /api/training-sessions/{sessionId}/recordings/audio
POST /api/training-sessions/{sessionId}/recordings/video
POST /api/analyses/ai-test
```

이유:

- 현재 요구사항은 하나의 source media를 업로드하는 흐름이다.
- 기존 `recordings/upload-url`과 `recordings`가 이미 이 유스케이스의 리소스 경계다.
- Backend-AI Redis Stream 계약은 public API가 아니라 내부 infrastructure 계약이다.

## 남은 구현 확인 사항

- 실제 Controller DTO는 이미 단일 media 구조라 이번 작업에서 변경하지 않았다.
- 영상 MIME일 때 동의 필드는 현재 정책대로 유지했다.
- Redis Stream schema version은 변경하지 않았다.
- 추후 음성과 영상을 별도 파일로 동시에 업로드하는 요구사항이 생기면 request body를
  `audio` 필수 + `video` 선택 nested 구조로 재설계해야 한다.
