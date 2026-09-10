# API Specification - voice

This document is generated from the API table and endpoint pages in the local API spec directory.

## Common Request Headers

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

- Public APIs: email availability, signup, login, social login, and token refresh.
- All other APIs require `Authorization: Bearer <accessToken>` unless an implementation note says otherwise.

## Common Response Format

```json
{
  "result": true,
  "message": "Request succeeded",
  "data": { }
}
```

```json
{
  "result": false,
  "message": "Error message",
  "data": null
}
```

## Auth Contract

- Login issues both an Access Token and a Refresh Token.
- Access Token is sent as `Authorization: Bearer <token>`.
- Refresh Token is sent through an HttpOnly Cookie or the token refresh request value, depending on the final backend policy.
- When the Access Token expires, call `POST /api/auth/token/refresh`.
- When the Refresh Token expires, the user must log in again.

## HTTP Status Codes

- 2xx: `200 OK`, `201 Created`, `204 No Content`
- 4xx: `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `429 Too Many Requests`
- 5xx: `500 Internal Server Error`, `502 Bad Gateway`, `503 Service Unavailable`, `504 Gateway Timeout`

## Endpoint Details

### GET /api/analysis-capabilities

- Description: 업로드·분석 UI가 사용할 형식, 크기·길이 제한, 동의 정책과 **연결 설정 여부** 조회.
- Auth: Bearer accessToken.
- Path params / Query params / Request body: 없음.
- Status: `200 OK`; 인증이 없거나 유효하지 않으면 `401`, 접근 거부 시 `403`.
- Response body (아직 저장소·RunPod 분석 callback을 연결하지 않은 기본 설정 예):

```json
{
  "result": true,
  "message": "녹음 및 분석 지원 조건을 조회했습니다.",
  "data": {
    "recordingUpload": "NOT_CONFIGURED",
    "analysisRequests": "NOT_CONFIGURED",
    "supportedLearningFocuses": ["PRONUNCIATION"],
    "acceptedAudioMimeTypes": ["audio/webm", "audio/mpeg", "audio/wav"],
    "acceptedVideoMimeTypes": ["video/mp4", "video/quicktime", "video/webm"],
    "maximumAudioUploadBytes": 20971520,
    "maximumVideoUploadBytes": 104857600,
    "minimumDurationMs": 500,
    "maximumDurationMs": 180000,
    "videoRequiresAudioTrack": true,
    "voiceProcessingConsentRequired": true,
    "videoProcessingConsentRequired": true,
    "consentPolicyRevision": null,
    "videoProcessingConsentPolicyRevision": "voice-video-processing-consent-v1"
  }
}
```

`recordingUpload`은 저장소와 미디어 정규화가 켜져 있으면 `CONFIGURED`, 아니면 `NOT_CONFIGURED`다. `analysisRequests`는 RunPod HTTP dispatch와 callback 설정이 켜져 있어야 `CONFIGURED`다. 이 API는 S3·RunPod를 호출하지 않으며, `CONFIGURED`가 연결 성공이나 추론 준비를 보증하지 않는다. 실제 업로드·분석 요청의 오류 처리도 유지한다.

크기는 바이트, 길이는 밀리초다. 표시된 파일 한도는 음성 20MiB·영상 100MiB의 업로드 URL 발급 상한과 배포된 정규화 입력 한도 중 작은 값이다. 프론트는 등록까지 처리 가능한 이 보수적인 한도를 사용한다. URL 발급 자체의 상한보다 정규화 한도가 작으면, 더 큰 파일로 URL을 얻더라도 등록 단계에서 거부된다. 영상에는 오디오 트랙이 있어야 하며 해당 영상에서 canonical WAV를 추출한다.

`consentPolicyRevision`은 음성 분석용이다. null이면 동의 정책이 미설정이므로 분석을 진행하지 않는다. 영상 등록에는 별도 `videoProcessingConsentPolicyRevision`을 사용한다. 각 revision은 정책 본문이나 실제 사용자 동의 증명 자체가 아니다.

### 녹음 등록·분석 연결 공통 보완 (2026-09-10)

1. `upload-url` 발급 → 응답의 `requiredHeaders`로 객체 업로드 → `recordings` 등록 → 녹음 선택 → 동의와 함께 `analyze` → 상태·결과 조회 순서를 사용한다.
2. 저장소 미설정 상태의 업로드·재생·실제 객체 확인은 `503`이다. 가짜 URL이나 성공한 삭제로 응답하지 않는다. 분석 Stream이 비활성화되어 있으면 분석 요청도 `503`이며 분석 job과 동의 변경 transaction은 롤백된다.
3. 등록 시 사용자→세션→발급 intent 순서로 잠근다. 발급 내역 부재는 `409 UPLOAD_INTENT_NOT_FOUND`, 만료·이미 소비된 상태는 `409 UPLOAD_INTENT_NOT_ACTIVE`, 발급 MIME·크기 불일치는 `422 ANALYSIS_SOURCE_NOT_READY`다. 실제 등록 완료 여부에 따른 `RECORDING_ALREADY_REGISTERED` 오류도 유지한다. 공통 HTTP 오류 body는 기존 `result/message/data` 형식을 따르며 enum 이름은 별도 응답 필드로 추가하지 않았다.
4. 상세 결과의 `summaryFeedback`은 워커가 보낸 승인 문장을 그대로 저장·반환한다. 결과의 음성 SHA-256은 등록된 canonical WAV와 일치해야 하며 영상이 등록되지 않은 시도의 시각 결과는 거부한다. `pronunciationEvidence`와 `visualSupplement`의 같은 음소 관계도 유지한다. 현재 STT·발음 총점·억양·속도 점수는 공급되지 않아 null이며 임의 점수로 채우지 않는다.
5. `/feedback/regenerate`의 현재 구현은 저장된 승인 문장을 다시 제공한다. Clova의 새 추론이나 새 교정 행동 생성으로 해석하지 않는다.

기본 분석 전송 계약은 RunPod HTTP request + Backend callback이다. 기존 Redis Stream 계약은 이번 RunPod 단계에서는 사용하지 않고 향후 내부 worker 또는 managed Redis 전환 시 참고 문서로 보관한다. [RunPod HTTP callback 계약](ai-runpod-http-callback-contract.md), [이번 API 연결 안내](../runpod_pipeline_api_20260910.md)를 참고한다.

### GET /api/auth/email-availability
- Description: 이메일 중복 확인 - Query: email. 사용 가능한 이메일이면 available=true 반환
- Auth: public
- Path params: None
- Query params: `email`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "email": "String",
    "available": "Boolean"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/auth/signup
- Description: 일반 회원가입 - 이메일·비밀번호·닉네임·약관 동의 시각을 받아 회원 생성 후 온보딩 필요 여부 반환
- Auth: public
- Path params: None
- Query params: None
- Request body:
```json
{
  "email": "String",
  "password": "String",
  "nickname": "String",
  "termsAgreed": "Boolean",
  "privacyAgreed": "Boolean"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "userId": "Long",
    "email": "String",
    "nickname": "String",
    "accessToken": "String (JWT)",
    "tokenType": "String",
    "expiresIn": "Integer",
    "onboardingRequired": "Boolean"
  }
}
```
- Status codes: 201 Created
- Error cases: See common error codes

### POST /api/auth/login
- Description: 일반 로그인 - 이메일·비밀번호 검증 후 Access Token과 Refresh Token 발급
- Auth: public
- Path params: None
- Query params: None
- Request body:
```json
{
  "email": "String",
  "password": "String"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "accessToken": "String (JWT)",
    "tokenType": "String",
    "expiresIn": "Integer",
    "user": {
      "id": "Long",
      "nickname": "String",
      "onboardingCompleted": "Boolean"
    }
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/auth/social-login
- Description: SNS 로그인 - provider와 authorizationCode를 받아 기존 회원 로그인 또는 신규 회원 생성. 신규 회원 여부 반환
- Auth: public
- Path params: None
- Query params: None
- Request body:
```json
{
  "provider": "String", // OAuthProvider; social_accounts.provider; 값: GOOGLE, KAKAO, NAVER, APPLE
  "authorizationCode": "String",
  "redirectUri": "String",
  "state": "String" // NAVER에서 필수, Google/Kakao에서는 선택
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "accessToken": "String (JWT)",
    "tokenType": "String",
    "expiresIn": "Integer",
    "isNewUser": "Boolean",
    "onboardingRequired": "Boolean",
    "user": {
      "id": "Long",
      "email": "String",
      "nickname": "String"
    }
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/auth/token/refresh
- Description: 토큰 갱신 - Refresh Token 쿠키 또는 요청값을 검증하여 Access Token 재발급
- Auth: public
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "accessToken": "String (JWT)",
    "tokenType": "String",
    "expiresIn": "Integer"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/auth/logout
- Description: 로그아웃 - 현재 세션 종료 및 Refresh Token 무효화
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": "Object | null"
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me
- Description: 내 정보 조회 - 로그인한 사용자의 이메일·닉네임·가입 방식·온보딩 완료 여부 조회
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "email": "String",
    "nickname": "String",
    "status": "String", // UserStatus; users.status; 값: ACTIVE, SUSPENDED, WITHDRAWN
    "loginProviders": [
      "String"
    ],
    "onboardingCompleted": "Boolean",
    "createdAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### PATCH /api/users/me
- Description: 프로필 수정 - 닉네임 등 수정 가능한 사용자 기본 정보 변경
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
{
  "nickname": "String"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "nickname": "String",
    "updatedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### DELETE /api/users/me
- Description: 회원 탈퇴 - 회원 상태를 탈퇴 처리하고 사용자 녹음 파일 및 연관 데이터 삭제 절차 수행
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "withdrawnAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK or 204 No Content
- Error cases: See common error codes

### DELETE /api/training-sessions/{sessionId}/recordings/{recordingId}
- Description: 녹음 시도 삭제 - DB soft delete와 durable storage deletion outbox를 같은 transaction에 기록. 분석 완료 녹음은 정책에 따라 제한
- Auth: Bearer accessToken
- Path params: `sessionId`, `recordingId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": "Object | null"
}
```
- Status codes: 200 OK or 204 No Content
- Error cases: See common error codes

### GET /api/onboarding/me
- Description: 온보딩 정보 조회 - 현재 수준·학습 목표·목표 학습량·설문 응답 JSON 조회
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "currentLevel": "String", // CurrentLevel; onboarding_profiles.current_level; 값: BEGINNER, INTERMEDIATE, ADVANCED
    "goalText": "String",
    "dailyGoalMinutes": "Integer",
    "weeklyGoalCount": "Integer",
    "surveyAnswers": {
      "learningPurposes": [
        "String"
      ],
      "improvementAreas": [
        "String"
      ],
      "pronunciationConcerns": [
        "String"
      ],
      "learningSituations": [
        "String"
      ]
    },
    "completedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/training-sessions/{sessionId}/analyze
- Description: AI 분석 요청 - 명시적 동의와 선택된 녹음의 음질을 확인하고 Seungun 발음 분석 작업을 비동기로 요청
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
{
  "accepted": true,
  "policyRevision": "String"
}
```
- 이 API는 파일을 직접 받지 않는다.
- 선택된 recording이 audio media에서 등록된 경우 Backend는 RunPod에 audio-only HTTP 분석 요청을 보낸다.
- 선택된 recording이 video media에서 등록된 경우 Backend는 영상에서 파생한 canonical WAV와 canonical MP4를 함께 담아 RunPod에 HTTP 분석 요청을 보낸다.
- 분석 결과는 RunPod이 Backend internal callback API로 전달한다.
- 클라이언트는 analyze 요청에서 audio/video 구분을 다시 보내지 않는다.
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "analysisId": "Long",
    "status": "String", // TrainingSessionStatus; training_sessions.status; 값: RECORDING, UPLOADING, ANALYZING, COMPLETED, FAILED, CANCELED
    "requestedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### PUT /api/onboarding/me
