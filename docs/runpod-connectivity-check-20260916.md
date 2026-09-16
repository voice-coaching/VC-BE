# RunPod A40 연결 점검 — 2026-09-16

> 후속 변경: 로컬 백엔드를 팟 HTTP v1.1 계약에 맞췄다. 아래는 수정 전 운영 연결 점검 기록이다. 변경 설계·배포 조건은 [최신 계약](api/ai-runpod-http-callback-contract.md)을 참고한다. 운영 배포나 저장소 설정 완료를 의미하지 않는다.

## 결론

팟 접속과 HTTP 통신은 가능하지만 현재 배포 조합으로는 백엔드 AI 분석 API가 정상 동작하지 않는다. 실제 음성 분석 → 결과 콜백의 종단 간 성공은 확인하지 못했다. 계정 API 키와 서비스 토큰은 이 문서에 기록하지 않는다.

## 실제 확인

| 항목 | 결과 |
| --- | --- |
| Pod | `s8hb5b0l5k4qlx`, `intelligentai-a40-int8-120gb-20260915`, RUNNING |
| SSH | 기존 키와 strict host-key checking으로 접속 성공 |
| GPU | NVIDIA A40, 메모리 34,485 / 46,068 MiB 사용 |
| HTTP 주소 | `https://s8hb5b0l5k4qlx-8080.proxy.runpod.net` |
| `/health` | 200, `alive` |
| `/readyz` | 503, `not_ready` |
| 인증된 `/health/services` | 503, `http_settings_incomplete_or_invalid`, `executorReady=false` |
| 팟 서비스 토큰 ↔ 로컬 `.env` | API 토큰과 callback 토큰 모두 일치; 운영 백엔드 설정과의 일치는 미확인 |
| 팟 내부 Clova `/health` | 200, `ready`, HyperCLOVAX-SEED-Think-32B, bnb-int8; 실제 생성 성공을 의미하지 않음 |
| 팟 → 운영 백엔드 claim | callback 토큰으로 401, 사용자 Access Token 오류 메시지 |

콜백 검사는 존재하지 않는 분석 ID와 임의 UUID로 수행했으며 실제 사용자 분석을 점유하지 않았다.

## 차단 원인

1. **워커 필수 저장소 설정 누락.** 생성된 런타임 환경에 다음 항목이 없다.
   - `AI_ANALYSIS_OBJECT_STORAGE_BUCKET`, `AI_ANALYSIS_OBJECT_STORAGE_REGION`, `AI_ANALYSIS_OBJECT_STORAGE_RECORDINGS_PREFIX`
   - `AI_ANALYSIS_READINESS_OBJECT_KEY`, `AI_ANALYSIS_READINESS_OBJECT_SHA256`, `AI_ANALYSIS_READINESS_OBJECT_SIZE_BYTES`
   - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`도 해당 환경 파일에 없다. 다른 AWS SDK 자격증명 공급원의 존재/권한은 검증하지 않았다.
2. **JWT 필터가 서비스 토큰을 사용자 JWT로 해석.** `SecurityConfig`에서 내부 AI 경로를 permitAll로 지정해도 `JwtAuthenticationFilter`는 먼저 Bearer 토큰을 파싱한다. 팟의 유효한 형식의 서비스 토큰도 사용자 JWT 오류로 차단된다.
3. **배포된 워커와 백엔드의 계약 불일치.** 워커 revision `b4057a008ad45c89f540e4cb6e19b6f29cd53aed`는 `voice-coaching.runpod-http.v1.1`을 검증한다.
   - 워커가 요구하는 `GET /api/internal/ai/worker-readiness`가 현재 백엔드 소스에 없다.
   - 워커 claim은 `requestPayloadSha256`을 요구하고 `schemaVersion`, `claimedUntil`을 받지 않는다. 백엔드는 반대로 `schemaVersion`, `claimedUntil`을 필수로 받는다.
   - 워커는 평평한 스키마 응답을 검증하지만 백엔드는 `result/message/data` envelope를 반환한다. lease와 result acknowledgement 필드도 맞춰야 한다.
   - 워커는 분석 요청의 `video` 필드를 필수로 요구한다(null 허용). 백엔드는 audio-only 요청에서 `video`를 생략한다.

## 이 점검에서 적용한 로컬 변경

- `.env`의 RunPod endpoint와 callback base URL 예시 값을 위 실제 팟 주소 및 `https://api.voice-coaching.site`로 변경했다. 서비스 토큰은 변경하지 않았다.
- JWT 필터에서 `/api/internal/ai/`를 제외했다. 전용 `RunPodInternalAuthentication` 검증은 그대로 유지한다.
- claim/heartbeat/result의 서비스 인증 전달, 잘못된 서비스 토큰 거절, 공개 경로와 유사 접두 경로의 JWT 검증 유지에 대한 회귀 테스트를 추가했다.
- 운영 백엔드 배포, 팟 설정 변경/재시작, 실제 사용자 데이터 분석은 수행하지 않았다.

검증: `JwtAuthenticationFilterTest`, `SecurityConfigTest`, `RunPodHttpClientConfigTest`의 총 5개 테스트가 실패/오류 없이 통과했다. 최초 샌드박스 실행은 테스트 컴파일 오류가 있었으나 일반 실행에서 컴파일과 테스트가 통과했다. Gradle의 기존 problems report 파일 충돌은 `--no-problems-report`로 우회하여 최종 `BUILD SUCCESSFUL`을 확인했다. `git diff --check`도 통과했다.

## 정상화에 필요한 후속 작업

1. 양측 v1.1 계약을 맞춘다. readiness 응답만 추가해 준비 완료로 표시하면 안 된다. claim digest, lease, heartbeat, 결과 acknowledgment 및 에러 형식까지 함께 검증한다.
2. 팟에 실제 녹음 저장소의 제한된 읽기 권한과 작은 비사용자 readiness 객체 정보를 영구 설정한다.
3. 수정된 백엔드를 배포하고 팟 설정을 적용한 뒤 `/readyz` 200을 확인한다.
4. 테스트 계정과 승인된 테스트 음성으로 업로드 → 분석 요청 → claim/heartbeat → 결과 callback → 공개 결과 조회까지 검증한다.
