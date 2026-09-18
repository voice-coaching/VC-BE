# 아키텍처 규칙 — VC-BE

## 코드 배치

기능별 패키지를 src/main/java/org/example/voice 아래에 둔다.
auth, user, onboarding, practicecontent, practiceexample, training, analysis, consent,
course, title, home, mypage, support, profileimage, notification이 기능 경계이며 common은 공유 기반 코드다.
실제 디렉터리와 [directory 명세](../../docs/architecture/directory.md)를 확인하고 필요한 패키지만 추가한다.

| 위치 | 책임 |
| --- | --- |
| feature/controller | HTTP mapping, 인증 사용자 전달, 입력·응답 변환 |
| feature/controller/dto | Request/Response/Query DTO |
| feature/application | 유스케이스 조합, 소유권, 트랜잭션, 상태 전이 조정 |
| feature/domain/entity | JPA 매핑과 엔티티 불변조건·상태 변경 |
| feature/domain/model | HTTP에 종속되지 않는 조회·명령·결과 모델 |
| feature/domain/port | 저장소 및 외부 기능 인터페이스 |
| feature/domain/type | enum 및 도메인 분류 |
| feature/infrastructure | JPA repository, port 구현, 외부 client, cache |
| feature/exception | 기능별 예외 |
| common | 공통 설정·응답·예외·보안·저장소 기반 코드 |

## 의존성

런타임 호출은 Controller → application → domain port → infrastructure → DB/외부 시스템이다.
소스 의존성에서 infrastructure가 domain port를 구현한다. domain이 infrastructure 구현을 참조하는 구조로 오해하지 않는다.

- Controller는 application에 유스케이스를 위임한다.
- application은 명시적으로 주입된 port와 domain model을 사용한다. 신규 HTTP 타입·controller DTO·JPA repository 직접 의존을 추가하지 않는다.
- domain은 controller, application 구현, 외부 SDK/client에 의존하지 않는다.
- 이 저장소는 domain/entity의 JPA annotation과 application의 Spring @Service/@Transactional을 허용한다. 순수 Java domain으로 전면 재구성하지 않는다.
- 기존 application의 controller DTO 의존, analysis/provider 등 예외가 있다. 관련 요청 없이 전면 이동하지 말고 변경 지점에서 의존 확대를 피한다.
- HTTP 전용 인증·schema 파싱 adapter를 경계에서 사용하는 것은 허용하지만, 상태 판단·DB 쓰기·외부 호출을 Controller에 넣지 않는다.

## 저장소·캐시·외부 경계

- JPA repository와 조회 구현은 infrastructure에 두고, 필요한 Reader/Writer port를 사용한다.
- Redis 캐시는 infrastructure 조회 경계에 둔다. 사용자별 key, TTL, 쓰기 후 무효화, 진행 중 결과의 캐시 여부를 함께 검토한다.
- 분석 전달과 결과 처리는 analysis/infrastructure/runpod 또는 stream 경계에 둔다.
- 녹음 저장·정규화는 training/infrastructure/storage, 공통 S3 기반은 common/storage의 기존 책임을 따른다.
- 다른 기능과 공유할 필요가 확인된 코드만 common으로 옮긴다. 일반화만을 위한 새 계층을 만들지 않는다.

구조가 바뀌면 docs/architecture의 directory, architecture, component 중 영향받는 명세를 갱신한다.
