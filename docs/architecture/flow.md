# 흐름 문서 - voice

이 문서는 voice 백엔드의 주요 기능 흐름을 단계별로 기록한다.
API 상세 필드와 응답 형식은 `docs/api/specification.md`, 모듈 책임은 `docs/architecture/component.md`를 함께 참고한다.

## 흐름 작성 기준

각 흐름은 다음 항목을 기준으로 정리한다.

- Actor: 흐름을 시작하는 사용자 또는 시스템
- Entry point: 대표 API 또는 이벤트
- Preconditions: 흐름 시작 전 필요한 조건
- Steps: 주요 처리 단계
- Validation: 검증 규칙
- Empty state: 조회 결과가 없을 때의 동작
- Error state: 실패 시 동작
- Permission behavior: 인증/권한 처리
- Retry or recovery: 재시도 또는 복구 방식
- Side effects: DB 변경, 외부 호출, 비동기 작업 등
- Related API: 관련 endpoint
- Related modules: 관련 모듈
- Related DB tables: 관련 테이블

## 공통 API 흐름

- Actor: Client
- Entry point: 모든 HTTP API
- Preconditions:
  - Public API를 제외한 요청은 `Authorization: Bearer <accessToken>`을 포함한다.
  - JSON body 요청은 `Content-Type: application/json`을 사용한다.
- Steps:
  1. Controller가 request body, path variable, query parameter를 DTO로 받는다.
  2. 인증이 필요한 API는 security layer에서 access token을 검증한다.
  3. Controller가 application service를 호출한다.
  4. Application service가 domain port를 통해 DB 또는 외부 시스템에 접근한다.
  5. 결과 model을 response DTO로 변환한다.
  6. Controller가 `ApiResponse<T>` 형태로 응답한다.
- Validation:
  - 입력 형식과 필수값을 검증한다.
  - path variable의 리소스 존재 여부를 검증한다.
  - 사용자 소유 리소스는 userId 기준으로 접근 가능 여부를 검증한다.
- Empty state:
  - 목록 조회는 빈 `items` 또는 빈 page 응답을 반환한다.
  - 단일 리소스 조회에서 대상이 없으면 404를 반환한다.
- Error state:
  - 잘못된 입력은 400
  - 인증 실패는 401
  - 권한 부족은 403
  - 리소스 없음은 404
  - 상태 충돌은 409
- Permission behavior:
  - Public API를 제외하면 로그인 사용자를 기준으로 처리한다.
- Retry or recovery:
  - 클라이언트는 401 발생 시 token refresh를 시도할 수 있다.
  - 일시적 외부 연동 실패는 명시된 재시도 API 또는 사용자 재시도를 따른다.
- Side effects:
  - command API는 DB 변경 또는 외부 시스템 호출을 만들 수 있다.
- Related API:
  - 전체 API
- Related modules:
  - `common`, 각 기능 모듈
- Related DB tables:
  - API별 관련 테이블

## 회원가입 및 로그인

- Actor: 비로그인 사용자
- Entry point:
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `POST /api/auth/social-login`
- Preconditions:
  - 일반 회원가입은 email, password, nickname, 약관 동의 값이 필요하다.
  - 일반 로그인은 email과 password가 필요하다.
  - 소셜 로그인은 provider, authorizationCode, redirectUri가 필요하다.
- Steps:
  1. Controller가 로그인 또는 회원가입 요청 DTO를 받는다.
  2. `AuthService` 또는 `SocialLoginService`가 입력을 검증한다.
  3. 일반 회원가입은 email/nickname 중복을 확인하고 password를 hash한다.
  4. 소셜 로그인은 `SocialOAuthProvider`로 외부 provider 사용자 정보를 조회한다.
  5. 기존 사용자 여부를 확인하고 필요하면 사용자와 social account를 생성한다.
  6. `TokenProvider`가 access token과 refresh token을 발급한다.
  7. refresh token을 저장한다.
  8. 로그인 결과와 온보딩 필요 여부를 응답한다.
