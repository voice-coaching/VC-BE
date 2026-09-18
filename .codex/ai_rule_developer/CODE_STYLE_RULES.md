# 코드 스타일 규칙 — VC-BE

## 기존 Java 코드와 일치

- Java 21 문법과 기존 Spring/Lombok 관례를 따른다. build.gradle에 없는 formatter/linter를 있다고 가정하지 않는다.
- 새 코드의 들여쓰기는 4 spaces, 메서드·논리 블록은 빈 줄로 구분한다. 주변 파일의 일관된 형식은 존중하며 무관한 전체 재포맷을 하지 않는다.
- Java 타입은 PascalCase, 메서드·변수는 lowerCamelCase, 상수는 UPPER_SNAKE_CASE, 패키지는 소문자다.
- DTO는 *Dto, 저장소는 *JpaRepository, 서비스는 *Service 등 해당 패키지 관례를 따른다.
- userId, requestId처럼 확립된 이름은 유지하고 모호한 임의 축약·무의미한 process/handle 이름을 피한다.
- boolean은 isActive, hasPermission, shouldRetry처럼 의미가 드러나게 작성한다.

## 타입과 메서드

- 공개 계약은 명시적 타입을 사용하고 request/response를 느슨한 Map으로 대체하지 않는다.
- 지역 var는 타입과 의도가 분명한 기존 관례 범위에서 허용한다.
- record, Lombok, class 중 주변 모델과 책임에 맞는 형태를 사용한다.
- 유스케이스, 검증, 상태 전이, 저장, 외부 호출의 책임을 구분하되 기계적인 줄 수 제한으로 메서드를 분리하지 않는다.
- 가능하면 클래스 하나가 주된 책임 하나를 가진다. 작은 중첩 record 등 응집된 계약은 기존 관례를 따른다.
- 관련 import를 정리하고 사용하지 않는 import는 같은 수정에서 제거한다.

## 설명과 범위

- 비명시적 제약, 트랜잭션·잠금 이유, 외부 계약 가정, fallback·재시도 의미에는 짧은 JavaDoc 또는 주석을 남긴다.
- 이름을 반복하는 주석이나 자명한 accessor 설명을 양산하지 않는다.
- TODO에는 미확정 계약과 완료 조건을 남긴다.
- 스타일 수정만으로 동작·공개 필드명·오류 메시지를 바꾸지 않는다.
