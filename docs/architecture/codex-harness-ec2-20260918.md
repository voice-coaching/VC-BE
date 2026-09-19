# Codex 하네스와 EC2 환경 재확인

확인: 2026-09-18 13:55 UTC 이후(KST 22:55 이후).
로컬 HEAD: 4d6d2c0c0f57db652cf1a63a426e7ca4f9a9d19c.
이 기록은 문서 개선의 근거이며 운영 변경이나 분석 성공 검증이 아니다.
기존 [하네스 정비 기록](codex-harness.md)은 당시 관측으로 유지한다.

## 현재 확인한 근거

| 대상 | 관측 |
| --- | --- |
| 로컬 | Java 21, Spring Boot 4.1.0, Gradle 9.5.1; Flyway V0~V26; 기본 테스트 H2/Flyway 비활성 |
| EC2 서비스 | alpha-backend/nginx active, Corretto 21.0.12 |
| 실행 정보 | MainPID 조회 후 cmdline의 -jar=/opt/alpha/app.jar 확인; WorkingDirectory=/opt/alpha/app |
| 현재 JAR 링크 | /opt/alpha/releases/d936066cba1d70bc152eb309b1c3614c71a5bdd3.jar |
| JAR SHA-256 | af65d4b3c3deb77ef4ed9c08bee251328352d29e2fd3761e9b6e84c8788bdab7 |
| manifest | Spring Boot 4.1.0, Build-Jdk-Spec 21 |
| 실행 환경 허용 키 | runpod_http, Stream=false, timeout=PT15M, claim TTL=PT90S, 관리 127.0.0.1:9091, 저장소/정규화=true |
| 관리 health | /internal/actuator/health HTTP 200, status UP |
| 공개 OpenAPI | /v3/api-docs HTTP 200 |
| 무토큰 capabilities | AUTHENTICATION_REQUIRED 응답; 인증된 기능 조회는 미실행 |
| nginx | API→127.0.0.1:8080; AI→RunPod proxy, upstream SNI 및 TLS verify on |
| 패키징 계약 | runpod_result_v1.schema.json의 $id는 v1이지만 schemaVersion에는 결과 v1/v2 enum 분기가 있음 |
| 패키징 migration | SQL 29개. 실제 DB 적용 이력은 미조회 |

JAR 링크와 파일명은 조회 시점의 산출물 식별 정보다. 실행 시작 때의 파일과 현재 파일의 동일성, 빌드 commit provenance를 별도 증명하지 않았다.
로컬 결과 schema는 결과 v1 const이고, 배포 산출물은 v1/v2 분기를 포함한다. 두 환경의 구현을 동일시할 수 없다.
최초 추측 경로 /actuator/health는 인증 오류를 반환했다. 실제 설정 경로로 다시 조회해 UP을 확인했다.

## 문서 설계 변경

- README: 삭제된 HTTP/Stream 계약 링크를 현존 명세·코드/테스트 출발점으로 대체하고, 누락 문서 탐색과 운영 문서 읽기 조건 추가.
- WORKFLOW: 목표가 없는 인계 처리, 인코딩, 로컬/원격 셸 확장 구분, 필요한 부분 재조회 기준 추가.
- EXTERNAL_INTEGRATION_RULES: 로컬 provider와 운영 구현 분리, schema 파일명 대신 내부 분기 검증.
- OPERATIONS 신설: 프로세스→산출물→선별 설정→라우팅/health 순서와 단계별 성공 증거.
- 시작 프롬프트: 공통 기술 스택 반복을 줄이고 목표/범위/근거/완료 조건 중심으로 구성.
- HARNESS_CHECKS: 삭제 문서, 인계, 배포 산출물, 계약 버전, 인증 401, provider 차이 시나리오 6개 추가.

짧은 진입점에서 필요한 지침을 읽도록 구성하는 원칙은 [공식 OpenAI AGENTS.md 문서](https://learn.chatgpt.com/docs/agent-configuration/agents-md)를 참고했다.
프로젝트 규칙은 실제 소스와 이번 EC2 관측에서 도출했다. 자동 hook·CI 검사·모델 설정은 추가하지 않았다.

## 검증과 제한

문서 변경이므로 Java 빌드·DB/Redis 통합 테스트는 실행하지 않는다.
RunPod 내부/모델·사용자 JWT 조회·새 추론·실제 DB migration 적용 이력은 확인하지 않았다.
서비스 재시작·환경 수정·push·배포는 수행하지 않았다.
기존 삭제 문서와 미추적 사용자 문서를 보존한다. .codex는 대부분 ignored이므로 Git diff 외에 파일 내용을 직접 검사한다.

검증 결과: .codex 로컬 Markdown 링크 35개 누락 없음, UTF-8/후행 공백 검사 통과, git diff --check 통과.
기존 .codex 5개 수정과 OPERATIONS 1개 추가를 직접 확인했으며 기존 docs 파일들의 SHA-256이 모두 유지되었다.
HARNESS_CHECKS의 기존 10개와 추가 6개 시나리오를 지침과 대조했다. 별도 에이전트 실행이나 토큰/성능 측정은 하지 않았다.
Git 사용자 전역 ignore 파일 접근 경고가 있었으나 저장소 상태 출력과 diff 검사는 완료됐다.
