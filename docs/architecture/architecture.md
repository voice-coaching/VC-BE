# 아키텍처 문서 - voice

## 요약
멀티 모달 기반 보이스 코칭 플랫폼 구현

## 기본 정보
- 스택: Spring Boot
- 데이터베이스: postgresql
- 인증 사용: 사용
- 외부 API 연동: 사용

## 계층 방향
- 이 프로젝트가 실제로 사용하는 의존성 방향을 기록한다.
- 각 계층이 import할 수 있는 대상과 금지 대상을 설명한다.
- 기능/도메인 폴더를 소유권 경계로 두고 각 기능 하위에 계층 폴더를 둔다.

## 프로필별 아키텍처 메모

### Spring Boot
- 기능 경계는 `.../<feature>` package 아래에 둔다. 예: login, post, talk.
- Controller는 각 기능 package 안에서 HTTP 요청/응답과 인증 컨텍스트 연결만 담당한다.
- Service는 같은 기능 package 안에서 트랜잭션 경계와 비즈니스 규칙의 중심이다.
- Repository는 JPA 또는 데이터 접근 인터페이스로 제한한다.
- Entity는 DB 영속성 모델이고 DTO는 API 계약이다. Entity를 외부 응답으로 직접 노출하지 않는다.
- 공통 config, security, exception handler만 전역 package로 분리한다.
- 권장 흐름: Controller -> Service -> Repository -> DB, 단 각 계층은 같은 기능 package 안에 둔다.

## 흐름 메모
- 데이터 흐름:
- 인증 흐름:
- API 흐름:
- 저장 흐름:
- 외부 연동 흐름:

## 아키텍처 결정
| Date | Decision | Reason | Impact |
| --- | --- | --- | --- |
