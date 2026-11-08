# 브라우저 원본 영상 업로드 권한 API

2026-11-08 후보 구현. 브랜치 `feat/browser-video-upload-grant-20261108`.
기준 `6e16f6f186d4991d6dd0a8040c757e5d5150e4ca`. 운영 배포·키 설치·설정 변경은 미수행이다.

`POST /api/training-sessions/{sessionId}/video-analysis-upload-grants`

필수 헤더: `Authorization: Bearer <로그인 Access Token>`, 정확한 `Origin`,
`Content-Type: application/json`.

```json
{
  "clientRequestId": "f00f86ac-72ab-4bb2-a923-73f67596d2a4",
  "source": {
    "sha256": "<원본 파일 SHA-256 소문자 64자리>",
    "fileSizeBytes": 2097382,
    "mimeType": "video/mp4"
  },
  "videoProcessingConsentAccepted": true,
  "videoProcessingConsentPolicyRevision": "voice-video-processing-consent-v1"
}
```

원본 MIME은 video/mp4, video/webm 또는 video/quicktime, 1~268435456 bytes다. 같은 시도에서는 UUID와
원본을 유지한다. 이 API는 X-User-Id를 신뢰하지 않으며, Access Token 서명·만료 및 활성
로그인 세션을 직접 확인한다. 세션 entity의 실제 userId와 요청자를 비교하고
PRONUNCIATION 및 RECORDING·UPLOADING·FAILED 상태를 확인한다. 원고는
`TrainingSession.analysisScriptText()`에서 읽는다. 기존 얼굴 영상 처리 동의는 Backend에 기록한다.

200 기존 `ApiResponse`의 data:

| 필드 | 의미 |
|---|---|
| schemaVersion | voice-coaching.browser-video-grant.v1 |
| aiBaseUrl | 설정된 AI HTTPS origin |
| jobsPath | /browser/v1/video-analysis-jobs |
| grantToken | 300초 Ed25519 서명 JWS |
| expiresAt | Unix epoch seconds |
| metadata | clientRequestId, 서버 scriptText, contentId, source |

`Cache-Control: no-store`다. 토큰을 로그·URL·영구 브라우저 저장소에 넣지 않는다.
오류는 기존 ApiResponse와 HTTP 400/401/403/404/409/503을 사용한다.

브라우저는 data.metadata로 AI 작업을 생성하고, `/source`에 원본 bytes를 PUT하고,
`/start`를 호출한다. WAV/MP4 정규화는 AI가 수행한다. AI 호출에 로그인 토큰이나
서버 Bearer를 보내지 않고 data.grantToken만 사용한다. Origin은 브라우저가 자동 전송한다.
작업·업로드·결과 조회의 AI 상세 명세는 다음 로컬 파일에 있다.

`/home/2020067/intelligentAI-browser-video-20261108/docs/api/browser-original-video-v1.md`

## 구성

기본 비활성. 다음 Spring properties를 양쪽 설정에 맞춰 설치한다.

```properties
ai.browser-video.enabled=false
ai.browser-video.issuer=<고정 issuer>
ai.browser-video.audience=<고정 audience>
ai.browser-video.ai-base-url=https://ai.example
ai.browser-video.allowed-origins=https://frontend.example
ai.browser-video.key-id=<키 식별자>
ai.browser-video.private-key-file=<Backend 전용 절대경로>
```

활성화 시 개인키는 PKCS#8 Ed25519 PEM이어야 한다. 개인키는 Backend에만 보관하고
AI에는 같은 kid의 공개키 PEM만 설치한다. 구성 오류 시 활성 Backend가 시작되지 않는다.
AI는 alg/typ/kid/issuer/audience/scope/시간/소유자/세션/시도/원고/원본/origin을 로컬에서
검증하며 Backend readiness·callback에 의존하지 않는다.

## 범위와 검증

Backend의 기존 recording/analysis 이력 및 독립 결과 수집 worker를 브라우저 영상 시도에
자동 연결하는 기능은 포함하지 않는다. 이후 연결 시 owner/session/clientRequestId와
원고·원본 해시를 보존하고, 서버 Bearer로 기존 AI 영상 상태/result/artifacts를 수집한 뒤
저장 성공 후 ack한다. 새 UUID나 재분석을 수집 재시도로 사용하지 않는다.
공식 점수 검산표를 Backend에서 다시 검사하거나 새 점수 게이트를 추가하지 않는다.

관련 BrowserVideo* 단위 테스트와 Java 컴파일을 수행한다. 운영 키/DB·사용자 작업·GPT는
해당 테스트에서 사용하지 않는다. 최신 develop 반영 및 실제 브라우저·전체 분석 검증과
배포는 담당자 인계 범위다.
