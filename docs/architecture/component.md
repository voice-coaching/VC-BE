# 컴포넌트 및 모듈 문서 - voice

이 문서는 voice 백엔드의 기능 모듈과 주요 컴포넌트 책임을 기록한다.
상세 패키지 구조는 `docs/architecture/architecture.md`와 `docs/architecture/directory.md`를 함께 참고한다.

## 분리 기준

- 기능 모듈은 사용자에게 제공되는 도메인 기능을 기준으로 나눈다.
- 각 기능 모듈은 기본적으로 `controller`, `application`, `domain`, `infrastructure` 계층을 가진다.
- API request/response DTO는 `controller/dto`에 둔다.
- 저장 대상 entity, 조회 model, port, enum type은 `domain` 하위에 둔다.
- DB 접근과 외부 시스템 연동 구현은 `infrastructure`에 둔다.
- 여러 모듈에서 공유되는 기반 컴포넌트만 `common`에 둔다.

## 컴포넌트 계약 규칙

- Controller는 HTTP 계약을 담당하고 application service를 호출한다.
- Application service는 유스케이스를 수행하고 domain port를 통해 저장소와 외부 기능을 사용한다.
- Domain port는 application이 필요로 하는 기능 계약을 정의한다.
- Infrastructure 구현체는 domain port를 구현하며 JPA repository 또는 외부 client를 사용한다.
- Response DTO는 domain model 또는 application 결과를 받아 API 응답 형태로 변환한다.
- Entity는 외부 API 응답으로 직접 노출하지 않는다.

## 모듈 목록

| Module | Main Responsibility | Main Application Services | Main Ports |
| --- | --- | --- | --- |
| `auth` | 회원가입, 로그인, 소셜 로그인, JWT, refresh token | `AuthService`, `SocialLoginService`, `TokenService`, `SocialAccountService` | `TokenProvider`, `PasswordHasher`, `SocialOAuthProvider`, `RefreshTokenReader`, `RefreshTokenWriter`, `SocialAccountReader`, `SocialAccountWriter` |
| `user` | 사용자 조회, 프로필 수정, 사용자 상태/역할 관리 | `UserService` | `UserReader`, `UserWriter` |
| `onboarding` | 온보딩 프로필과 설문 응답 저장/조회 | `OnboardingService` | `OnboardingProfileReader`, `OnboardingProfileWriter` |
| `practicecontent` | 학습 콘텐츠, 기준 음성, 콘텐츠 추천 | `PracticeContentService`, `PracticeContentRecommendationService`, `ReferenceAudioService` | `PracticeContentReader`, `ReferenceAudioReader` |
| `training` | 학습 세션, 녹음 업로드/선택, 분석 요청 | `TrainingSessionService`, `VoiceRecordingService`, `RecordingUploadService`, `TrainingAnalysisRequestService` | `TrainingSessionReader`, `TrainingSessionWriter`, `VoiceRecordingReader`, `VoiceRecordingWriter`, `TrainingAnalysisReader`, `TrainingAnalysisWriter`, `AnalysisJobPublisher` |
| `analysis` | 분석 결과, 세그먼트 분석, 피드백 재생성 | `AnalysisService`, `AnalysisSegmentService`, `FeedbackRegenerationService` | `AnalysisResultReader`, `AnalysisResultWriter`, `AnalysisSegmentReader`, `AnalysisSegmentWriter` |
| `course` | 클래스, 클래스 단계, 사용자 클래스 진도 | `CourseService`, `CourseStepService`, `CourseProgressService` | `CourseReader`, `CourseStepReader`, `CourseProgressReader`, `CourseProgressWriter` |
| `home` | 홈 대시보드, 최근 학습, 추천 조회 | `HomeService`, `RecentLearningService`, `RecommendationService` | `HomeReader` |
| `common` | 공통 설정, 응답, 예외, 보안, 저장소, 유틸리티 | N/A | N/A |

## Auth 컴포넌트

### 책임

- 일반 회원가입과 로그인
- 소셜 로그인 provider 연동
- access token과 refresh token 발급/갱신
- refresh token 저장과 폐기
- 사용자 인증 관련 예외 처리

