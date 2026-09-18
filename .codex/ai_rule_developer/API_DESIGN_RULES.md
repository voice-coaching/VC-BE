# API 설계 규칙 — VC-BE

## 공개 계약

- 기존 Spring MVC Controller와 /api prefix를 따른다. /api/v1로 임의 전환하지 않는다.
- 리소스는 kebab-case 복수형, 소유 관계는 하위 경로, 검색·페이징은 query parameter로 표현한다.
- 기존 /analyze, /retry, /select, /complete, /cancel, /regenerate 같은 명령 경로는 유지한다. REST 정리만을 이유로 변경하지 않는다.
- Request/Response DTO는 feature/controller/dto에 두고 기존 *Dto 이름과 명시적 필드 타입을 따른다.
- 형식 검증은 Jakarta Validation/DTO 경계에서, 소유권과 상태 검증은 application에서 수행한다.
- domain model → 응답 DTO의 from(...) 변환을 사용하고 JPA entity나 Spring Data Page를 직접 직렬화하지 않는다.

## 응답·실패

- 일반 공개 응답은 common/response/ApiResponse의 result, message, data 구조를 사용한다.
- code는 현재 ApiResponse의 선택 필드다. 실제 예외 매핑에서 제공할 때만 존재하며, 모든 실패가 code를 가진다고 가정하지 않는다.
- 페이지 응답, null, 필드 생략, enum 직렬화, 시간 형식은 해당 DTO·테스트·명세를 함께 확인한다.
- 기존 성공 status를 보존한다. POST를 일괄 201로 바꾸지 않는다.
- 비즈니스 예외는 BaseException/BusinessException/ErrorCode와 GlobalExceptionHandler를 따른다.
- 401(인증 실패), 403(권한 부족), 404(대상 없음), 409(상태 충돌), 413/422/429 및 upstream 실패 매핑은 실제 계약대로 문서화한다.
- 사용자에게 반환되는 메시지에는 외부 오류 원문·비밀정보를 넣지 않는다.

## 인증 경계

- public / authenticated / admin-only / service-token 중 대상 계약을 명시한다.
- SecurityConfig, JwtAuthenticationFilter, LoginUser 및 해당 service의 소유권 검사를 함께 확인한다.
- /api/internal/ai는 사용자 JWT와 별도의 서비스 토큰 인증이다. SecurityConfig의 permitAll만 보고 무인증이라고 해석하지 않는다.
- 로그인·refresh cookie·만료·CORS 변경은 인증 테스트와 문서를 함께 검토한다.

## 내부 AI 계약

- /api/internal/ai 및 worker /v1/analysis-jobs는 전용 JSON Schema·인증·flat response 계약을 따른다.
- 내부 응답에 공개 ApiResponse envelope를 추가하거나 HTTP 결과 DTO를 Stream 결과 모델과 동일시하지 않는다.
- OpenAPI에서 숨겨진 내부 endpoint도 docs/api/endpoints.md의 내부 API 구역과 관련 상세 계약에 기록한다.

## 문서

- docs/api/endpoints.md는 method, path, auth, 한 줄 설명의 색인이다.
- docs/api/specification.md는 요청·응답 필드, type, required/null/생략, enum, status, 오류·부수효과의 계약이다.
- 두 문서의 endpoint 집합을 맞춘다. 긴 내부 wire schema는 docs/contracts와 전용 계약 문서에 연결한다.
- 공개 DTO·보안 설정 변경 시 대상 Controller 테스트와 필요하면 OpenAPI 테스트를 실행한다.
