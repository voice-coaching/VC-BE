# Service Layer Rules - voice

이 문서는 voice 백엔드의 `application` 계층, 즉 service layer 작성 규칙을 정의한다.
Service layer는 HTTP 세부사항과 infrastructure 구현을 직접 드러내지 않고, 하나의 유스케이스를 실행하는 중심 계층이다.

## 기본 원칙

- Service class는 각 기능 모듈의 `application` 패키지에 둔다.
- Service는 `@Service`와 `@RequiredArgsConstructor`를 기본으로 사용한다.
- 의존성은 `private final` 필드와 생성자 주입으로 받는다.
- Service method는 하나의 유스케이스를 표현한다.
- 트랜잭션 경계는 service layer에 둔다.
- DB 접근과 외부 시스템 접근은 domain port 또는 provider 계약을 통해 수행한다.
- Controller, HTTP response, infrastructure 구현 세부사항을 service 안으로 끌고 오지 않는다.

## 위치와 이름

| Type | Location | Naming | Examples |
| --- | --- | --- | --- |
| Application service | `<feature>/application` | `*Service` | `AuthService`, `TrainingSessionService`, `CourseProgressService` |
| Read service | `<feature>/application` | 조회 유스케이스 이름 + `Service` | `RecentLearningService`, `RecommendationService` |
| Command service | `<feature>/application` | 명령 유스케이스 이름 + `Service` | `RecordingUploadService`, `TrainingAnalysisRequestService` |
| Cross-use-case coordinator | `<feature>/application` | 대표 도메인 이름 + `Service` | `HomeService`, `SocialLoginService` |

Service 이름은 담당 유스케이스 또는 도메인 흐름이 드러나야 한다. 의미가 넓은 `Manager`, `Processor`, `Handler` 이름은 피한다.

## Service 책임

Service는 다음 책임을 가진다.

- 입력값과 필수 도메인 조건 검증
- 로그인 사용자 기준의 소유권 또는 권한 검증
- domain entity/model/port 조합
- 상태 전이 가능 여부 판단
- 트랜잭션 경계 설정
- 외부 작업 발행 또는 provider 호출 조율
- domain model 또는 application result 반환

Service가 직접 맡지 않는 책임은 다음과 같다.

- HTTP path, status code, header 결정
- `ApiResponse<T>` 생성
- JPA query 세부 구현
- 외부 API request/response raw DTO 노출
- 화면 전용 표현 로직
- 특정 infrastructure 구현체의 내부 처리 방식

## 의존 방향

권장 의존 흐름은 다음과 같다.

```text
Controller -> Application Service -> Domain Port -> Infrastructure -> DB / External System
```

Service는 다음 대상에 의존할 수 있다.

- 같은 기능 또는 필요한 다른 기능의 domain port
- domain entity, domain model, domain type
- 다른 application service가 명확한 유스케이스 협력자인 경우
- 공통 예외와 `ErrorCode`
- 시간, 문자열 등 일반 Java/Spring 기반 유틸리티

Service는 다음 대상에 직접 의존하지 않는다.

- Spring Data JPA repository
- infrastructure `*Impl` 구현체
- Controller class
- `ApiResponse`
- Servlet request/response
- 외부 provider의 raw response DTO

## Transaction 규칙

- 쓰기 유스케이스에는 `@Transactional`을 붙인다.
- 조회 유스케이스에는 `@Transactional(readOnly = true)`를 우선 사용한다.
- 여러 저장소 변경과 외부 작업 발행이 하나의 유스케이스에 묶이면 service method가 트랜잭션 경계를 가진다.
- 단순 계산 helper에는 트랜잭션을 붙이지 않는다.
- 외부 API 호출이 긴 작업이면 DB transaction 안에서 오래 붙잡지 않도록 구조를 검토한다.
- 비동기 분석 요청처럼 DB record 생성 후 job 발행이 필요한 경우, DB에 추적 가능한 상태를 먼저 만든 뒤 publisher를 호출한다.

예시:

```java
@Transactional
public AnalysisRequestData requestAnalysis(Long sessionId, Long userId) {
    trainingSessionService.assertSessionExists(sessionId, userId);
    Long recordingId = voiceRecordingReader.findSelectedRecordingId(sessionId, userId)
            .orElseThrow(() -> new BaseException(ErrorCode.SELECTED_RECORDING_NOT_FOUND));

    AnalysisRequestData result = trainingAnalysisWriter.createPending(recordingId);
    trainingSessionWriter.updateStatus(sessionId, TrainingSessionStatus.ANALYZING);
    analysisJobPublisher.publish(result.analysisId(), sessionId, recordingId);
    return result;
}
```

## 입력과 반환 규칙

- Controller에서 받은 path variable, query DTO, request DTO는 service 호출 인자로 전달할 수 있다.
- 장기적으로 service는 Controller DTO보다 domain model 또는 command/query 객체에 의존하는 것을 우선한다.
- 현재 코드에 남아 있는 Controller DTO 반환은 점진적으로 `domain/model` 반환 후 `ResponseDto.from(...)` 변환으로 정리한다.
- Service 반환값은 다음 중 하나를 우선한다.
  - domain model
  - application result record
  - primitive 또는 enum 기반 단순 결과
  - Optional은 reader/port에서 주로 사용하고 service는 필요 시 예외로 변환한다.
- Entity를 Controller까지 그대로 반환하지 않는다.

## Validation 규칙

Service는 유스케이스 실행 전 필요한 비즈니스 조건을 검증한다.

- 필수 입력값 존재 여부
- 리소스 존재 여부
- 사용자 소유권
- 현재 상태에서 가능한 명령인지 여부
- 중복 요청 여부
- retry 제한
- 외부 작업 요청 가능 조건