### 주요 계약

- `TokenProvider`: JWT 생성, 파싱, 검증 계약
- `PasswordHasher`: 비밀번호 해시와 검증 계약
- `SocialOAuthProvider`: 외부 OAuth provider 사용자 정보 조회 계약
- `RefreshTokenReader` / `RefreshTokenWriter`: refresh token 조회와 저장 계약
- `SocialAccountReader` / `SocialAccountWriter`: social account 조회와 저장 계약

### 소유 경계

- OAuth provider 세부 구현은 `auth/infrastructure`에 둔다.
- JWT 구현체도 `auth/infrastructure`에 둔다.
- 사용자 생성이 필요하면 `user` port를 통해 사용자 모듈과 협력한다.

## User 컴포넌트

### 책임

- 내 정보 조회
- 프로필 수정
- 회원 탈퇴
- 사용자 상태와 역할 관리

### 주요 계약

- `UserReader`: 사용자 조회 계약
- `UserWriter`: 사용자 생성, 수정, 상태 변경 계약

### 소유 경계

- 사용자 entity와 사용자 상태 enum은 `user/domain`이 소유한다.
- 인증 토큰 자체의 발급/검증은 `auth`가 소유한다.

## Onboarding 컴포넌트

### 책임

- 사용자 온보딩 프로필 저장
- 온보딩 설문 응답 저장/수정/조회
- 학습 수준, 학습 목표, 주간/일일 목표 관리

### 주요 계약

- `OnboardingProfileReader`: 온보딩 프로필 조회 계약
- `OnboardingProfileWriter`: 온보딩 프로필 저장/수정 계약

### 소유 경계

- 온보딩 설문 구조와 온보딩 완료 여부는 `onboarding` 모듈이 소유한다.
- 사용자 기본 정보는 `user` 모듈과 분리한다.

## Practice Content 컴포넌트

### 책임

- 학습 콘텐츠 목록/상세 조회
- 기준 음성 목록과 재생 URL 조회
- 다음 학습 콘텐츠 조회
- 콘텐츠 기반 추천 조회

### 주요 계약

- `PracticeContentReader`: 학습 콘텐츠 조회와 추천 후보 조회 계약
- `ReferenceAudioReader`: 기준 음성 조회 계약

### 소유 경계

- 콘텐츠, 기준 음성 entity와 콘텐츠 타입/난이도/학습 초점 enum은 `practicecontent`가 소유한다.
- 사용자별 학습 기록 기반 추천은 `home` 또는 `training` 데이터와 협력할 수 있지만, 콘텐츠 원본 데이터는 이 모듈이 소유한다.

## Training 컴포넌트

### 책임

- 학습 세션 생성, 조회, 완료, 취소
- 녹음 업로드 URL 발급
- 업로드 완료된 녹음 등록
- 녹음 시도 목록 조회와 최종 녹음 선택
- 분석 요청, 진행 상태 조회, 재시도 요청

### 주요 계약

- `TrainingSessionReader` / `TrainingSessionWriter`: 학습 세션 조회와 상태 변경 계약
- `VoiceRecordingReader` / `VoiceRecordingWriter`: 녹음 파일 메타데이터 조회와 저장 계약
- `TrainingAnalysisReader` / `TrainingAnalysisWriter`: 분석 요청/결과 흐름 조회와 저장 계약
- `AnalysisJobPublisher`: 비동기 분석 작업 발행 계약

### 소유 경계

- 학습 세션 생명주기와 녹음 선택 규칙은 `training` 모듈이 소유한다.
- 실제 분석 결과의 상세 데이터는 `analysis` 모듈이 소유한다.
- 파일 저장소 presigned URL 발급 구현은 `training/infrastructure` 또는 `common/storage` 경계에 둔다.

## Analysis 컴포넌트

### 책임

- 분석 결과 조회
- 세그먼트별 분석 결과 조회
- STT/음성 분석 provider 결과 저장
- AI 피드백 재생성

### 주요 계약

