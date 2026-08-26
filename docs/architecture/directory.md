# 디렉토리 문서 - voice

이 문서는 voice 프로젝트의 현재 디렉토리 구조와 각 경로의 책임을 설명한다.
새 파일이나 패키지를 추가할 때는 이 문서와 `docs/architecture/architecture.md`, `docs/architecture/component.md`를 함께 참고한다.

## 작성 기준

- 기능 경계는 `src/main/java/org/example/voice/<feature>` 패키지로 나눈다.
- 기능 패키지는 기본적으로 `controller`, `application`, `domain`, `infrastructure` 계층을 따른다.
- API DTO는 각 기능의 `controller/dto`에 둔다.
- 도메인 내부는 `entity`, `model`, `port`, `type`으로 책임을 나눈다.
- 공통 기반 코드는 `common` 아래에 둔다.
- 테스트 코드는 운영 코드의 패키지 구조를 최대한 따른다.

## 최상위 구조

| Path | Type | Responsibility | Notes |
| --- | --- | --- | --- |
| `src/main/java/org/example/voice` | directory | Spring Boot 운영 코드 루트 | 기능 모듈과 `common` 패키지를 둔다. |
| `src/main/resources` | directory | 설정, migration, 런타임 리소스 | `application.yaml`, Flyway migration 등을 둔다. |
| `src/test/java/org/example/voice` | directory | 테스트 코드 루트 | 운영 코드 패키지 구조를 따른다. |
| `src/test/resources` | directory | 테스트 전용 설정 | 테스트용 `application.yaml` 등을 둔다. |
| `docs/api` | directory | API 명세 문서 | endpoint 목록과 request/response 명세를 둔다. |
| `docs/architecture` | directory | 아키텍처 문서 | 구조, 컴포넌트, 디렉토리, 상태, 흐름, 배포 문서를 둔다. |
| `docs/database` | directory | DB 문서 | schema 문서를 둔다. |
| `docs/troubleshooting` | directory | 장애 분석과 해결 기록 | 운영 중 발생한 오류의 로그, 원인 코드, 해결 방식, 배포 조치를 기록한다. |
| `.codex/ai_rule_developer` | directory | AI 개발 규칙 문서 | API, 아키텍처, 코드 스타일 등 AI 작업 기준을 둔다. |

## 운영 코드 루트

```text
src/main/java/org/example/voice
+-- VoiceApplication.java
+-- analysis
+-- auth
+-- common
+-- course
+-- home
+-- onboarding
+-- practicecontent
+-- training
+-- user
```

| Path | Responsibility |
| --- | --- |
| `VoiceApplication.java` | Spring Boot application entry point |
| `analysis` | 음성 분석 결과, 세그먼트 분석, 피드백 재생성 |
| `auth` | 회원가입, 로그인, 소셜 로그인, JWT, refresh token |
| `common` | 공통 설정, 응답, 예외, 보안, 저장소, 유틸리티 |
| `course` | 클래스, 클래스 단계, 사용자 클래스 진도 |
| `home` | 홈 대시보드, 최근 학습, 추천 |
| `onboarding` | 온보딩 프로필과 설문 응답 |
| `practicecontent` | 학습 콘텐츠와 기준 음성 |
| `training` | 학습 세션, 녹음, 분석 요청 흐름 |
| `user` | 사용자 프로필, 역할, 상태 |

## 기능 패키지 표준 구조

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

