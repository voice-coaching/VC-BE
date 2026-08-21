# 아키텍처 문서 - voice

## 요약

voice는 발음 및 억양 학습을 위한 Spring Boot 기반 백엔드 프로젝트이다.
사용자는 학습 콘텐츠를 선택해 녹음을 업로드하고, 음성 분석 결과와 피드백을 기반으로 학습을 이어간다.

프로젝트는 기능 단위 패키지를 기준으로 나누며, 각 기능 내부는 `controller`, `application`, `domain`, `infrastructure` 계층으로 분리한다.

## 기본 정보

- Language: Java 21
- Framework: Spring Boot 4.1.0
- Build: Gradle
- Database: PostgreSQL
- Persistence: Spring Data JPA
- Migration: Flyway
- Authentication: JWT Access Token / Refresh Token
- API Documentation: Springdoc OpenAPI
- External Integration: OAuth, storage presigned URL, STT/AI feedback provider
- Cache: Redis Cache

## 주요 기능

- 일반 회원가입 및 로그인
- SNS 로그인
- JWT 인증 및 토큰 갱신
- 사용자 프로필 조회/수정/탈퇴
- 온보딩 설문 저장 및 조회
- 학습 콘텐츠 및 기준 음성 조회
- 학습 세션 생성과 상태 관리
- 음성 녹음 업로드, 등록, 최종 녹음 선택
- 음성 분석 요청과 분석 결과 조회
- 클래스 목록/상세/단계/진도/완료 관리
- 홈 대시보드, 개인화 추천, 최근 학습 조회

## 패키지 구조

기본 루트 패키지는 `org.example.voice`이다.

