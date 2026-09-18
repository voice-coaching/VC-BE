# VC-BE Codex 하네스 정비 기록

확인일: 2026-09-18. 대상은 로컬 VC-BE checkout a749fa7과 작업 시작 시 존재하던 사용자 문서다.
이 문서는 에이전트 작업 규칙의 설계·검증 범위이며 애플리케이션 기능 변경 명세가 아니다.

## 코드에서 확인한 기준

| 항목 | 확인 결과 | 근거 |
| --- | --- | --- |
| 기술 스택 | Java 21, Spring Boot 4.1.0, Gradle Wrapper 9.5.1, JPA, PostgreSQL, Flyway | [build.gradle](../../build.gradle), [wrapper](../../gradle/wrapper/gradle-wrapper.properties) |
| 모듈 | 기능별 controller/application/domain/infrastructure; JPA entity는 domain/entity | [코드 루트](../../src/main/java/org/example/voice/) |
| HTTP | 공개 /api, 선택적 오류 code, 내부 AI는 별도 토큰·flat JSON | [ApiResponse](../../src/main/java/org/example/voice/common/response/ApiResponse.java), [내부 Controller](../../src/main/java/org/example/voice/analysis/controller/InternalRunPodAnalysisController.java) |
| AI | RunPod HTTP와 별도 Stream 구현 공존; 기본 transport disabled | [application.yaml](../../src/main/resources/application.yaml), [RunPodContract](../../src/main/java/org/example/voice/analysis/infrastructure/runpod/RunPodContract.java) |
| 검증 | 기본 H2와 조건부 PostgreSQL/Redis 통합 테스트 | [테스트 설정](../../src/test/resources/application.yaml), [migration 테스트](../../src/test/java/org/example/voice/support/SupportPostgresMigrationTest.java), [Redis 스크립트](../../scripts/run_analysis_redis_integration.sh) |
| CI·배포 | clean test bootJar; develop push가 운영 배포를 유발 | [CI](../../.github/workflows/ci.yml), [deploy](../../.github/workflows/deploy.yml) |
| Git 관리 | .codex/와 AGENTS.md는 ignore 대상이나 기존 규칙 4개는 이미 tracked | [.gitignore](../../.gitignore) |

원격 운영 상태는 이번 작업에서 재검증하지 않았다. 위 버전과 파일 상태는 확인 시점의 기록이며 앞으로는 원본을 다시 확인한다.

## 발견한 문제와 조치

| 기존 문제 | 적용한 변경 |
| --- | --- |
| FastAPI·MySQL 전제와 실제 Spring/PostgreSQL 충돌 | 전역·계층·API·DB 규칙을 구현에 맞춤 |
| 일반 REST 기본값 /api/v1이 기존 경로 변경을 유도 | /api와 기존 명령 endpoint의 호환성 명시 |
| 시작 프롬프트와 규칙에 같은 지시 반복 | 짧은 시작 프롬프트, 단일 전역 규칙, 작업별 탐색표 |
| 자동 발견 진입점 부재 | 루트 AGENTS.md에서 .codex로 연결 |
| 규칙마다 모든 계층 지시가 반복되고 상세 읽기 조건 없음 | 공통/계층/검증/문서의 책임을 나누고 필요한 문서만 읽도록 구성 |
| 테스트 실행·skip·환경 차이와 완료 기준 누락 | WORKFLOW와 TESTING_RULES 추가 |
| Java와 무관한 스타일 예시, 모호한 5칸/3칸 공백, 기계적 10줄 분리 | Java 네이밍·주변 형식·의미 기준의 분리로 수정 |
| 인계·계획·현재 코드·운영 상태 혼동 가능 | 근거 종류와 리비전 구분, 운영 진단 단계 구분 |
| 공개 envelope·domain 순수성의 절대화가 기존 구조와 충돌 | 내부 AI flat JSON, JPA annotation 등 실제 예외 명시 |

## 남겨 둔 명세 차이

- docs/database/schema.md의 도입부는 V0..V15를 가리키지만 현재 migration 디렉터리에는 V26까지 있다.
- 오래된 architecture/flow 문서에는 Stream 중심 설명이 남아 있고 HTTP 계약·구현이 별도로 존재한다.
- 최신 인계·채점 계획은 다른 리비전의 결과 v2·점수를 언급하지만, 이 checkout의 HTTP 결과 schema는 runpod-analysis-result.v1이다.
- 기존 일부 서비스는 controller DTO에 의존한다. 규칙은 신규 의존 확대를 막되 현재 요청과 무관한 일괄 리팩토링을 요구하지 않는다.

이번 요청은 .codex 최적화이므로 위 차이를 앱·운영 변경으로 해소하지 않았다.
해당 기능을 수정할 때 코드·스키마·대상 리비전을 확인하고 관련 명세를 갱신하도록 규칙에 반영했다.

## 사용·유지보수

- [AGENTS.md](../../AGENTS.md)에서 [탐색표](../../.codex/README.md)와 [전역 규칙](../../.codex/ai_rule_developer/GLOBAL_RULES.md)로 진입한다.
- 수동 요청 템플릿은 [.codex/codex_start_prompt.txt](../../.codex/codex_start_prompt.txt)를 사용한다.
- 규칙 변경 시 [HARNESS_CHECKS](../../.codex/HARNESS_CHECKS.md)의 대표 요청과 정적 구조를 검토한다.
- 개인 전역 설정, 모델 선택, 승인·sandbox 정책, CI hook은 변경하지 않았다.
- .gitignore 정책을 유지했다. 로컬에서 생성·변경한 ignored 규칙은 Git diff만으로 모두 확인되지 않으며, 다른 checkout에 자동 공유되지 않는다.
- 기존 사용자 규칙의 현재 내용을 기준으로 수정했고 Git HEAD로 복원하지 않았다. 기존 ref_docs, 백업, 인계·계획·미디어는 보존한다.

## 검증 범위

- 변경 파일 16개와 로컬 Markdown 링크 56개를 확인했으며 누락된 링크는 없었다.
- 새 문서의 UTF-8 인코딩·후행 공백, 기존 사용자 문서·영상·백업 12개의 SHA-256 보존을 확인했다.
- git diff --check를 통과했다. ignored 파일은 Git 결과와 별도로 실제 내용을 확인했다.
- 이전 스택·API 기본값·공백 지시를 검색하고, 대표 요청 10개의 기대 행동을 규칙과 문서 검토로 대조했다. 별도 에이전트 세션을 실행한 행동 평가는 아니다.
- 시작 프롬프트는 UTF-8 기준 2,670바이트에서 919바이트로 약 66% 줄었다. 모델 성능이나 총 토큰 절감률을 측정한 결과는 아니다.
- 문서 변경이므로 Java 빌드, DB/Redis 통합 테스트, 운영·LLM 호출은 실행하지 않았다.

규칙은 동작 방향을 안내하며 자동 강제 장치가 아니다.

## 설계 참고

짧은 AGENTS.md를 진입점으로 두고 상세 문서를 분리하는 구성은
[OpenAI의 AGENTS.md 안내](https://learn.chatgpt.com/docs/agent-configuration/agents-md)와
[Codex 작업 권장사항](https://learn.chatgpt.com/guides/best-practices)을 참고했다.
프로젝트별 계층·테스트·운영 규칙은 외부 예제를 복사하지 않고 위 로컬 근거에서 도출했다.
