# Code Style Rules - voice

이 문서는 voice 백엔드의 Java/Spring Boot 코드 작성 스타일을 정의한다.
코드 스타일은 취향이 아니라 유지보수성과 일관성을 위한 규칙이며, 기존 코드의 패턴을 우선 따른다.

## 기본 원칙

- Java 21과 Spring Boot conventions를 따른다.
- 패키지, 클래스, 메서드, 필드 이름은 역할과 도메인 의미가 드러나게 작성한다.
- API DTO, domain entity, domain model, port, infrastructure 구현체를 이름과 위치로 명확히 구분한다.
- 새 코드는 기존 모듈의 스타일을 먼저 확인한 뒤 같은 방식으로 작성한다.
- 불필요한 추상화, 범용 helper, 의미 없는 wrapper를 만들지 않는다.
- 깨진 한글 문자열이나 깨진 주석을 새로 추가하지 않는다. 새 문서와 메시지는 UTF-8 기준으로 작성한다.

## Java Formatting

- 들여쓰기는 공백 4칸을 사용한다.
- 한 줄은 읽기 쉬운 길이를 유지하고, method chain이나 생성자 인자가 길면 여러 줄로 나눈다.
- 여러 줄 인자는 현재 코드처럼 다음 줄에 8칸 들여쓰기한다.

```java
return trainingSessionWriter.create(
        userId,
        request.contentId(),
        request.courseStepId(),
        request.learningFocus()
);
```

- import는 IDE 또는 formatter 기본 정렬을 따른다.
- 사용하지 않는 import는 남기지 않는다.
- wildcard import는 사용하지 않는다.
- 클래스 내부 순서는 대체로 상수, 필드, 생성자/팩토리, public method, private helper 순서를 따른다.

## 네이밍

- 클래스명은 PascalCase를 사용한다.
- 메서드명과 변수명은 camelCase를 사용한다.
- 상수는 UPPER_SNAKE_CASE를 사용한다.
- 패키지명은 lowercase를 사용한다.
- API URL은 kebab-case를 사용한다.
- DB column은 snake_case를 사용하고 `@Column(name = "...")`에 명시한다.
- boolean 메서드는 `is`, `has`, `can`, `should`처럼 참/거짓 의미가 드러나게 작성한다.
  - 예: `isSuspended()`, `existsSession(...)`, `hasNext`
- 컬렉션 변수는 복수형 또는 역할이 드러나는 이름을 사용한다.
  - 예: `courseIds`, `items`, `progressByCourseId`
- `process`, `handle`, `doSomething`, `data`, `result`처럼 문맥 없는 이름은 피한다.

## 클래스와 파일

- 하나의 public top-level class 또는 record는 하나의 파일에 둔다.
- 파일명은 public class/record 이름과 일치시킨다.
- Controller는 `*Controller`로 끝낸다.
- Application service는 `*Service`로 끝낸다.
- Domain port는 역할에 따라 `*Reader`, `*Writer`, `*Provider`, `*Publisher` 등을 사용한다.
- Infrastructure port 구현체는 `*Impl`로 끝낸다.
- Spring Data JPA repository는 `*JpaRepository`로 끝낸다.
- 예외 클래스는 `*Exception`으로 끝낸다.
- DTO 클래스명은 반드시 `Dto`로 끝낸다.

## DTO Style

- API DTO는 각 기능의 `controller/dto` 패키지에 둔다.
- DTO는 가능한 `record`를 사용한다.
- Request DTO와 Response DTO를 분리한다.
- Query/filter DTO는 `*ConditionDto` 이름을 사용한다.
- Response DTO는 domain model 또는 application 결과를 받는 `from(...)` 정적 팩토리 메서드를 둘 수 있다.

```java
public record TrainingSessionResponseDto(
        Long sessionId,
        Long contentId,
        Long courseStepId,
        String learningFocus,
        String status,
        OffsetDateTime startedAt
) {

    public static TrainingSessionResponseDto from(TrainingSessionCreatedData data) {
        return new TrainingSessionResponseDto(
                data.sessionId(),
                data.contentId(),
                data.courseStepId(),
                data.learningFocus().name(),
                data.status().name(),
                data.startedAt()
        );
    }
}
```

- DTO에서 entity를 직접 들고 있지 않는다.
- enum을 응답할 때 클라이언트 계약이 문자열이면 `.name()`으로 명시적으로 변환한다.
- page, size 같은 입력 기본값/제한값은 DTO의 `normalized()` 또는 application layer에서 일관되게 처리한다.

## Entity Style