- Validation:
  - email 중복
  - nickname 중복
  - password 검증
  - 약관 동의 여부
  - 지원하는 OAuth provider 여부
- Empty state:
  - 소셜 로그인에서 기존 계정이 없으면 신규 사용자로 생성한다.
- Error state:
  - 중복 email 또는 nickname은 409
  - 비밀번호 불일치 또는 잘못된 token은 401
  - 지원하지 않는 provider는 400
- Permission behavior:
  - Public API이다.
- Retry or recovery:
  - 로그인 실패 시 사용자는 credentials를 수정해 다시 요청한다.
  - 소셜 provider 일시 실패 시 다시 인증 요청을 진행한다.
- Side effects:
  - `users`, `social_accounts`, `refresh_tokens` 데이터가 생성 또는 갱신된다.
  - 사용자 마지막 로그인 시간이 갱신될 수 있다.
- Related API:
  - `GET /api/auth/email-availability`
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `POST /api/auth/social-login`
- Related modules:
  - `auth`, `user`
- Related DB tables:
  - `users`, `social_accounts`, `refresh_tokens`

## 토큰 갱신 및 로그아웃

- Actor: 로그인 사용자
- Entry point:
  - `POST /api/auth/token/refresh`
  - `POST /api/auth/logout`
- Preconditions:
  - token refresh는 유효한 refresh token이 필요하다.
  - logout은 현재 access token 또는 refresh token 식별 정보가 필요하다.
- Steps:
  1. `TokenService`가 refresh token을 검증한다.
  2. refresh token 저장 상태와 만료 여부를 확인한다.
  3. 갱신 요청이면 새 access token을 발급한다.
  4. 로그아웃 요청이면 refresh token을 폐기한다.
- Validation:
  - refresh token 존재 여부
  - refresh token 만료 여부
  - token owner 일치 여부
- Empty state:
  - 저장된 refresh token이 없으면 인증 실패로 처리한다.
- Error state:
  - 유효하지 않은 token은 401
  - 이미 폐기되었거나 만료된 token은 401
- Permission behavior:
  - token refresh는 public endpoint일 수 있으나 refresh token 검증이 필수다.
  - logout은 로그인 사용자 기준으로 처리한다.
- Retry or recovery:
  - refresh token이 만료되면 사용자는 다시 로그인해야 한다.
- Side effects:
  - access token 재발급
  - refresh token 폐기 또는 갱신
- Related API:
  - `POST /api/auth/token/refresh`
  - `POST /api/auth/logout`
- Related modules:
  - `auth`, `common/security`
- Related DB tables:
  - `refresh_tokens`

## 온보딩 저장 및 조회

- Actor: 로그인 사용자
- Entry point:
  - `GET /api/onboarding/me`
  - `PUT /api/onboarding/me`
  - `PATCH /api/onboarding/me`
- Preconditions:
  - 사용자는 로그인되어 있어야 한다.
  - 최초 완료 저장은 필수 설문 응답과 학습 목표 값이 필요하다.
- Steps:
  1. Controller가 온보딩 request DTO 또는 query를 받는다.
  2. `OnboardingService`가 필수값과 사용자 프로필 존재 여부를 확인한다.
  3. 전체 저장은 온보딩 프로필과 설문 응답을 저장하고 완료 시간을 기록한다.
  4. 부분 수정은 허용된 필드만 갱신한다.
  5. 조회는 현재 사용자의 온보딩 프로필과 설문 응답을 반환한다.
- Validation:
  - currentLevel 허용 값
  - 목표 시간/횟수 범위
  - 필수 설문 응답 누락 여부
- Empty state:
  - 온보딩 정보가 없으면 404 또는 onboardingRequired 상태로 처리한다.
- Error state:
  - 필수 응답 누락은 400
  - 온보딩 프로필 없음은 404