- Description: 온보딩 저장 및 완료 - 온보딩 전체 응답을 저장하고 completedAt을 기록. 최초 완료와 재저장 모두 지원
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
{
  "currentLevel": "String", // CurrentLevel; onboarding_profiles.current_level; 값: BEGINNER, INTERMEDIATE, ADVANCED
  "goalText": "String",
  "dailyGoalMinutes": "Integer",
  "weeklyGoalCount": "Integer",
  "surveyAnswers": {
    "learningPurposes": [
      "String"
    ],
    "improvementAreas": [
      "String"
    ],
    "pronunciationConcerns": [
      "String"
    ],
    "learningSituations": [
      "String"
    ]
  }
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "completed": "Boolean",
    "completedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/training-sessions/{sessionId}/analysis/status
- Description: 분석 진행 상태 조회 - PENDING·PROCESSING·COMPLETED·FAILED 상태와 진행 단계 반환
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "analysisId": "Long",
    "status": "String", // AnalysisStatus; analysis_results.status; 값: PENDING, PROCESSING, COMPLETED, FAILED
    "stage": "String",
    "progressPercent": "Integer",
    "failureReason": "Object | null",
    "updatedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### PATCH /api/onboarding/me
- Description: 온보딩 일부 수정 - 학습 목적·개선 영역·목표 학습량 등 일부 항목만 변경
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
{
  "goalText": "String",
  "dailyGoalMinutes": "Integer",
  "surveyAnswers": {
    "improvementAreas": [
      "String"
    ]
  }
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "goalText": "String",
    "dailyGoalMinutes": "Integer",
    "updatedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/training-sessions/{sessionId}/analysis/retry
- Description: 분석 재시도 - 새 명시적 동의와 단기 권한 grant로 실패한 분석 작업을 같은 최종 녹음으로 다시 요청
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
{
  "accepted": true,
  "policyRevision": "String"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "analysisId": "Long",
    "status": "String", // AnalysisStatus; analysis_results.status; 값: PENDING, PROCESSING, COMPLETED, FAILED
    "retryCount": "Integer",
    "requestedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/home
- Description: 홈 대시보드 조회 - 오늘의 학습 현황·추천 카드·최근 학습·클래스 진행률을 한 번에 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "today": {
      "completedCount": "Integer",
      "goalCount": "Integer",
      "learningSeconds": "Integer"
    },
    "recommendations": [
      {
        "contentId": "Long",
        "title": "String",
        "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
        "reason": "String"
      }
    ],
    "recentTraining": {
      "sessionId": "Long",
      "contentId": "Long",
      "title": "String",
      "status": "String" // 문맥별 enum 확인 필요: UserStatus / PublishStatus / CourseProgressStatus / TrainingSessionStatus / AnalysisStatus
    },
    "courseProgress": {
      "courseId": "Long",
      "title": "String",
      "progressPercent": "Number"
    }
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/training-sessions/{sessionId}/complete
- Description: 학습 세션 완료 - 분석 완료 여부를 확인하고 학습 시간과 완료 일시를 저장
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
{
  "totalLearningSeconds": "Integer"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "sessionId": "Long",
    "status": "String", // TrainingSessionStatus; training_sessions.status; 값: RECORDING, UPLOADING, ANALYZING, COMPLETED, FAILED, CANCELED
    "completedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/recommendations
- Description: 개인화 추천 목록 조회 - Query: type, limit. 온보딩과 최근 분석 결과를 기준으로 추천 콘텐츠 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: `type`, `limit`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "contentId": "Long",
        "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
        "title": "String",
        "difficulty": "String", // Difficulty; practice_contents.difficulty 또는 courses.difficulty; 값: BEGINNER, INTERMEDIATE, ADVANCED
        "reason": "String"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/training-sessions/{sessionId}/cancel
- Description: 학습 세션 취소 - 진행 중 세션을 CANCELED로 변경하고 미사용 임시 녹음 삭제 대상으로 처리
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "sessionId": "Long",
    "status": "String", // TrainingSessionStatus; training_sessions.status; 값: RECORDING, UPLOADING, ANALYZING, COMPLETED, FAILED, CANCELED
    "canceledAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/training-sessions/recent
- Description: 최근 학습 이어하기 조회 - 가장 최근의 미완료 학습 또는 완료 직전 학습 정보를 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "sessionId": "Long",
    "contentId": "Long",
    "contentTitle": "String",
    "status": "String", // 문맥별 enum 확인 필요: UserStatus / PublishStatus / CourseProgressStatus / TrainingSessionStatus / AnalysisStatus
    "resumeType": "String",
    "lastUpdatedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/recordings/{recordingId}/playback-url
- Description: 사용자 녹음 재생 URL 조회 - 본인 녹음인지 확인한 뒤 과거 녹음을 재생할 수 있는 제한시간 URL 반환
- Auth: Bearer accessToken
- Path params: `recordingId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "recordingId": "Long",
    "playbackUrl": "String (URL)",
    "expiresAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/practice-contents
- Description: 학습 콘텐츠 목록 조회 - Query: type, category, difficulty, focus, page, size. NEWS·SENTENCE·ANNOUNCER·CLASS_PRACTICE 공통 목록
- Auth: Bearer accessToken
- Path params: None
- Query params: `type`, `category`, `difficulty`, `focus`, `page`, `size`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
        "title": "String",
        "category": "String",
        "difficulty": "String", // Difficulty; practice_contents.difficulty 또는 courses.difficulty; 값: BEGINNER, INTERMEDIATE, ADVANCED
        "estimatedSeconds": "Integer"
      }
    ],
    "page": "Integer",
    "size": "Integer",
    "totalElements": "Integer",
    "totalPages": "Integer",
    "hasNext": "Boolean"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/analyses/{analysisId}
- Description: 종합 분석 결과 조회 - STT 문장·전체 점수·발음·억양·속도·강세·휴지·강점·약점·종합 피드백 반환
- Auth: Bearer accessToken
- Path params: `analysisId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "status": "String", // AnalysisStatus; analysis_results.status; 값: PENDING, PROCESSING, COMPLETED, FAILED
    "outcome": "String | null", // result.v2 완료 값: COACHING_READY, COMPLETED_NO_ISSUE
    "transcript": "String | null", // result.v2 현재 미측정
    "sttConfidence": "Number | null", // result.v2 현재 미측정
    "overallScore": "Number | null", // result.v2 현재 미측정
    "pronunciationScore": "Number | null", // result.v2 현재 미측정
    "intonationScore": "Number | null", // result.v2 현재 미측정
    "speedWpm": "Number | null", // result.v2 현재 미측정
    "speedStatus": "String | null", // result.v2 현재 미측정
    "stressScore": "Number | null", // result.v2 현재 미측정
    "pauseScore": "Number | null", // result.v2 현재 미측정
    "strengths": [
      "String"
    ],
    "weaknesses": [
      "String"
    ],
    "summaryFeedback": "String | null",
    "pronunciationEvidence": { // COACHING_READY일 때만 존재, 그 외 null
      "schemaVersion": "voice-coaching.pronunciation-evidence.v1",
      "selectedPhone": "String",
      "selectedExpectedIndex": "Integer",
      "selectedStartMs": "Integer | null",
      "selectedEndMs": "Integer | null",
      "detectorScore": "Number",
      "operatingThreshold": "Number",
      "scoreSemantics": "detector_ranking_score_not_calibrated_correctness_confidence",
      "evidenceState": "frozen_detector_threshold_passed"
    },
    "analyzedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/practice-contents/{contentId}
- Description: 학습 콘텐츠 상세 조회 - 제목·스크립트·난이도·학습 초점·뉴스 출처·목표 발음 항목을 반환
- Auth: Bearer accessToken
- Path params: `contentId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
    "learningFocus": "String", // LearningFocus; practice_contents.learning_focus 또는 training_sessions.learning_focus; 값: PRONUNCIATION, INTONATION, BOTH
    "category": "String",
    "title": "String",
    "description": "String",
    "scriptText": "String",
    "difficulty": "String", // Difficulty; practice_contents.difficulty 또는 courses.difficulty; 값: BEGINNER, INTERMEDIATE, ADVANCED
    "targetPronunciations": [
      "String"
    ],
    "estimatedSeconds": "Integer",
    "referenceAudioAvailable": "Boolean"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/analyses/{analysisId}/segments
- Description: 음절별 분석 결과 조회 - Query: page, size. 원문·인식문·재생 시간·일치 유형·점수·상세 피드백 반환
- Auth: Bearer accessToken
- Path params: `analysisId`
- Query params: `page`, `size`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "sequenceNo": "Integer",
        "expectedText": "String",
        "recognizedText": "String",
        "startMs": "Integer",
        "endMs": "Integer",
        "matchType": "String", // SegmentMatchType; analysis_segments.match_type; 값: MATCH, SUBSTITUTION, OMISSION, ADDITION
        "resultStatus": "String", // SegmentResultStatus; analysis_segments.result_status; 값: NORMAL, CAUTION, NEEDS_IMPROVEMENT
        "targetUnit": "String",
        "errorType": "String",
        "pronunciationScore": "Number",
        "intonationScore": "Number",
        "feedback": "String"
      }
    ],
    "page": "Integer",
    "size": "Integer",
    "totalElements": "Integer"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/practice-contents/{contentId}/reference-audios
- Description: 기준 음성 목록 조회 - 콘텐츠에 연결된 아나운서·코치·TTS 기준 음성 목록 반환
- Auth: Bearer accessToken
- Path params: `contentId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "speakerName": "String",
        "speakerType": "String", // SpeakerType; reference_audios.speaker_type; 값: ANNOUNCER, COACH, TTS
        "durationMs": "Integer",
        "primary": "Boolean"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/training-sessions/{sessionId}/analysis
- Description: 학습 세션 분석 결과 조회 - 세션 ID로 최종 분석 결과와 analysisId를 조회하여 결과 화면 진입에 사용
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "sessionId": "Long",
    "analysisId": "Long",
    "status": "String", // AnalysisStatus; analysis_results.status; 값: PENDING, PROCESSING, COMPLETED, FAILED
    "outcome": "String | null", // AnalysisOutcome; COMPLETED일 때만 설정
    "overallScore": "Number",
    "pronunciationScore": "Number",
    "intonationScore": "Number"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/reference-audios/{audioId}/playback-url
- Description: 기준 음성 재생 URL 조회 - 권한 검증 후 제한시간이 있는 재생 URL 또는 CDN URL 반환
- Auth: Bearer accessToken
- Path params: `audioId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "audioId": "Long",
    "playbackUrl": "String (URL)",
    "expiresAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/analyses/{analysisId}/feedback/regenerate
- Description: 분석 결과 요약 재생성 - 음절별 분석 데이터는 유지하고 AI 종합 피드백만 재생성. 운영 정책에 따라 횟수 제한 가능
- Auth: Bearer accessToken
- Path params: `analysisId`
- Query params: None
- Request body:
```json
{
  "feedbackStyle": "String"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "analysisId": "Long",
    "strengths": [
      "String"
    ],
    "weaknesses": [
      "String"
    ],
    "summaryFeedback": "String",
    "regeneratedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/practice-contents/next
- Description: 다음 학습 콘텐츠 조회 - Query: type, category, difficulty, excludeId. 현재 콘텐츠 다음에 연습할 항목 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: `type`, `category`, `difficulty`, `excludeId`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
    "title": "String",
    "difficulty": "String", // Difficulty; practice_contents.difficulty 또는 courses.difficulty; 값: BEGINNER, INTERMEDIATE, ADVANCED
    "scriptText": "String"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/training-sessions
- Description: 학습 기록 목록 조회 - Query: type, status, from, to, page, size. 완료된 학습 기록을 최신순으로 조회
- Auth: Bearer accessToken
- Path params: None
- Query params: `type`, `status`, `from`, `to`, `page`, `size`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "sessionId": "Long",
        "contentId": "Long",
        "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
        "title": "String",
        "status": "String", // 문맥별 enum 확인 필요: UserStatus / PublishStatus / CourseProgressStatus / TrainingSessionStatus / AnalysisStatus
        "overallScore": "Number",
        "completedAt": "String (ISO-8601)"
      }
    ],
    "page": "Integer",
    "size": "Integer",
    "totalElements": "Integer",
    "totalPages": "Integer",
    "hasNext": "Boolean"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/practice-contents/{contentId}/recommendations
- Description: 콘텐츠 기반 추천 조회 - 현재 콘텐츠의 난이도·발음 유형과 유사한 다음 콘텐츠 목록 반환
- Auth: Bearer accessToken
- Path params: `contentId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "title": "String",
        "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
        "similarityReason": "String"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/training-sessions/{sessionId}
- Description: 학습 기록 상세 조회 - 콘텐츠·최종 녹음·STT·종합 결과·음절별 피드백을 한 번에 반환
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "session": {
      "id": "Long",
      "status": "String", // 문맥별 enum 확인 필요: UserStatus / PublishStatus / CourseProgressStatus / TrainingSessionStatus / AnalysisStatus
      "startedAt": "String (ISO-8601)",
      "completedAt": "String (ISO-8601)",
      "totalLearningSeconds": "Integer"
    },
    "content": {
      "id": "Long",
      "title": "String",
      "scriptText": "String"
    },
    "recording": {
      "id": "Long",
      "durationMs": "Integer",
      "qualityStatus": "String" // RecordingQualityStatus; voice_recordings.quality_status; 값: PENDING, PASS, LOW_VOLUME, TOO_NOISY, TOO_SHORT, NO_SPEECH, FAILED
    },
    "analysis": {
      "id": "Long",
      "transcript": "String",
      "overallScore": "Number"
    },
    "segments": [
      {
        "sequenceNo": "Integer",
        "expectedText": "String",
        "recognizedText": "String",
        "startMs": "Integer",
        "endMs": "Integer",
        "resultStatus": "String" // SegmentResultStatus; analysis_segments.result_status; 값: NORMAL, CAUTION, NEEDS_IMPROVEMENT
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/courses
- Description: 클래스 목록 조회 - Query: type, difficulty, status, page, size. 발음·억양 클래스 목록 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: `type`, `difficulty`, `status`, `page`, `size`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "courseType": "String", // CourseType; courses.course_type; 값: PRONUNCIATION, INTONATION
        "title": "String",
        "difficulty": "String", // Difficulty; practice_contents.difficulty 또는 courses.difficulty; 값: BEGINNER, INTERMEDIATE, ADVANCED
        "estimatedMinutes": "Integer",
        "progressPercent": "Number"
      }
    ],
    "page": "Integer",
    "size": "Integer",
    "totalElements": "Integer",
    "totalPages": "Integer"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### DELETE /api/users/me/training-sessions/{sessionId}
- Description: 학습 기록 삭제 - 사용자 요청에 따라 학습 기록과 연결된 녹음 파일 및 분석 결과 삭제
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": "Object | null"
}
```
- Status codes: 200 OK or 204 No Content
- Error cases: See common error codes

### GET /api/courses/{courseId}
- Description: 클래스 상세 조회 - 클래스 설명·난이도·예상 시간·사용자 진행률 요약 반환
- Auth: Bearer accessToken
- Path params: `courseId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "courseType": "String", // CourseType; courses.course_type; 값: PRONUNCIATION, INTONATION
    "title": "String",
    "description": "String",
    "difficulty": "String", // Difficulty; practice_contents.difficulty 또는 courses.difficulty; 값: BEGINNER, INTERMEDIATE, ADVANCED
    "estimatedMinutes": "Integer",
    "stepCount": "Integer",
    "progress": {
      "status": "String", // PublishStatus; courses.status; 값: DRAFT, PUBLISHED, HIDDEN
      "progressPercent": "Number",
      "lastStepId": "Long"
    }
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/statistics
- Description: 학습 통계 조회 - Query: period, from, to. 누적 횟수·누적 시간·오늘 현황·연속 학습일·평균 점수 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: `period`, `from`, `to`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "period": {
      "from": "String",
      "to": "String"
    },
    "totalSessionCount": "Integer",
    "totalLearningSeconds": "Integer",
    "todaySessionCount": "Integer",
    "todayGoalCount": "Integer",
    "consecutiveLearningDays": "Integer",
    "averageOverallScore": "Number",
    "averagePronunciationScore": "Number",
    "averageIntonationScore": "Number"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/courses/{courseId}/steps
- Description: 클래스 단계 목록 조회 - 이론·예시 듣기·녹음 연습·결과 확인 단계와 연결 콘텐츠 반환
- Auth: Bearer accessToken
- Path params: `courseId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "stepOrder": "Integer",
        "stepType": "String", // CourseStepType; course_steps.step_type; 값: THEORY, AUDIO_EXAMPLE, PRACTICE, RESULT_REVIEW
        "title": "String",
        "completed": "Boolean"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/strengths-weaknesses
- Description: 강점 및 약점 조회 - Query: period, limit. analysis_segments를 집계하여 잘하는 발음과 반복 오류 항목 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: `period`, `limit`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "strengths": [
      {
        "targetUnit": "String",
        "label": "String",
        "averageScore": "Number",
        "attemptCount": "Integer"
      }
    ],
    "weaknesses": [
      {
        "targetUnit": "String",
        "label": "String",
        "averageScore": "Number",
        "attemptCount": "Integer",
        "commonErrorType": "String"
      }
    ],
    "minimumDataSatisfied": "Boolean"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/courses/{courseId}/start
- Description: 클래스 시작 - 사용자 클래스 진도 레코드를 생성하거나 기존 진도를 이어서 반환
- Auth: Bearer accessToken
- Path params: `courseId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "courseId": "Long",
    "status": "String", // CourseProgressStatus; user_course_progress.status; 값: NOT_STARTED, IN_PROGRESS, COMPLETED
    "lastStepId": "Long",
    "progressPercent": "Number"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/score-trends
- Description: 점수 변화 추이 조회 - Query: metric, period. 발음·억양·종합 점수의 날짜별 변화 데이터를 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: `metric`, `period`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "metric": "String",
    "points": [
      {
        "date": "String (ISO-8601)",
        "score": "Number",
        "sessionCount": "Integer"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/course-progress
- Description: 내 클래스 진행 목록 조회 - Query: status. 사용자가 시작하거나 완료한 클래스와 진행률 조회
- Auth: Bearer accessToken
- Path params: None
- Query params: `status`
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "courseId": "Long",
        "title": "String",
        "status": "String", // CourseProgressStatus; user_course_progress.status; 값: NOT_STARTED, IN_PROGRESS, COMPLETED
        "lastStepId": "Long",
        "progressPercent": "Number",
        "updatedAt": "String (ISO-8601)"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/users/me/weakness-recommendations
- Description: 약점 기반 추천 조회 - 최근 약점 분석을 기준으로 문장·뉴스·클래스 추천 목록 반환
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "weaknesses": [
      {
        "targetUnit": "String",
        "label": "String",
        "averageScore": "Number"
      }
    ],
    "recommendations": [
      {
        "targetType": "String",
        "contentId": "Long",
        "contentType": "String", // ContentType; practice_contents.content_type; 값: NEWS, SENTENCE, ANNOUNCER, CLASS_PRACTICE
        "title": "String",
        "reason": "String"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### GET /api/courses/{courseId}/progress
- Description: 클래스 진행 상태 조회 - 현재 상태·마지막 단계·진행률·완료 일시 조회
- Auth: Bearer accessToken
- Path params: `courseId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "courseId": "Long",
    "status": "String", // CourseProgressStatus; user_course_progress.status; 값: NOT_STARTED, IN_PROGRESS, COMPLETED
    "lastStepId": "Long",
    "progressPercent": "Number",
    "startedAt": "String (ISO-8601)",
    "completedAt": "Object | null"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### PATCH /api/courses/{courseId}/progress
- Description: 클래스 진행 상태 수정 - lastStepId와 progressPercent를 갱신하여 이어서 학습 기능 제공
- Auth: Bearer accessToken
- Path params: `courseId`
- Query params: None
- Request body:
```json
{
  "lastStepId": "Long",
  "progressPercent": "Number"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "courseId": "Long",
    "status": "String", // CourseProgressStatus; user_course_progress.status; 값: NOT_STARTED, IN_PROGRESS, COMPLETED
    "lastStepId": "Long",
    "progressPercent": "Number"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/courses/{courseId}/complete
- Description: 클래스 완료 처리 - 모든 필수 단계 완료 여부를 확인한 뒤 COMPLETED 상태로 변경
- Auth: Bearer accessToken
- Path params: `courseId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "courseId": "Long",
    "status": "String", // CourseProgressStatus; user_course_progress.status; 값: NOT_STARTED, IN_PROGRESS, COMPLETED
    "progressPercent": "Number",
    "completedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/training-sessions
- Description: 학습 세션 생성 - contentId·courseStepId(선택)·learningFocus를 받아 학습 세션 생성
- Auth: Bearer accessToken
- Path params: None
- Query params: None
- Request body:
```json
{
  "contentId": "Long",
  "courseStepId": "Object | null",
  "learningFocus": "String" // LearningFocus; practice_contents.learning_focus 또는 training_sessions.learning_focus; 값: PRONUNCIATION, INTONATION, BOTH
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "sessionId": "Long",
    "contentId": "Long",
    "courseStepId": "Object | null",
    "learningFocus": "String", // LearningFocus; practice_contents.learning_focus 또는 training_sessions.learning_focus; 값: PRONUNCIATION, INTONATION, BOTH
    "status": "String", // TrainingSessionStatus; training_sessions.status; 값: RECORDING, UPLOADING, ANALYZING, COMPLETED, FAILED, CANCELED
    "startedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 201 Created
- Error cases: See common error codes

### GET /api/training-sessions/{sessionId}
- Description: 학습 세션 조회 - 세션 상태·콘텐츠·녹음 시도·분석 가능 여부를 조회
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "id": "Long",
    "status": "String", // TrainingSessionStatus; training_sessions.status; 값: RECORDING, UPLOADING, ANALYZING, COMPLETED, FAILED, CANCELED
    "content": {
      "id": "Long",
      "title": "String",
      "scriptText": "String"
    },
    "selectedRecordingId": "Long",
    "recordingCount": "Integer",
    "analysisAvailable": "Boolean",
    "startedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### POST /api/training-sessions/{sessionId}/recordings/upload-url
- Description: 단일 media 업로드 URL 발급 - 파일명·MIME 타입·크기를 받아 음성 또는 영상 원본을 private object storage에 업로드할 Presigned URL 발급
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
{
  "fileName": "String",
  "mimeType": "String",
  "fileSizeBytes": "Integer"
}
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "objectKey": "String",
    "uploadUrl": "String (URL)",
    "expiresAt": "String (ISO-8601)",
    "requiredHeaders": {
      "Content-Type": "String",
      "Content-Length": "String"
    }
  }
}
```
- 이 API는 하나의 업로드 media URL만 발급한다.
- audio MIME이면 음성-only 분석 후보가 된다.
- video MIME이면 등록 단계에서 Backend가 같은 source object에서 canonical WAV와 canonical MP4를 파생할 수 있다.
- 영상 파일을 업로드하더라도 AI speech analysis의 기준 입력은 영상에서 추출한 canonical WAV다.
- 허용 형식: `audio/webm`, `audio/mpeg`, `audio/wav`는 최대 20 MiB;
  `video/mp4`, `video/quicktime`, `video/webm`은 최대 100 MiB이다.
- Status codes: 200 OK, 400 unsupported format, 413 size limit
- Error cases: See common error codes

### POST /api/training-sessions/{sessionId}/recordings
- Description: 단일 media 업로드 완료 등록 - 업로드 객체를 소유권·실제 container/codec 기준으로 검사하고 Backend에서 AI 입력용 canonical media를 만든 뒤 녹음 시도를 등록
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
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
- `objectKey`, `mimeType`, `fileSizeBytes`, `durationMs`는 업로드된 단일 source media의 정보다.
- `durationMs`는 클라이언트 표시용 주장이고 영속 값은 정규화 WAV에서 다시 측정한다.
- audio MIME이면 Backend는 source media를 16 kHz mono PCM WAV로 정규화하고, 이후 AI에는 audio-only request를 발행한다.
- video MIME이면 Backend는 같은 source media에서 음성 track을 추출해 16 kHz mono PCM WAV를 만들고, 영상 분석 입력이 필요할 때 canonical MP4도 생성한다.
- 영상 MIME이면 두 consent 필드가 각각 `true`,
  `voice-video-processing-consent-v1`이어야 하며, 동의 검증 전에 영상을 decode하지 않는다.
- object key는 인증 사용자와 path의 `sessionId`에 발급된 prefix와 정확히 일치해야 한다.
- 원본 업로드 객체는 성공·실패와 무관하게 처리 후 삭제한다. DB에는 backend-only
  정규화 WAV key, 실제 크기·duration, SHA-256, 기술 품질 상태를 저장하고, 영상 입력이 있으면 canonical MP4 key와 digest도 함께 저장한다.
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "recordingId": "Long",
    "attemptNo": "Integer",
    "analysisMediaType": "String", // AUDIO_ONLY 또는 AUDIO_VISUAL
    "qualityStatus": "String", // RecordingQualityStatus; voice_recordings.quality_status; 값: PENDING, PASS, LOW_VOLUME, TOO_NOISY, TOO_SHORT, NO_SPEECH, FAILED
    "selected": "Boolean",
    "createdAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK, 400 missing video consent, 403 object owner mismatch,
  422 invalid container/codec or technical quality result, 503 unavailable normalizer/storage
- Error cases: See common error codes

### GET /api/training-sessions/{sessionId}/recordings
- Description: 녹음 시도 목록 조회 - 해당 학습 세션의 녹음 시도와 품질 검사 상태 조회
- Auth: Bearer accessToken
- Path params: `sessionId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "items": [
      {
        "id": "Long",
        "attemptNo": "Integer",
        "durationMs": "Integer",
        "analysisMediaType": "String", // AUDIO_ONLY 또는 AUDIO_VISUAL
        "qualityStatus": "String", // RecordingQualityStatus; voice_recordings.quality_status; 값: PENDING, PASS, LOW_VOLUME, TOO_NOISY, TOO_SHORT, NO_SPEECH, FAILED
        "selected": "Boolean"
      }
    ]
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

### PATCH /api/training-sessions/{sessionId}/recordings/{recordingId}/select
- Description: 최종 녹음 선택 - 분석에 사용할 최종 녹음을 선택하고 다른 시도는 선택 해제
- Auth: Bearer accessToken
- Path params: `sessionId`, `recordingId`
- Query params: None
- Request body:
```json
// Body 없음
```
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "sessionId": "Long",
    "selectedRecordingId": "Long",
    "selectedAt": "String (ISO-8601)"
  }
}
```
- Status codes: 200 OK
- Error cases: See common error codes

## 내부 API 매핑

- 현재 RunPod 기반 Backend-AI 분석 연동은
  [RunPod HTTP callback 계약](ai-runpod-http-callback-contract.md)을 따른다.
- 기존 [Redis Stream contract](ai-redis-stream-contract.md)는 이번 RunPod 단계에서는 사용하지 않고,
  향후 내부 worker 또는 managed Redis 전환 시 참고 문서로 보관한다.
- 이 공개 REST API는 분석 요청과 상태·결과 조회만 제공한다. RunPod은 Backend internal callback API로
  결과를 전달하며, 클라이언트는 RunPod API나 내부 callback API를 직접 호출하지 않는다.
- STT와 AI 피드백 구현 세부사항은 RunPod/AI 내부 계약이며 공개 API DTO에 직접 노출하지 않는다.

### POST /api/internal/ai/analyses/{analysisId}/result
- 구현 상태: 예정
- Description: RunPod AI 분석 결과 callback - Backend가 요청한 비동기 분석 결과를 내부 API로 수신한다.
- Auth: `Authorization: Bearer {AI_CALLBACK_TOKEN}`
- Path params: `analysisId`
- Query params: None
- Request body: [RunPod HTTP callback 계약](ai-runpod-http-callback-contract.md)을 따른다.
- Response body:
```json
{
  "result": "Boolean",
  "message": "String",
  "data": {
    "analysisId": "Long",
    "status": "String"
  }
}
```
- Status codes: 200 OK, 400 Bad Request, 401 Unauthorized, 404 Not Found, 409 Conflict, 422 Unprocessable Entity, 503 Service Unavailable
- Error cases: callback token 누락 또는 불일치, analysis id 불일치, stale request id, 이미 terminal 상태인 analysis, 지원하지 않는 result schema, 결과 저장 실패
