# API Design Rules - voice

이 문서는 voice 백엔드의 공개 HTTP API를 설계하거나 수정할 때 따라야 하는 규칙이다.
API 계약은 `docs/api/endpoints.md`, `docs/api/specification.md`, 실제 Controller/DTO 구현과 항상 함께 맞춘다.

## 기본 원칙

- 모든 공개 API는 Spring Boot Controller에서 제공하며 기본 경로는 `/api`를 사용한다.
- 현재 프로젝트는 `@RequestMapping("/api/<resource>")` 형태를 기준으로 한다.
- URL은 구현 방식이 아니라 클라이언트가 다루는 리소스와 유스케이스를 기준으로 정한다.
- URL에는 동사보다 명사를 우선 사용하고, 실제 동작은 HTTP method로 표현한다.
- Request DTO, Response DTO, domain entity, domain model, persistence entity 역할을 섞지 않는다.
- Entity를 API 응답으로 직접 노출하지 않는다.
- 신규 또는 변경 API는 구현과 함께 `docs/api/endpoints.md`, `docs/api/specification.md`를 갱신한다.

## 경로 설계

- 리소스 컬렉션은 kebab-case 복수형을 사용한다.
  - 예: `/api/training-sessions`, `/api/practice-contents`, `/api/reference-audios`
- 단일 리소스 식별자는 path variable로 표현한다.
  - 예: `/api/training-sessions/{sessionId}`, `/api/courses/{courseId}`
- 현재 로그인 사용자의 소유 리소스는 `/api/users/me/...` 경로를 사용한다.
  - 예: `/api/users/me/training-sessions`, `/api/users/me/course-progress`
- 특정 리소스의 하위 개념은 부모 리소스 아래에 둔다.
  - 예: `/api/training-sessions/{sessionId}/recordings`
  - 예: `/api/analyses/{analysisId}/segments`
- 검색, 목록 필터, 페이징 조건은 query parameter로 표현한다.
  - 예: `GET /api/practice-contents?type=&category=&difficulty=&page=&size=`
- 단순 조회가 아닌 명확한 명령형 유스케이스는 하위 action 경로를 허용한다.
  - 예: `/complete`, `/cancel`, `/start`, `/select`, `/retry`, `/regenerate`
- 내부 구현 이름을 공개 API에 노출하지 않는다.
  - 예: provider, adapter, worker, job implementation name

## HTTP Method

- `GET`: 조회, 목록, 검색, 상태 확인
- `POST`: 생성, 실행 요청, 상태 전이 명령
- `PUT`: 전체 교체 또는 전체 저장
- `PATCH`: 일부 필드 수정
- `DELETE`: 삭제 또는 삭제 요청

동일한 경로라도 method별 의미가 명확히 달라야 한다. 같은 동작을 여러 method로 중복 제공하지 않는다.

## 요청 DTO

- Controller 입력 객체는 각 모듈의 `controller/dto` 패키지에 둔다.
- 모든 DTO 클래스명은 `Dto`로 끝낸다.
  - 예: `TrainingSessionCreateRequestDto`, `PracticeContentQueryConditionDto`
- Request DTO와 Response DTO를 분리한다.
- Body 입력은 `@RequestBody` DTO로 받는다.
- Query parameter 묶음은 `@ModelAttribute` condition DTO로 받는다.
- Path variable은 Controller method parameter에서 명시적으로 받는다.
- enum 값은 domain `type` enum을 사용하되, API 문서에 허용 값을 명시한다.
- 입력 기본값과 범위 보정이 필요하면 DTO 또는 application layer에서 일관되게 처리한다.
  - 예: page 기본값, size 최대값

## 응답 DTO

- 모든 정상 응답은 `ApiResponse<T>`로 감싼다.

```json
{
  "result": true,
  "message": "Request succeeded",
  "data": {}
}
```

- 실패 응답은 `ApiResponse.error(message)` 형식을 따른다.

```json
{
  "result": false,
  "message": "Error message",
  "data": null
}
```