- Permission behavior:
  - 본인 온보딩 정보만 조회/수정할 수 있다.
- Retry or recovery:
  - 누락된 필드를 채운 뒤 다시 저장한다.
- Side effects:
  - `onboarding_profiles`, `survey_answers`가 생성 또는 갱신된다.
- Related API:
  - `GET /api/onboarding/me`
  - `PUT /api/onboarding/me`
  - `PATCH /api/onboarding/me`
- Related modules:
  - `onboarding`, `user`
- Related DB tables:
  - `onboarding_profiles`, `survey_answers`

## 학습 콘텐츠 탐색

- Actor: 로그인 사용자
- Entry point:
  - `GET /api/practice-contents`
  - `GET /api/practice-contents/{contentId}`
  - `GET /api/practice-contents/next`
  - `GET /api/practice-contents/{contentId}/reference-audios`
  - `GET /api/reference-audios/{audioId}/playback-url`
- Preconditions:
  - 사용자는 로그인되어 있어야 한다.
  - 콘텐츠는 게시 상태여야 한다.
- Steps:
  1. Controller가 type, category, difficulty, focus, page, size 등 조회 조건을 받는다.
  2. condition DTO가 기본 page/size 값을 보정한다.
  3. `PracticeContentService` 또는 `ReferenceAudioService`가 domain reader port를 호출한다.
  4. Infrastructure reader는 Redis Cache에 조회 결과가 있으면 캐시 값을 반환한다.
  5. 캐시가 없으면 JPA repository로 PostgreSQL을 조회하고 결과를 Redis에 저장한다.
  6. 기준 음성 재생 URL이 필요한 경우 권한과 대상 존재 여부를 확인한다.
  7. 목록, 상세, 기준 음성, 다음 콘텐츠 결과를 response DTO로 변환한다.
- Validation:
  - 콘텐츠 존재 여부
  - 게시 상태 여부
  - 기준 음성 존재 여부
  - page/size 범위
- Empty state:
  - 조건에 맞는 목록이 없으면 빈 목록을 반환한다.
  - 다음 콘텐츠가 없으면 404 또는 명시된 empty response를 반환한다.
- Error state:
  - 콘텐츠 없음은 404
  - 기준 음성 없음은 404
- Permission behavior:
  - 로그인 사용자는 게시된 콘텐츠만 조회한다.
- Retry or recovery:
  - 다른 필터 조건으로 다시 조회할 수 있다.
  - 캐시 값은 TTL 만료 후 다음 조회에서 DB 값으로 다시 채워진다.
- Side effects:
  - 일반 조회는 DB 변경을 만들지 않는다.
  - 학습 콘텐츠 목록, 상세, 다음 콘텐츠, 콘텐츠 기반 추천, 기준 음성 목록 조회는 Redis cache entry를 생성할 수 있다.
  - 재생 URL 발급은 외부 storage/CDN 접근을 만들 수 있다.
- Related API:
  - `GET /api/practice-contents`
  - `GET /api/practice-contents/{contentId}`
  - `GET /api/practice-contents/next`
  - `GET /api/practice-contents/{contentId}/reference-audios`
  - `GET /api/reference-audios/{audioId}/playback-url`
- Related modules:
  - `practicecontent`, `common/storage`
- Related DB tables:
  - `practice_contents`, `reference_audios`

## 학습 세션 생성 및 녹음

- Actor: 로그인 사용자
- Entry point:
  - `POST /api/training-sessions`
  - `GET /api/training-sessions/{sessionId}`
  - `POST /api/training-sessions/{sessionId}/recordings/upload-url`
  - `POST /api/training-sessions/{sessionId}/recordings`
  - `GET /api/training-sessions/{sessionId}/recordings`
  - `PATCH /api/training-sessions/{sessionId}/recordings/{recordingId}/select`