```text
src/main/java/org/example/voice
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

각 기능 패키지는 아래 계층을 기본으로 한다.

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

기능에 필요 없는 하위 패키지는 만들지 않는다. 여러 기능에서 공유되는 기반 코드는 `common` 패키지에 둔다.

## 모듈 책임

| Module | Responsibility |
| --- | --- |
| `auth` | 일반 로그인, 소셜 로그인, JWT 발급/검증, refresh token, social account |
| `user` | 사용자 프로필, 역할, 상태, 내 정보 조회/수정/탈퇴 |
| `onboarding` | 온보딩 프로필, 설문 응답, 학습 목표 |
| `practicecontent` | 학습 콘텐츠, 기준 음성, 다음 콘텐츠, 콘텐츠 기반 추천 |
| `training` | 학습 세션, 녹음 업로드, 녹음 선택, 분석 요청 흐름 |
| `analysis` | STT 결과, 음성 분석 결과, 세그먼트 분석, AI 피드백 재생성 |
| `course` | 클래스, 클래스 단계, 사용자 클래스 진도 |
| `home` | 홈 대시보드, 개인화 추천, 최근 학습 |
| `common` | 공통 설정, 응답, 예외, 보안, 스토리지, 유틸리티 |

## 계층 책임

### Controller

- HTTP endpoint를 정의한다.
- request body, path variable, query parameter를 DTO로 받는다.
- 인증 사용자 컨텍스트를 application layer에 전달한다.
- application 결과를 response DTO로 변환한다.
- 정상 응답은 `ApiResponse<T>`로 감싼다.
- 비즈니스 로직과 상태 전이 판단을 직접 수행하지 않는다.

### Application

- 하나의 유스케이스를 실행하는 중심 계층이다.
- 트랜잭션 경계를 관리한다.
- domain entity, domain model, domain port를 조합한다.
- 비즈니스 규칙, 상태 전이 가능 여부, 소유권 검증을 처리한다.
- Controller DTO나 HTTP 세부사항에 의존하지 않는 것을 목표로 한다.

### Domain

- 핵심 도메인 상태와 계약을 표현한다.
- `entity`는 저장되는 도메인 상태를 가진다.
- `model`은 조회/집계 결과 또는 application 반환 데이터를 표현한다.
- `port`는 application이 필요로 하는 저장소/외부 기능 인터페이스를 정의한다.
- `type`은 상태와 분류 enum을 정의한다.
- domain은 controller와 infrastructure 구현체에 의존하지 않는다.

### Infrastructure

- domain port를 구현한다.
- Spring Data JPA repository와 DB 접근 코드를 둔다.
- OAuth, JWT, storage, STT, AI feedback 등 외부 시스템 연동 구현을 둔다.
- 외부 응답을 내부 domain model 또는 application에서 사용할 수 있는 형태로 변환한다.

### Common

`common`은 여러 기능에서 공유되는 기반 코드만 포함한다.

```text
common
+-- cache
+-- config
+-- response
+-- exception
+-- enums
+-- security
+-- storage
+-- util
```

- `cache`: 캐시 key 조립 등 여러 모듈에서 재사용하는 캐시 기반 유틸리티
- `config`: Web, Security, JPA, Jackson, Redis Cache 등 전역 설정
- `response`: `ApiResponse`, `PageResponse`
- `exception`: `ErrorCode`, `BaseException`, `BusinessException`, `GlobalExceptionHandler`
- `security`: JWT 인증 필터, 로그인 사용자 컨텍스트
- `storage`: 저장소 또는 presigned URL 관련 공통 추상화
- `util`: 도메인 의미가 없는 공통 유틸리티

## 의존 방향

권장 의존 흐름은 다음과 같다.

```text
Controller -> Application -> Domain Port -> Infrastructure -> DB / External System
```

- Controller는 application service를 호출한다.
- Application은 domain entity/model/type/port를 사용한다.
- Infrastructure는 domain port를 구현한다.
- Domain은 Spring Web, Controller DTO, JPA repository 구현체를 알지 않는다.
- 순환 의존이 생기면 패키지 책임을 다시 나눈다.

## 데이터 흐름

일반적인 조회 흐름:

```text
Client
-> Controller
-> Application Service
-> Domain Port
-> Infrastructure Reader / Redis Cache
-> JPA Repository
-> Database
```

조회 결과 캐시는 infrastructure reader 구현체의 `@Cacheable` 경계에서 적용한다. Application service와 Controller는 Redis 세부사항에 의존하지 않는다.

일반적인 명령 흐름:

```text
Client
-> Controller
-> Application Service
-> Domain Entity / Domain Port
-> Infrastructure Writer
-> Database or External System
```

응답 변환 흐름:

```text
Domain Entity / Domain Model
-> Application Result
-> ResponseDto.from(...)
-> ApiResponse<T>
```

## 인증 경계

- 공개 API는 회원가입, 로그인, 소셜 로그인, 토큰 갱신처럼 명확한 경우에만 허용한다.
- 그 외 API는 기본적으로 `Authorization: Bearer <accessToken>`을 요구한다.
- 인증은 security 계층에서 처리하고, 권한 및 소유권 검증은 application layer에서 명시적으로 수행한다.
- 현재 임시 구현에 하드코딩된 `userId`가 있더라도 최종 구조는 로그인 사용자 컨텍스트를 기준으로 한다.

## 외부 연동 경계

- OAuth 연동과 JWT 발급/검증은 `auth/infrastructure`에 둔다.
- STT, AI feedback, voice analysis provider는 `analysis`의 provider 또는 infrastructure 경계에 둔다.
- 녹음 파일 업로드와 재생 URL 발급은 `training/infrastructure` 또는 `common/storage` 책임을 명확히 구분한다.
- 외부 provider DTO를 공개 API DTO로 그대로 사용하지 않는다.
- timeout, retry, fallback, failure mapping이 필요한 경우 application 흐름과 infrastructure 구현을 분리한다.

## 저장소 경계

- JPA repository는 `infrastructure`에 둔다.
- Application service가 JPA repository에 직접 의존하지 않도록 domain port를 우선 사용한다.
- 읽기와 쓰기 책임이 커지면 `Reader`와 `Writer` port로 분리한다.
- Entity 변경 시 DB schema 문서와 migration 필요 여부를 함께 검토한다.

## 캐시 경계

- Redis Cache는 반복 조회가 많고 변경 빈도가 낮은 read model에 우선 적용한다.
- 캐시 설정과 공통 key 유틸리티는 `common/config`, `common/cache`에 둔다.
- 기능별 TTL은 `common/cache/CacheTtlProvider` 구현체로 제공한다.
- 기능별 cache name과 key 생성 규칙은 각 기능의 `infrastructure/cache`에 둔다.
- 캐시 적용은 domain port 구현체인 infrastructure reader에서 수행한다.
- 존재하지 않는 단일 리소스나 다음 콘텐츠 없음 같은 negative lookup은 캐시하지 않는다.
- 학습 콘텐츠 목록, 상세, 다음 콘텐츠, 콘텐츠 기반 추천, 기준 음성 목록은 TTL 기반으로 만료한다.
- 클래스 목록, 상세, 단계 목록은 사용자별 진도 정보가 포함되므로 cache key에 `userId`를 포함한다.
- 클래스 시작, 진도 수정, 완료 처리 시 클래스 목록 캐시는 전체 무효화하고 상세/단계 캐시는 해당 `userId`와 `courseId` 기준으로 무효화한다.
- 분석 결과 상세, 학습 세션 기준 분석 결과, 분석 세그먼트 목록은 완료된 분석 결과만 Redis Cache에 저장한다.
- 분석 진행 중이거나 실패한 결과는 캐시하지 않으며, 피드백 재생성 시 분석 상세 캐시를 무효화한다.
- 마이페이지 학습 기록 목록/상세, 학습 통계, 강점·약점 집계, 점수 추이, 약점 기반 추천은 사용자별 조회 조건을 cache key에 포함해 Redis Cache에 저장한다.
- 학습 세션 완료/취소 또는 마이페이지 학습 기록 삭제는 마이페이지 조회 결과의 원천 데이터를 변경하므로 마이페이지 캐시를 전체 무효화한다.

## 아키텍처 결정

| Date | Decision | Reason | Impact |
| --- | --- | --- | --- |
| 2026-08-12 | 기능 단위 패키지와 4계층 구조를 기본 구조로 사용한다. | 도메인별 책임을 분리하고 API, 유스케이스, 도메인, 인프라 변경 범위를 줄이기 위해서이다. | 신규 기능은 `<feature>/controller`, `application`, `domain`, `infrastructure` 구조를 따른다. |
| 2026-08-12 | API DTO는 `controller/dto`, 조회/집계 모델은 `domain/model`에 둔다. | 외부 API 계약과 내부 도메인 데이터를 분리하기 위해서이다. | Entity를 API 응답으로 직접 노출하지 않고 DTO 변환을 명시한다. |
| 2026-08-12 | 저장소와 외부 연동은 domain port와 infrastructure 구현으로 분리한다. | application layer가 DB/JPA/외부 provider 세부사항에 강하게 결합되는 것을 줄이기 위해서이다. | Reader/Writer/Provider port와 `*Impl`, `*JpaRepository` 구현을 사용한다. |
| 2026-08-20 | Redis Cache는 infrastructure reader 경계에서 적용한다. | 조회 성능을 개선하면서 Controller와 application service가 Redis 세부사항에 의존하지 않도록 하기 위해서이다. | 공통 캐시 설정은 `common`, 기능별 캐시 이름과 key/TTL 규칙은 각 기능의 `infrastructure/cache`에 둔다. |
