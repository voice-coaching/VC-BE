# Architecture Rules - voice

이 문서는 voice 백엔드의 패키지 구조, 계층 책임, 의존 방향을 정의한다.
프로젝트는 발음 및 억양 학습을 위한 Spring Boot 기반 백엔드이며, Java 21, Spring Boot, Spring Data JPA, PostgreSQL, JWT 인증을 사용한다.

## 기본 원칙

- 기능은 도메인 또는 유스케이스 단위의 최상위 패키지로 나눈다.
- 각 기능 패키지는 기본적으로 `controller`, `application`, `domain`, `infrastructure` 계층을 가진다.
- 공통 설정, 공통 응답, 공통 예외, 보안, 유틸리티는 `common` 패키지에 둔다.
- API 계약 객체, 도메인 객체, 영속성 접근 구현을 섞지 않는다.
- 하위 계층은 상위 계층의 HTTP, DTO, Spring Web 세부사항에 의존하지 않는다.
- 새 구조를 만들 때는 기존 패키지와 이름 규칙을 우선 따른다.

## 최상위 패키지

기본 루트는 `src/main/java/org/example/voice`이다.

```text
org.example.voice
+-- VoiceApplication.java
+-- auth
+-- user
+-- onboarding
+-- practicecontent
+-- training
+-- analysis
+-- course
+-- home
+-- common
```

각 패키지의 역할은 다음과 같다.

- `auth`: 회원가입, 로그인, 소셜 로그인, JWT, refresh token, social account
- `user`: 사용자 프로필, 상태, 역할, 사용자 조회/수정
- `onboarding`: 온보딩 프로필, 설문 응답, 학습 목표
- `practicecontent`: 학습 콘텐츠, 기준 음성, 콘텐츠 추천/다음 콘텐츠
- `training`: 학습 세션, 녹음 업로드, 녹음 선택, 분석 요청 흐름
- `analysis`: STT/음성 분석 결과, 세그먼트 분석, 피드백 재생성
- `course`: 클래스, 클래스 단계, 사용자 클래스 진도
- `home`: 홈 대시보드, 최근 학습, 개인화 추천 조회
- `common`: 전역 설정, 응답 포맷, 예외 처리, 보안, 스토리지, 유틸리티

## 기능 패키지 구조

도메인 기능 패키지는 아래 구조를 기본으로 한다.

```text
<feature>
+-- controller
|   +-- dto
+-- application
+-- domain
|   +-- entity
|   +-- model
|   +-- port
|   +-- type
+-- infrastructure
+-- exception
```

- `controller`: HTTP 요청/응답 처리, 인증 컨텍스트 연결, DTO 변환
- `controller/dto`: API request/response/query DTO
- `application`: 유스케이스 조합, 트랜잭션 경계, 비즈니스 흐름 제어
- `domain/entity`: JPA entity 또는 저장 대상 도메인 상태
- `domain/model`: 조회 모델, 집계 결과, application 반환 데이터
- `domain/port`: application/domain이 필요로 하는 저장소 또는 외부 기능 인터페이스
- `domain/type`: enum, 상태, 분류 타입
- `infrastructure`: JPA repository, port 구현체, 외부 API/client 구현체
- `exception`: 기능별 비즈니스 예외

기능에 필요 없는 하위 패키지는 만들지 않는다. 여러 기능에서 공유하는 것은 먼저 `common`에 둘 만큼 일반적인지 확인한다.

## Common 패키지

`common`은 여러 기능에서 반복 사용되는 기반 코드만 가진다.

```text
common
+-- config
+-- enums
+-- exception
+-- response
+-- security
+-- storage
+-- util
```

- `config`: Spring/JPA/Jackson/Security/Web 설정
- `response`: 공통 API 응답과 페이지 응답
- `exception`: 공통 예외, `ErrorCode`, `GlobalExceptionHandler`
- `security`: JWT 인증 필터, 로그인 사용자 컨텍스트
- `storage`: 파일 저장소와 presigned URL 같은 저장소 공통 추상화
- `util`: 도메인 의미가 없는 작은 유틸리티

특정 기능에만 필요한 예외, 타입, provider, repository 구현은 `common`에 두지 않는다.

## 계층 책임

### Controller

- HTTP method, path, path variable, query parameter, request body를 선언한다.
- 요청 DTO를 받고 응답 DTO를 반환한다.
- `ApiResponse<T>`로 정상 응답을 감싼다.
- 도메인 entity를 직접 반환하지 않는다.
- 복잡한 비즈니스 판단을 하지 않고 application service에 위임한다.
- 인증 사용자 정보는 명시적으로 application layer에 전달한다.

### Application