- `AnalysisResultReader` / `AnalysisResultWriter`: 분석 결과 조회와 저장 계약
- `AnalysisSegmentReader` / `AnalysisSegmentWriter`: 세그먼트 분석 조회와 저장 계약

### 소유 경계

- 분석 결과 상태, 점수, STT transcript, 세그먼트 결과는 `analysis` 모듈이 소유한다.
- 분석 요청 트리거와 학습 세션 상태 변경은 `training` 모듈과 협력한다.
- 외부 STT/AI provider 응답은 내부 model로 변환한 뒤 사용한다.

## Course 컴포넌트

### 책임

- 클래스 목록과 상세 조회
- 클래스 단계 목록 조회
- 사용자 클래스 시작, 진도 조회/수정, 완료 처리

### 주요 계약

- `CourseReader`: 클래스 목록/상세 조회 계약
- `CourseStepReader`: 클래스 단계 조회 계약
- `CourseProgressReader` / `CourseProgressWriter`: 사용자 클래스 진도 조회와 저장 계약

### 소유 경계

- 클래스, 클래스 단계, 사용자 클래스 진도 entity는 `course` 모듈이 소유한다.
- 클래스 단계가 학습 콘텐츠와 연결될 수 있지만, 콘텐츠 원본은 `practicecontent` 모듈이 소유한다.

## Home 컴포넌트

### 책임

- 홈 대시보드 조회
- 오늘의 학습 상태 조회
- 최근 학습 조회
- 개인화 추천 조회

### 주요 계약

- `HomeReader`: 홈 화면에 필요한 집계/조회 데이터 계약

### 소유 경계

- 홈은 여러 모듈의 데이터를 조합해 화면용 read model을 제공한다.
- 홈 전용 entity를 만들기보다 `domain/model` 기반 조회 모델을 우선 사용한다.
- 원본 데이터 소유권은 `training`, `course`, `practicecontent`, `analysis`, `onboarding` 모듈에 둔다.

## Common 컴포넌트

| Package | Responsibility |
| --- | --- |
| `common/config` | Web, Security, JPA, Jackson 설정 |
| `common/response` | `ApiResponse`, `PageResponse` |
| `common/exception` | 공통 예외, `ErrorCode`, 전역 예외 처리 |
| `common/security` | JWT 인증 필터와 로그인 사용자 컨텍스트 |
| `common/storage` | 저장소 공통 추상화 |
| `common/util` | 문자열, 날짜/시간 등 범용 유틸리티 |

`common`은 특정 기능의 비즈니스 규칙을 소유하지 않는다.

## 컴포넌트 간 협력 흐름

### 로그인

```text
AuthController
-> AuthService / SocialLoginService
-> TokenProvider, PasswordHasher, SocialOAuthProvider
-> UserReader/UserWriter, RefreshTokenReader/Writer
```

### 학습 세션과 녹음

```text
TrainingSessionController / VoiceRecordingController
-> TrainingSessionService / VoiceRecordingService / RecordingUploadService
-> TrainingSessionReader/Writer, VoiceRecordingReader/Writer
-> Training infrastructure, storage provider
```

### 분석 요청과 결과 조회

```text
TrainingAnalysisController
-> TrainingAnalysisRequestService
-> AnalysisJobPublisher
-> Analysis provider / Analysis infrastructure
-> AnalysisController
-> AnalysisService / AnalysisSegmentService
```

### 홈 화면

```text
HomeController
-> HomeService / RecentLearningService / RecommendationService
-> HomeReader
-> training, course, practicecontent, analysis 데이터 조회
```

## 변경 시 체크리스트

- 새 기능이 기존 모듈 책임 안에 들어가는가?
- 모듈 간 직접 entity 공유가 필요한지, model/port 계약으로 충분한지 확인했는가?
- application service가 infrastructure 구현체가 아니라 port에 의존하는가?
- `common`에 올리는 코드가 특정 기능 규칙을 포함하지 않는가?
- public API 변경이 있다면 `docs/api` 문서와 DTO가 함께 갱신되었는가?
- entity 또는 repository 변경이 있다면 DB 문서와 migration 필요 여부를 확인했는가?