예시 검증 흐름:

```text
입력 필수값 검증
-> 대상 리소스 존재 확인
-> 사용자 소유권 확인
-> 상태 전이 가능 여부 확인
-> 저장 또는 외부 작업 발행
```

## 예외 처리 규칙

- 비즈니스 실패는 `BaseException`, 기능별 exception, 또는 `BusinessException` 계열로 표현한다.
- HTTP 상태와 메시지는 `ErrorCode`를 통해 일관되게 관리한다.
- 조회 대상이 없으면 `orElseThrow(...)`로 명확한 예외를 던진다.
- Controller에서 service 예외를 try-catch로 감싸지 않는다.
- 예외 응답은 `GlobalExceptionHandler`가 처리한다.
- `RuntimeException` 직접 throw는 피한다.

예시:

```java
return courseProgressReader.findCourseProgress(courseId, userId)
        .map(CourseProgressDetailResponseDto::from)
        .orElseThrow(CourseProgressNotFoundException::new);
```

## Port 사용 규칙

- Service는 DB 접근 시 domain port를 우선 사용한다.
- 읽기와 쓰기 책임은 커지면 `Reader`와 `Writer`로 분리한다.
- 외부 작업 발행은 `Publisher` port로 표현한다.
- OAuth, token, password, storage, AI/STT 같은 외부 기능은 `Provider` 또는 명확한 domain port로 표현한다.
- Service가 `*JpaRepository`를 직접 주입받지 않는다.

예시:

```text
TrainingAnalysisRequestService
-> VoiceRecordingReader
-> TrainingAnalysisReader
-> TrainingAnalysisWriter
-> TrainingSessionWriter
-> AnalysisJobPublisher
```

## 상태 전이 규칙

- 상태 전이는 service에서 검증하고 entity 또는 writer port를 통해 수행한다.
- terminal 상태는 일반 명령으로 되돌리지 않는다.
- `COMPLETED`, `CANCELED`, `FAILED` 같은 상태는 후속 명령 허용 여부를 명확히 검사한다.
- 분석 요청은 최종 녹음 선택, 녹음 품질 `PASS`, 중복 분석 없음 조건을 만족해야 한다.
- 클래스 완료는 필수 단계 완료와 progress 조건을 만족해야 한다.

상태 전이 문서는 `docs/architecture/state.md`를 기준으로 한다.

## 외부 연동과 비동기 작업

- Service는 외부 연동의 유스케이스 순서를 조율한다.
- 실제 HTTP/SDK 호출 구현은 infrastructure 또는 provider 구현체에 둔다.
- 외부 작업을 요청하기 전 DB에 추적 가능한 record를 먼저 만든다.
- retry count, timeout, fallback, failure mapping은 service 규칙과 infrastructure 구현을 분리해 관리한다.
- mock publisher를 사용하더라도 service는 `AnalysisJobPublisher` 같은 port만 의존해야 한다.

## Service 간 협력

- 같은 유스케이스 흐름에서 이미 존재하는 service의 public method를 재사용할 수 있다.
- 단순 공통 검증을 위해 service끼리 과도하게 얽히면 domain port 또는 domain method로 분리한다.
- 순환 의존이 생기면 책임을 다시 나눈다.
- 예: `TrainingAnalysisRequestService`는 세션 존재 검증을 위해 `TrainingSessionService.assertSessionExists(...)`를 사용할 수 있다.

## 메서드 작성 규칙

- public method는 API 유스케이스나 application 유스케이스 단위로 작성한다.
- private helper는 validation, mapping, 상태 계산처럼 의도가 명확한 경우에만 분리한다.
- 긴 if/else 흐름은 상태 전이 표나 private helper로 나눈다.
- 같은 예외 조건이 반복되면 도메인 method 또는 private validator를 고려한다.
- null 반환보다 Optional, 빈 컬렉션, 명시적 예외를 사용한다.

## 임시 구현 규칙

- 임시 로그인 사용자 ID는 최종 인증 흐름으로 교체해야 한다.
- 임시 stage/progress 계산은 주석 또는 문서에 이유를 남긴다.
- mock publisher/provider는 port 뒤에 숨겨 service 코드가 바뀌지 않게 한다.
- 임시 구현이 API 계약으로 굳어지지 않도록 문서에 최종 방향을 함께 기록한다.

## 금지 사항

- Service에서 `ApiResponse`를 생성하지 않는다.
- Service에서 HTTP status code를 직접 결정하지 않는다.
- Service에서 JPA repository를 직접 호출하지 않는다.
- Service에서 entity를 public API 응답으로 직접 반환하지 않는다.
- Service method 하나에 validation, query, mutation, 외부 호출, mapping을 무분별하게 몰아넣지 않는다.
- 외부 provider raw DTO를 service 반환값으로 노출하지 않는다.
- 의미 없는 `process`, `handle`, `manage` 이름으로 유스케이스를 숨기지 않는다.

## 변경 전 체크리스트

- service method가 하나의 유스케이스를 표현하는가?
- 필요한 transaction annotation이 붙어 있는가?
- DB 접근을 domain port로 수행하는가?
- 상태 전이 전 validator가 충분한가?
- 사용자 소유권 또는 권한 검증이 필요한가?
- 실패 조건이 `ErrorCode`와 exception 흐름으로 표현되는가?
- 반환값이 Controller DTO에 과도하게 묶여 있지 않은가?
- 외부 연동이 provider/port 뒤에 숨겨져 있는가?
- 관련 API, flow, state 문서를 함께 갱신해야 하는가?