- Preconditions:
  - 사용자는 로그인되어 있어야 한다.
  - 학습 콘텐츠가 존재하고 학습 가능한 상태여야 한다.
  - 녹음 업로드는 유효한 sessionId가 필요하다.
- Steps:
  1. `TrainingSessionService`가 콘텐츠 존재 여부와 사용 가능 여부를 확인한다.
  2. 학습 세션을 생성하고 초기 상태를 저장한다.
  3. 사용자가 녹음 파일 업로드 URL을 요청한다.
  4. `RecordingUploadService`가 파일명, MIME type, 크기를 검증하고 presigned URL을 발급한다.
  5. 클라이언트가 storage에 파일을 업로드한다.
  6. 업로드 완료 후 `VoiceRecordingService`가 objectKey, duration, file size를 등록한다.
  7. 사용자는 녹음 목록을 조회하고 최종 녹음을 선택한다.
- Validation:
  - contentId 필수
  - learningFocus 필수
  - session owner 일치 여부
  - 파일 크기와 MIME type
  - 업로드된 object 존재 여부
  - 선택 가능한 녹음 상태 여부
- Empty state:
  - 녹음 목록이 없으면 빈 목록을 반환한다.
- Error state:
  - 콘텐츠 없음은 404
  - 학습 불가능 콘텐츠는 409
  - 세션 없음은 404
  - 파일 형식 오류는 400
  - 파일 크기 초과는 413
- Permission behavior:
  - 본인 학습 세션과 녹음만 접근할 수 있다.
- Retry or recovery:
  - 업로드 실패 시 새 upload URL을 발급받아 다시 업로드한다.
  - 품질이 낮은 녹음은 다시 녹음 후 등록한다.
- Side effects:
  - `training_sessions`, `voice_recordings`가 생성 또는 갱신된다.
  - storage에 녹음 파일이 업로드된다.
- Related API:
  - `POST /api/training-sessions`
  - `GET /api/training-sessions/{sessionId}`
  - `POST /api/training-sessions/{sessionId}/recordings/upload-url`
  - `POST /api/training-sessions/{sessionId}/recordings`
  - `GET /api/training-sessions/{sessionId}/recordings`
  - `PATCH /api/training-sessions/{sessionId}/recordings/{recordingId}/select`
- Related modules:
  - `training`, `practicecontent`, `common/storage`
- Related DB tables:
  - `training_sessions`, `voice_recordings`, `practice_contents`

## 분석 요청 및 결과 조회

- Actor: 로그인 사용자, 비동기 분석 worker
- Entry point:
  - `POST /api/training-sessions/{sessionId}/analyze`
  - `GET /api/training-sessions/{sessionId}/analysis/status`
  - `POST /api/training-sessions/{sessionId}/analysis/retry`
  - `GET /api/training-sessions/{sessionId}/analysis`
  - `GET /api/analyses/{analysisId}`
  - `GET /api/analyses/{analysisId}/segments`
  - `POST /api/analyses/{analysisId}/feedback/regenerate`
- Preconditions:
  - 학습 세션이 존재해야 한다.
  - 최종 선택된 녹음이 있어야 한다.
  - 녹음 품질이 분석 가능한 상태여야 한다.
- Steps:
  1. `TrainingAnalysisRequestService`가 세션과 최종 녹음 존재 여부를 확인한다.
  2. 이미 진행 중인 분석이 있는지 확인한다.
  3. 분석 요청 기록을 생성하고 상태를 PENDING 또는 PROCESSING으로 둔다.
  4. `AnalysisJobPublisher`가 비동기 분석 작업을 발행한다.
  5. 외부 STT/AI provider가 녹음 파일을 분석한다.
  6. `analysis` 모듈이 분석 결과와 세그먼트 결과를 저장한다.
  7. 사용자는 상태 API로 진행률을 조회한다.
  8. 완료 후 종합 분석과 세그먼트 분석을 조회한다.
  9. 완료된 분석 결과 조회는 Redis Cache에 저장될 수 있다.
  10. 필요하면 피드백 재생성 API로 AI 요약 피드백만 다시 생성한다.