| Path | Type | Responsibility | Notes |
| --- | --- | --- | --- |
| `<feature>/controller` | directory | HTTP API endpoint | `*Controller` 클래스를 둔다. |
| `<feature>/controller/dto` | directory | API request/response/query DTO | 모든 DTO 이름은 `Dto`로 끝낸다. |
| `<feature>/application` | directory | 유스케이스와 트랜잭션 경계 | `*Service` 클래스를 둔다. |
| `<feature>/domain/entity` | directory | 저장 대상 도메인 entity | JPA entity를 둔다. |
| `<feature>/domain/model` | directory | 조회/집계/application result model | API DTO가 아닌 내부 반환 데이터를 둔다. |
| `<feature>/domain/port` | directory | 저장소/외부 기능 계약 | `*Reader`, `*Writer`, `*Provider`, `*Publisher` 등을 둔다. |
| `<feature>/domain/type` | directory | enum과 상태 타입 | 도메인 상태, 분류 값을 둔다. |
| `<feature>/infrastructure` | directory | port 구현체, JPA repository, 외부 client | `*Impl`, `*JpaRepository` 등을 둔다. |
| `<feature>/exception` | directory | 기능별 비즈니스 예외 | `*Exception` 클래스를 둔다. |

기능에 필요 없는 하위 패키지는 만들지 않는다. 예를 들어 entity가 없는 read model 모듈은 `domain/entity` 없이 `domain/model`, `domain/port`만 둘 수 있다.

## 현재 기능별 구조

| Module | Current Subdirectories | Notes |
| --- | --- | --- |
| `analysis` | `application`, `controller`, `domain`, `infrastructure`, `provider` | 분석 provider 인터페이스가 별도 패키지로 존재한다. |
| `auth` | `application`, `controller`, `domain`, `exception`, `infrastructure` | OAuth/JWT 구현체는 infrastructure에 둔다. |
| `course` | `application`, `controller`, `domain`, `exception`, `infrastructure` | 클래스와 사용자 진도를 함께 다룬다. |
| `home` | `application`, `controller`, `domain`, `exception`, `infrastructure` | 여러 모듈 데이터를 조합하는 read model 중심이다. |
| `onboarding` | `application`, `controller`, `domain`, `exception`, `infrastructure` | 온보딩 프로필과 설문 응답을 다룬다. |
| `practicecontent` | `application`, `controller`, `domain`, `exception`, `infrastructure` | 학습 콘텐츠와 기준 음성을 다룬다. |
| `training` | `application`, `controller`, `domain`, `exception`, `infrastructure` | 학습 세션, 녹음, 분석 요청 흐름을 다룬다. |
| `user` | `application`, `controller`, `domain`, `exception`, `infrastructure` | 사용자 기본 정보와 상태를 다룬다. |

## Common 구조

```text
common
+-- cache
+-- config
+-- enums
+-- exception
+-- response
+-- security
+-- storage
+-- util
```

| Path | Responsibility | Examples |
| --- | --- | --- |
| `common/cache` | 여러 모듈에서 재사용하는 캐시 기반 유틸리티와 TTL provider 계약 | `CacheKey`, `CacheTtlProvider` |
| `common/config` | 전역 Spring 설정 | `SecurityConfig`, `WebConfig`, `JpaConfig`, `JacksonConfig`, `OpenApiConfig`, `CacheConfig` |
| `common/enums` | 여러 모듈에서 공유되는 enum | 현재 placeholder만 존재한다. |
| `common/exception` | 공통 예외와 전역 예외 처리 | `ErrorCode`, `BaseException`, `BusinessException`, `GlobalExceptionHandler` |
| `common/response` | 공통 API 응답 모델 | `ApiResponse`, `PageResponse` |
| `common/security` | 인증 필터와 로그인 사용자 컨텍스트 | `JwtAuthenticationFilter`, `LoginUser` |
| `common/storage` | 저장소 공통 추상화 | `StorageClient`, `StorageProperties` |
| `common/util` | 범용 유틸리티 | `DateTimeUtils`, `StringUtils` |

`common`에는 특정 기능만 사용하는 비즈니스 규칙을 두지 않는다.

## 기능별 Infrastructure Cache 구조

기능별 Redis cache name과 key 생성 규칙은 해당 기능의 `infrastructure/cache`에 둔다.