- Entity는 `domain/entity` 패키지에 둔다.
- JPA entity는 `@Entity`, `@Table`, `@Id`를 명시한다.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- 필드 조회는 `@Getter`를 사용한다.
- setter를 열어두지 않는다.
- 생성은 정적 팩토리 메서드 또는 의미 있는 도메인 메서드를 사용한다.
- enum 필드는 `@Enumerated(EnumType.STRING)`을 사용한다.
- 시간 필드는 `OffsetDateTime`을 우선 사용한다.
- 상태 변경 메서드는 도메인 의미가 드러나게 작성한다.
  - 예: `recordLogin(...)`, `complete(...)`, `cancel(...)`

## Service Style

- Application service는 `application` 패키지에 둔다.
- 클래스에는 `@Service`, 생성자 주입에는 `@RequiredArgsConstructor`를 사용한다.
- 필드는 `private final`로 선언한다.
- 쓰기 유스케이스에는 `@Transactional`을 붙인다.
- 읽기 전용 구현은 필요하면 `@Transactional(readOnly = true)`를 사용한다.
- service method 이름은 유스케이스를 드러내야 한다.
  - 예: `create`, `complete`, `cancel`, `getCourseProgress`
- validation, 권한 확인, 상태 전이는 private helper로 분리할 수 있다.
- Controller 응답 DTO를 반환하지 말고 domain model 또는 application 결과를 반환하는 것을 우선한다.
- 기존 코드에 남아 있는 DTO 의존은 점진적으로 domain model 의존으로 정리한다.

## Repository and Infrastructure Style

- Infrastructure 구현체는 `infrastructure` 패키지에 둔다.
- Port 구현체에는 `@Repository` 또는 적절한 Spring stereotype을 붙인다.
- JPA repository는 infrastructure 내부에서만 직접 사용한다.
- Optional 반환은 리소스가 없을 수 있는 조회에 사용한다.
- collection 조회 결과는 null 대신 빈 List/Map을 반환한다.
- 복잡한 조회 변환은 private helper로 분리한다.
- stream은 가독성을 해치지 않는 범위에서 사용한다.

## Exception Style

- 비즈니스 오류는 `BaseException` 또는 기능별 `BusinessException` 계열로 표현하고 `ErrorCode`를 사용한다.
- 새 에러가 필요하면 `common/exception/ErrorCode.java`에 HTTP 상태와 메시지를 추가한다.
- Controller에서 try-catch로 에러 응답을 직접 만들지 않는다.
- 예외 메시지는 사용자 또는 클라이언트가 이해할 수 있는 문장으로 작성한다.
- 임시 디버깅용 예외나 `RuntimeException` 직접 throw는 피한다.

## Lombok

- 생성자 주입은 `@RequiredArgsConstructor`를 사용한다.
- Entity에는 `@Getter`와 protected no-args constructor를 사용한다.
- `@Data`는 사용하지 않는다.
- 무분별한 `@Setter`는 사용하지 않는다.
- builder가 실제 가독성을 높일 때만 사용한다.

## Comment Style

- 주석은 코드가 말하지 못하는 제약, 결정 이유, 외부 연동 조건을 설명할 때만 작성한다.
- 코드 내용을 그대로 반복하는 주석은 작성하지 않는다.
- TODO를 남길 때는 후속 작업 조건이나 이슈 맥락을 함께 적는다.
- 깨진 인코딩의 한글 주석을 새로 만들지 않는다.
- 기존 깨진 주석을 건드리는 파일을 수정할 때는 의미를 파악할 수 있는 범위에서 정상 한글 또는 명확한 영어로 정리한다.

## 테스트와 검증

- 코드 변경 후 가능한 범위에서 `gradlew.bat test` 또는 `./gradlew test`를 실행한다.
- API 계약 변경 시 Controller/Service 테스트 또는 문서 갱신 여부를 함께 확인한다.
- Entity나 schema 변경 시 DB 문서와 migration 필요 여부를 확인한다.
- 단순 문서 변경은 테스트 실행이 필수는 아니지만, markdown이 깨지지 않는지 확인한다.

## 금지 사항

- Entity를 API 응답으로 직접 반환하지 않는다.
- Controller에 비즈니스 로직을 길게 작성하지 않는다.
- Application service가 infrastructure 구현체 세부사항에 강하게 묶이지 않게 한다.
- 의미 없는 공통 util을 만들지 않는다.
- 사용하지 않는 패키지, import, 주석 처리된 코드를 남기지 않는다.
- 새 코드에 깨진 한글 문자열을 추가하지 않는다.
- public 계약이 불명확한 `Map<String, Object>` 또는 raw type을 사용하지 않는다.

## 변경 전 체크리스트

- 파일 위치가 프로젝트 계층 규칙과 맞는가?
- 이름만 보고 역할을 알 수 있는가?
- DTO, domain model, entity가 분리되어 있는가?
- transaction 위치가 application/infrastructure 책임과 맞는가?
- 예외가 `ErrorCode`와 전역 핸들러 흐름을 따르는가?
- 새 주석과 메시지가 UTF-8로 정상 표시되는가?