- Validation:
  - session owner 일치 여부
  - 선택된 녹음 존재 여부
  - 분석 가능한 녹음 품질
  - 분석 중복 요청 여부
  - retry 가능 상태와 최대 재시도 횟수
- Empty state:
  - 분석 결과가 아직 없으면 상태 API에서 진행 중 상태를 반환한다.
  - 세그먼트 결과가 없으면 빈 page 또는 분석 미완료 상태로 처리한다.
- Error state:
  - 세션 없음은 404
  - 선택된 녹음 없음은 409
  - 품질 미달은 422
  - 이미 분석 중이면 409
  - 분석 결과 없음은 404
  - 재시도 불가능 상태는 409
- Permission behavior:
  - 본인 세션, 녹음, 분석 결과만 조회/요청할 수 있다.
- Retry or recovery:
  - FAILED 상태의 분석은 retry API로 재시도할 수 있다.
  - 피드백 재생성은 분석 데이터는 유지하고 AI summary만 다시 생성한다.
  - 캐시 값은 TTL 만료 후 다음 조회에서 DB 값으로 다시 채워진다.
- Side effects:
  - `analysis_results`, `analysis_segments`가 생성 또는 갱신된다.
  - 분석 job이 발행된다.
  - 외부 STT/AI provider가 호출된다.
  - 완료된 분석 결과 상세, 학습 세션 기준 분석 결과, 세그먼트 목록 조회는 Redis cache entry를 생성할 수 있다.
  - 피드백 재생성은 분석 상세 캐시를 무효화한다.
- Related API:
  - `POST /api/training-sessions/{sessionId}/analyze`
  - `GET /api/training-sessions/{sessionId}/analysis/status`
  - `POST /api/training-sessions/{sessionId}/analysis/retry`
  - `GET /api/training-sessions/{sessionId}/analysis`
  - `GET /api/analyses/{analysisId}`
  - `GET /api/analyses/{analysisId}/segments`
  - `POST /api/analyses/{analysisId}/feedback/regenerate`
- Related modules:
  - `training`, `analysis`, `common/storage`
- Related DB tables:
  - `training_sessions`, `voice_recordings`, `analysis_results`, `analysis_segments`

## 학습 세션 완료 및 취소

- Actor: 로그인 사용자
- Entry point:
  - `POST /api/training-sessions/{sessionId}/complete`
  - `POST /api/training-sessions/{sessionId}/cancel`
  - `DELETE /api/training-sessions/{sessionId}/recordings/{recordingId}`
  - `DELETE /api/users/me/training-sessions/{sessionId}`
- Preconditions:
  - 학습 세션이 존재해야 한다.
  - 완료는 분석이 완료되어 있어야 한다.
  - 취소는 이미 완료/취소되지 않은 세션이어야 한다.
- Steps:
  1. `TrainingSessionService`가 세션 존재와 소유권을 확인한다.
  2. 완료 요청은 선택 녹음의 분석 완료 여부를 확인한다.
  3. 완료 가능한 경우 세션 상태를 COMPLETED로 변경하고 완료 시간을 기록한다.
  4. 취소 요청은 세션 상태를 CANCELED로 변경한다.
  5. 삭제 요청은 정책에 따라 DB record와 storage file 삭제 대상 여부를 확인한다.
- Validation:
  - session owner 일치 여부
  - 분석 완료 여부
  - terminal 상태 여부
  - 삭제 가능한 녹음 여부
- Empty state:
  - 대상 세션 또는 녹음이 없으면 404
- Error state:
  - 분석 미완료는 409
  - 이미 종료된 세션은 409
  - 최종 선택 또는 분석 완료 녹음 삭제 제한은 409
- Permission behavior:
  - 본인 세션과 녹음만 변경할 수 있다.
