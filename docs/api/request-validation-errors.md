# 요청 형식 오류 응답

이슈 #90. Spring MVC 요청 해석 오류도 공통 `ApiResponse` 형식으로 반환한다.

| 조건 | HTTP | code | message |
| --- | --- | --- | --- |
| 필수 query parameter 누락 | 400 | VALIDATION_ERROR | 입력값을 확인해 주세요. |
| 지원하지 않는 요청 Content-Type | 415 | UNSUPPORTED_MEDIA_TYPE | 지원하지 않는 요청 Content-Type입니다. |

두 경우 모두 `result: false`, `data: null`이다. 프레임워크 예외 메시지나 내부 구현 정보는 반환하지 않는다.

## 재현 예시

- `GET /api/auth/email-availability`: query `email` 누락 → 400. 정상 형식의 중복 이메일은 기존대로 200 및 `available: false`다.
- `GET /api/users/me/score-trends`: 인증 후 query `metric` 누락 → 400.
- `POST /api/users/me/profile-image`, `PUT /api/users/me/profile-image`: 인증 후 `Content-Type: application/json`과 `{}` 전송 → 415 및 `Accept: multipart/form-data`.

사진 multipart 내부 파일 형식 오류(400 INVALID_PROFILE_IMAGE), 파일 누락(400 VALIDATION_ERROR), 크기 초과(413 PAYLOAD_TOO_LARGE)와 요청 Content-Type 오류(415)는 구분한다. 보호 API의 인증 검증은 기존대로 요청 해석 전에 수행한다.

실제 미예상 서버 예외는 기존 공통 500 응답을 유지한다. 이번 수정은 지원하지 않는 HTTP 메서드 등 다른 종류의 예외 처리 변경을 포함하지 않는다.