- 하나의 유스케이스를 실행하는 중심 계층이다.
- 트랜잭션 경계를 둔다.
- domain entity, domain model, port를 조합한다.
- 비즈니스 규칙과 상태 전이 가능 여부를 판단한다.
- Controller DTO나 Spring Web 타입에 의존하지 않는다.
- 외부 시스템 직접 호출 대신 domain port 또는 provider 인터페이스를 통해 접근한다.

### Domain

- 프로젝트의 핵심 상태, 타입, 계약을 표현한다.
- `entity`는 저장되는 상태와 도메인 규칙을 가진다.
- `model`은 API 응답 DTO가 아닌 application 결과 또는 조회용 데이터 구조다.
- `port`는 application이 필요로 하는 기능의 인터페이스다.
- `type`은 상태와 분류 enum을 둔다.
- HTTP, Controller DTO, JPA repository 구현체에 의존하지 않는다.

### Infrastructure

- domain port를 구현한다.
- Spring Data JPA repository를 둔다.
- OAuth, JWT, STT, AI feedback, storage 같은 외부 시스템 접근 구현을 둔다.
- 외부 API 응답을 내부 domain model 또는 DTO로 변환해 상위 계층에 전달한다.
- infrastructure 구현체가 application 흐름을 주도하지 않는다.

## 의존 방향

권장 흐름은 다음과 같다.

```text
Controller -> Application -> Domain Port -> Infrastructure -> DB / External System
```

- Controller는 application service를 호출한다.
- Application은 domain entity/model/type/port에 의존한다.
- Infrastructure는 domain port를 구현하고 DB 또는 외부 시스템에 접근한다.
- Domain은 controller, application 구현체, infrastructure 구현체에 의존하지 않는다.
- 순환 의존이 생기면 패키지 책임을 다시 나눈다.

## DTO와 모델 배치

- 모든 API DTO는 각 기능의 `controller/dto` 패키지에 둔다.
- DTO 클래스명은 반드시 `Dto`로 끝낸다.
- Request DTO와 Response DTO는 분리한다.
- 목록/검색 조건 DTO는 `*ConditionDto` 이름을 사용할 수 있다.
- Application 반환 전용 데이터는 `domain/model`에 둔다.
- Entity를 Controller 응답으로 직접 사용하지 않는다.

## Repository와 Port 규칙

- JPA repository 인터페이스는 `infrastructure`에 둔다.
  - 예: `CourseJpaRepository`, `TrainingSessionJpaRepository`
- Application이 직접 JPA repository에 의존하지 않도록 domain port를 우선 사용한다.
  - 예: `CourseReader`, `CourseProgressWriter`
- Port 구현체는 `infrastructure`에 두고 `*Impl` 이름을 사용한다.
  - 예: `CourseReaderImpl`, `VoiceRecordingWriterImpl`
- 읽기와 쓰기 책임이 커지면 `Reader`와 `Writer` port로 분리한다.

## 외부 연동 구조

- 외부 시스템 접근은 `infrastructure`, 기능별 `provider`, 또는 `common/storage`에 둔다.
- STT, AI feedback, voice analysis 등 분석 관련 외부 기능은 `analysis` 패키지의 provider/port 경계를 우선 사용한다.
- OAuth, JWT처럼 인증 흐름에 속한 구현은 `auth/infrastructure`에 둔다.
- Presigned URL과 파일 저장소 연동은 `training/infrastructure` 또는 `common/storage`의 책임을 명확히 나눈다.
- 외부 provider의 request/response 객체를 공개 API DTO로 재사용하지 않는다.

## 예외 처리

- 기능별 예외는 각 기능의 `exception` 패키지에 둔다.
- 공통 예외와 전역 핸들러는 `common/exception`에 둔다.
- 비즈니스 예외는 `BusinessException`과 `ErrorCode`를 통해 상태 코드와 메시지를 일관되게 처리한다.
- Controller에서 try-catch로 에러 응답을 직접 만들지 않는다.

## 새 기능 추가 체크리스트

- 최상위 기능 패키지가 이미 있는가? 있으면 그 안에 추가한다.
- 새 기능 패키지가 필요하면 `controller/application/domain/infrastructure` 구조를 기준으로 만든다.
- API DTO는 `controller/dto`에 있고 이름이 `Dto`로 끝나는가?
- 저장 대상은 `domain/entity`, 조회/집계 결과는 `domain/model`에 분리했는가?
- application이 infrastructure 구현체가 아니라 domain port에 의존하는가?
- 공통 코드로 올리기 전에 최소 두 개 이상의 기능에서 실제로 공유되는지 확인했는가?
- README와 `docs/architecture/*` 문서 갱신이 필요한 변경인가?