- Retry or recovery:
  - 분석 미완료면 분석 완료 후 다시 완료 요청한다.
  - 취소 후 같은 세션은 되돌리지 않는다.
- Side effects:
  - 세션 상태와 완료/취소 시간이 갱신된다.
  - 삭제 API는 DB record 또는 storage 삭제 대상을 만들 수 있다.
- Related API:
  - `POST /api/training-sessions/{sessionId}/complete`
  - `POST /api/training-sessions/{sessionId}/cancel`
  - `DELETE /api/training-sessions/{sessionId}/recordings/{recordingId}`
  - `DELETE /api/users/me/training-sessions/{sessionId}`
- Related modules:
  - `training`, `analysis`, `common/storage`
- Related DB tables:
  - `training_sessions`, `voice_recordings`, `analysis_results`, `analysis_segments`

## 클래스 학습 흐름

- Actor: 로그인 사용자
- Entry point:
  - `GET /api/courses`
  - `GET /api/courses/{courseId}`
  - `GET /api/courses/{courseId}/steps`
  - `POST /api/courses/{courseId}/start`
  - `GET /api/courses/{courseId}/progress`
  - `PATCH /api/courses/{courseId}/progress`
  - `POST /api/courses/{courseId}/complete`
- Preconditions:
  - 사용자는 로그인되어 있어야 한다.
  - 클래스는 게시 상태여야 한다.
- Steps:
  1. 사용자가 클래스 목록을 조회한다.
  2. `CourseReader` 또는 `CourseStepReader` infrastructure 구현체가 Redis Cache에 조회 결과가 있으면 캐시 값을 반환한다.
  3. 캐시가 없으면 JPA repository로 클래스 상세와 단계 목록을 조회하고 결과를 Redis에 저장한다.
  4. 시작 API가 사용자 클래스 진도 record를 생성하거나 기존 진도를 반환한다.
  5. 사용자가 단계 학습을 진행하며 lastStepId와 progressPercent를 갱신한다.
  6. 모든 필수 단계 완료 조건을 만족하면 클래스 완료 API를 호출한다.
  7. 완료 시 사용자 클래스 진도 상태를 COMPLETED로 변경한다.
- Validation:
  - course 존재 여부
  - course publish 상태
  - lastStepId가 해당 course에 속하는지
  - progressPercent 범위
  - 완료 전 필수 단계 완료 여부
- Empty state:
  - 클래스 목록이 없으면 빈 목록을 반환한다.
  - 진도 record가 없으면 NOT_STARTED 상태를 반환할 수 있다.
- Error state:
  - 클래스 없음은 404
  - 이미 완료된 클래스는 409
  - 잘못된 progressPercent는 400
  - 잘못된 course step은 409
- Permission behavior:
  - 본인 클래스 진도만 조회/수정할 수 있다.
- Retry or recovery:
  - 잘못된 step/progress 값을 수정해 다시 요청한다.
  - 캐시 값은 TTL 만료 후 다음 조회에서 DB 값으로 다시 채워진다.
- Side effects:
  - `user_course_progress`가 생성 또는 갱신된다.
  - 클래스 목록, 상세, 단계 목록 조회는 Redis cache entry를 생성할 수 있다.
  - 클래스 시작, 진도 수정, 완료 처리는 사용자별 클래스 조회 캐시를 무효화한다.
- Related API:
  - `GET /api/courses`
  - `GET /api/courses/{courseId}`
  - `GET /api/courses/{courseId}/steps`
  - `POST /api/courses/{courseId}/start`
  - `GET /api/courses/{courseId}/progress`
  - `PATCH /api/courses/{courseId}/progress`
  - `POST /api/courses/{courseId}/complete`
- Related modules:
  - `course`, `practicecontent`
- Related DB tables:
  - `courses`, `course_steps`, `user_course_progress`, `practice_contents`

## 홈 대시보드 및 추천

