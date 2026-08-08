# 컴포넌트 및 모듈 문서 - voice

프로젝트 단위를 어떻게 분리하고 재사용하는지 기록한다.

## 분리 기준
- 공통 단위:
- 기능 전용 단위:
- Service/Application 단위:
- Repository/Adapter 단위:
- UI 또는 Presentation 단위:

## 계약 규칙
- Props 또는 입력 모델 규칙:
- 출력/result 모델 규칙:
- 재사용 기준:
- 소유 경계:

## 프로필 메모

### Spring Boot
- 생성자 주입을 기본으로 사용한다.
- @Transactional은 비즈니스 유스케이스가 있는 service 계층에 둔다.
- ControllerAdvice 또는 공통 예외 처리기로 오류 응답을 일관화한다.
- Entity 변경은 migration과 DB 문서 변경을 함께 검토한다.