| Path | Responsibility | Examples |
| --- | --- | --- |
| `practicecontent/infrastructure/cache` | 학습 콘텐츠 조회 캐시 이름, key 생성 규칙, TTL 제공 구현 | `PracticeContentCacheNames`, `PracticeContentCacheKeys`, `PracticeContentCacheTtlProvider` |
| `course/infrastructure/cache` | 클래스 조회 캐시 이름, key 생성 규칙, TTL 제공 구현 | `CourseCacheNames`, `CourseCacheKeys`, `CourseCacheTtlProvider` |
| `analysis/infrastructure/cache` | 분석 결과 조회 캐시 이름, key 생성 규칙, TTL 제공 구현 | `AnalysisCacheNames`, `AnalysisCacheKeys`, `AnalysisCacheTtlProvider` |
| `mypage/infrastructure/cache` | 마이페이지 학습 기록/통계 조회 캐시 이름, key 생성 규칙, TTL 제공 구현 | `MyPageCacheNames`, `MyPageCacheKeys`, `MyPageCacheTtlProvider` |
| `home/infrastructure/cache` | 홈 화면 조회 캐시 이름, key 생성 규칙, TTL 제공 구현 | `HomeCacheNames`, `HomeCacheKeys`, `HomeCacheTtlProvider` |

## Resources 구조

```text
src/main/resources
+-- application.yaml
+-- db
    +-- migration
```

| Path | Responsibility |
| --- | --- |
| `src/main/resources/application.yaml` | 기본 애플리케이션 설정 |
| `src/main/resources/db/migration` | Flyway migration SQL |

로컬 환경 변수와 비밀값은 Git에 올리지 않는다. 로컬 설정은 `.gitignore`에 등록된 `application-local.yaml`, `application-secret.yaml`, `.env` 등을 사용한다.

## Test 구조

```text
src/test/java/org/example/voice
+-- VoiceApplicationTests.java
+-- auth
+-- common
```

| Path | Responsibility |
| --- | --- |
| `src/test/java/org/example/voice` | 테스트 코드 루트 |
| `src/test/java/org/example/voice/<feature>` | 기능별 테스트 |
| `src/test/resources/application.yaml` | 테스트 전용 설정 |

테스트는 운영 코드와 같은 패키지 구조를 우선 따른다.

## Docs 구조

```text
docs
+-- api
+-- architecture
+-- database
```

| Path | Responsibility |
| --- | --- |
| `docs/api/endpoints.md` | API endpoint 목록 |
| `docs/api/specification.md` | API request/response 상세 명세 |
| `docs/architecture/architecture.md` | 전체 아키텍처 개요 |
| `docs/architecture/component.md` | 컴포넌트와 모듈 책임 |
| `docs/architecture/deployment.md` | 배포 환경과 운영 보조 서비스 접근 방식 |
| `docs/architecture/directory.md` | 디렉토리 구조와 배치 규칙 |
| `docs/architecture/flow.md` | 주요 기능 흐름 |
| `docs/architecture/state.md` | 상태 모델과 상태 전이 |
| `docs/database/schema.md` | DB schema 문서 |
| `docs/troubleshooting` | 장애 분석, 원인 코드, 해결 방식, 배포 조치 기록 |

## 파일 추가 체크리스트

- 새 파일이 기존 기능 모듈에 속하는가, 새 모듈이 필요한가?
- Controller DTO를 `controller/dto` 밖에 만들지 않았는가?
- Entity, model, port, type을 `domain` 하위 책임에 맞게 배치했는가?
- JPA repository와 port 구현체를 `infrastructure`에 두었는가?
- 기능별 예외를 해당 기능의 `exception`에 두었는가?
- 공통 코드가 실제로 여러 모듈에서 공유되는가?
- 새 설정이나 비밀값 파일이 `.gitignore` 규칙을 따르는가?
- 구조 변경이 있으면 README와 `docs/architecture/*` 문서를 함께 갱신했는가?