- Actor: 로그인 사용자
- Entry point:
  - `GET /api/home`
  - `GET /api/recommendations`
  - `GET /api/users/me/training-sessions/recent`
  - `GET /api/users/me/weakness-recommendations`
- Preconditions:
  - 사용자는 로그인되어 있어야 한다.
  - 추천은 온보딩 정보, 최근 학습, 분석 결과가 있을수록 정교해진다.
- Steps:
  1. `HomeService`가 오늘 학습 상태, 추천, 최근 학습, 클래스 진도를 조회한다.
  2. `RecentLearningService`가 이어할 수 있는 최근 학습 세션을 조회한다.
  3. `RecommendationService`가 온보딩과 분석 결과 기반 추천 후보를 조회한다.
  4. Controller가 홈 화면용 response DTO로 조립해 반환한다.
- Validation:
  - 사용자 존재 여부
  - 조회 조건 type, limit, period 등 허용 값
- Empty state:
  - 최근 학습이 없으면 empty state 또는 404 정책을 따른다.
  - 추천 후보가 없으면 빈 추천 목록을 반환한다.
- Error state:
  - 최근 학습 없음은 현재 정책에 따라 404가 될 수 있다.
  - 사용자 없음은 404
- Permission behavior:
  - 본인 학습 데이터만 조회한다.
- Retry or recovery:
  - 학습/온보딩/분석 데이터가 쌓이면 추천 품질이 개선된다.
- Side effects:
  - 일반 조회는 DB 변경을 만들지 않는다.
- Related API:
  - `GET /api/home`
  - `GET /api/recommendations`
  - `GET /api/users/me/training-sessions/recent`
  - `GET /api/users/me/weakness-recommendations`
- Related modules:
  - `home`, `training`, `analysis`, `practicecontent`, `course`, `onboarding`
- Related DB tables:
  - `training_sessions`, `analysis_results`, `analysis_segments`, `practice_contents`, `courses`, `user_course_progress`, `onboarding_profiles`

## 마이페이지 학습 기록

- Actor: 로그인 사용자
- Entry point:
  - `GET /api/users/me/training-sessions`
  - `GET /api/users/me/training-sessions/{sessionId}`
  - `GET /api/users/me/statistics`
  - `GET /api/users/me/strengths-weaknesses`
  - `GET /api/users/me/score-trends`
- Preconditions:
  - 사용자는 로그인되어 있어야 한다.
  - 분석 기반 통계는 완료된 분석 데이터가 필요하다.
- Steps:
  1. 사용자가 학습 기록 목록 또는 상세를 조회한다.
  2. 기간, 상태, 유형, page/size 조건을 적용한다.
  3. 완료된 학습과 분석 결과를 기준으로 통계와 추이를 집계한다.
  4. 강점/약점은 세그먼트 분석 또는 분석 결과를 기준으로 계산한다.
  5. 결과를 목록, 상세, 통계, 추이 DTO로 반환한다.
- Validation:
  - 기간 조건
  - metric 허용 값
  - page/size 범위
  - session owner 일치 여부
- Empty state:
  - 기록이 없으면 빈 목록 또는 minimumDataSatisfied=false를 반환한다.
- Error state:
  - 세션 없음은 404
  - 본인 소유가 아니면 403
- Permission behavior:
  - 본인 학습 기록만 조회할 수 있다.
- Retry or recovery:
  - 기간 또는 필터 조건을 변경해 다시 조회할 수 있다.
- Side effects:
  - 조회 흐름은 DB 변경을 만들지 않는다.
- Related API:
  - `GET /api/users/me/training-sessions`
  - `GET /api/users/me/training-sessions/{sessionId}`
  - `GET /api/users/me/statistics`
  - `GET /api/users/me/strengths-weaknesses`
  - `GET /api/users/me/score-trends`
- Related modules:
  - `home`, `training`, `analysis`, `user`
- Related DB tables:
  - `training_sessions`, `voice_recordings`, `analysis_results`, `analysis_segments`