- Response DTO는 Controller DTO 패키지에 둔다.
- Domain model 또는 application 결과 객체는 Response DTO의 `from(...)` 팩토리 메서드에서 변환한다.
- 목록 응답은 현재 API 명세의 `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext` 형태와 맞춘다.
- Spring Data `Page`를 직접 반환하지 않고 필요하면 `PageResponse<T>` 또는 전용 Response DTO로 감싼다.
- 날짜/시간은 ISO-8601 문자열로 문서화한다.
- 파일 URL, presigned URL, token 등 만료 시간이 있는 값은 `expiresAt`, `expiresIn` 같은 필드를 함께 제공한다.

## 상태 코드

- 생성 성공은 `201 Created`를 우선 검토한다.
- 일반 조회, 수정, 명령 성공은 `200 OK`를 기본으로 한다.
- 응답 body가 필요 없는 삭제 성공은 `204 No Content`를 사용할 수 있다.
- 잘못된 입력은 `400 Bad Request`를 사용한다.
- 인증이 없거나 access token이 유효하지 않으면 `401 Unauthorized`를 사용한다.
- 인증은 되었지만 해당 리소스 또는 동작 권한이 없으면 `403 Forbidden`을 사용한다.
- 리소스를 찾을 수 없으면 `404 Not Found`를 사용한다.
- 중복, 이미 완료된 상태, 현재 상태에서 수행할 수 없는 요청은 `409 Conflict`를 사용한다.
- 외부 API 또는 비동기 분석 처리 실패는 상황에 따라 `502 Bad Gateway`, `504 Gateway Timeout`, `409 Conflict` 중 하나로 명확히 매핑한다.

## 인증과 권한

- Public API는 API 문서에 `public`으로 명시한다.
  - 예: 회원가입, 로그인, 소셜 로그인, 토큰 갱신
- 그 외 API는 기본적으로 `Authorization: Bearer <accessToken>`을 요구한다.
- Controller에서는 인증 사용자 식별자를 명확하게 application layer로 전달한다.
- 임시 구현에서 하드코딩된 `userId`를 사용하더라도, API 규칙과 문서에는 최종 인증 흐름을 기준으로 기록한다.
- 사용자 소유 리소스는 service/application layer에서 소유권을 검증한다.
- 관리자 전용 API가 생기면 문서에 `admin-only`로 명시하고 일반 사용자 API와 경로/권한을 구분한다.

## 에러 설계

- 비즈니스 예외는 `BusinessException`과 `ErrorCode`를 통해 HTTP 상태와 메시지를 결정한다.
- Controller에서 예외 응답을 직접 조립하지 않고 `GlobalExceptionHandler`를 통해 처리한다.
- 신규 에러 케이스는 `ErrorCode`에 추가하고 API 문서의 Error cases에 반영한다.
- 클라이언트가 복구 가능한 에러는 메시지가 행동 기준을 제공해야 한다.
- 외부 연동 실패, presigned URL 발급 실패, 분석 재시도 제한 등은 일반 500으로 뭉개지 않는다.

## 문서화 규칙

각 엔드포인트 문서에는 다음 항목을 포함한다.

- method
- path
- auth
- path params
- query params
- request body
- response body
- status codes
- error cases
- 구현 상태가 필요한 경우 implementation status

API 문서의 필드명과 실제 DTO 필드명은 반드시 일치해야 한다. 필드명을 바꾸면 문서, DTO, 테스트를 함께 갱신한다.

## 외부 연동 API 경계

- STT, AI feedback, storage, OAuth 같은 외부 API 응답을 클라이언트에 그대로 노출하지 않는다.
- 외부 provider DTO와 내부 API DTO를 분리한다.
- timeout, retry, fallback, failure mapping이 필요한 연동은 application/infrastructure 책임을 분리하고 문서에 실패 동작을 기록한다.
- presigned URL처럼 보안상 민감한 값은 만료 시간과 접근 권한을 명확히 한다.

## 변경 전 체크리스트

- 이 API가 기존 리소스 경로 아래에 들어갈 수 있는가?
- HTTP method가 동작 의미와 맞는가?
- Request/Response DTO가 entity나 provider DTO와 분리되어 있는가?
- 인증 요구 사항이 public, authenticated, admin-only 중 하나로 명확한가?
- 성공/실패 상태 코드가 API 명세와 `ErrorCode`에 반영되어 있는가?
- `docs/api/endpoints.md`와 `docs/api/specification.md`가 함께 갱신되었는가?
