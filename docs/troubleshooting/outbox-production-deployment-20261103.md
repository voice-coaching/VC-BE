# HTTP 분석 outbox 운영 복구 — 2026-11-03 KST

## 원인과 확인 범위

사용자 정정에 따라 최초 전송 오류는 분석 106의 HTTP 503이다. 503의 내부 원인은
확인하지 않았다. 뒤늦은 409를 최초 장애 원인으로 보거나 endpoint gate가 운영
정체를 만들었다고 설명하지 않는다.

직접 조회한 운영 JAR 링크는 main `6806dd35cdb5ff182b10bd4fcd4baefc7a48430d`이며,
SHA-256은 `e280974c868acaa3ebdd02eafbde3336652b1e283f25c85dd610dd4144d95911`이다.
2026-11-02 16:28 UTC의 읽기 전용 DB 조회에서 106~114는 모두 PENDING이고 deadline이
지났으며 worker claim, lease, handoff 인수 기록이 없었다. 영속 delivery spool에도
해당 분석들의 수신 항목이 없었다. 106의 attempt_count는 14006, 107~114는 0이었다.
이는 분석 ID이며 대응 outbox ID는 113~121이다.

PR #176은 develop `38c65a6`에 병합되었으나 운영 main은 별도다. main의 HTTP
dispatcher는 retryable과 상한을 무시하고, timeout sweeper의 expire()는 비어 있다.
main의 AnalysisResult 실행 식별자 검사도 항상 true를 반환하므로 develop dispatcher를
그대로 복사하는 것만으로 세대 보호가 복구되지 않는다.

## 배포할 변경

- 운영 main 기준 별도 브랜치에 필요한 HTTP 경로만 통합한다. 관리자·결제·콘텐츠,
  인증 정책, DB schema, AI 서버, endpoint gate 구현은 변경하지 않는다.
- PR #176의 due-row 선택, 오류별 유한 재시도, 30/60/120/240/300초 backoff 및
  Retry-After 처리를 포함한다. HTTP 전 deadline을 검사해 만료 요청을 제출하지 않는다.
- dispatcher에서 캡처한 request/execution과 현재 분석·outbox의 실제 필드를 비교한다.
  무조건 true인 전역 helper에 의존하지 않는다. 다른 세대와 terminal 결과를 보호하며,
  claim 또는 handoff가 확인된 실행은 전송 실패 때문에 종료하지 않는다.
- HTTP timeout sweeper를 복원한다. deadline 또는 미인수 lease가 만료된 PENDING /
  PROCESSING을 기존 repository로 잠그고, 수신 완료된 정확한 execution 또는 DB
  handoff는 제외한다. 실패 전이, pending outbox 종료, 기존 cancellation 예약을 사용한다.
- Redis Stream용 2인자 recordDispatchFailure와 다른 main 정책은 바꾸지 않는다.

## 기존 요청 처리 결정

사용자가 106~114의 deadline 만료 및 claim·결과 인수 부재를 확인한 경우 정상 timeout
처리 경로로 종료하도록 승인했다. 직접 SQL UPDATE나 새 실행 ID/deadline 생성은 하지
않는다. 배포된 dispatcher/sweeper의 기존 도메인 전이로 종료시키며 새 분석은 요청하지
않는다. 배포 직전 상태가 달라졌으면 현재 claim·세대·수신 정보를 우선한다.

## 검증·배포 기록

- 자동 테스트·실제 추론은 실행하지 않는다. 최종 Java 21 bootJar 빌드와 소스/diff,
  실제 JAR의 class bytecode를 확인한다.
- 운영 main과 동일한 baseline의 migration/계약 리소스와 비교한다.
- 배포 시 기존 JAR를 보존하고 산출물 SHA-256, 링크, 새 프로세스 시작 및 health를
  대조한다. 시작/health 실패 시 기존 JAR로 되돌린다.
- 배포 후 106~114의 실제 상태, outbox 상태, request/execution 불변을 읽기 전용으로
  확인한다. health 성공을 새 추론 성공으로 표현하지 않는다.

Java 21 `bootJar -x test --no-daemon` 빌드는 성공했다. 최초 오프라인 빌드는 기존
PortOne SDK 캐시 부재로 실패했으며, 의존성을 정상 저장소에서 받은 뒤 빌드했다.
자동 테스트·compileTestJava·실제 추론은 실행하지 않았다. `git diff --check`와
패키징된 세 클래스의 javap 결과에서 due-row 조회, deadline, isRetryable, 재시도 상한,
현재 실행 필드 비교, claim/handoff 보호, sweeper 조회·실패·취소 예약을 확인했다.

Windows checkout의 SQL 줄바꿈을 LF로 정규화하여 다시 패키징했다. Git 내용 변경은
없다. 실제 운영 JAR와 비교해 66개 migration 리소스 및 17개 계약 리소스 집합의
이름·내용 해시가 각각 일치한다. 최종 패키지의 세 클래스도 앞서 확인한 바이트코드와
동일함을 확인했다. application.yaml과 공개 계약, 신규 migration은 변경하지 않았다.

## 실제 배포 결과

- 배포 소스 커밋: `0b7262c569faca1fbf0114191dbe66119ad9836c`.
- 최종 JAR SHA-256: `eabd8379c5fe7e1300e99ae7a5210d54714faa4d83bc8a84c2f6ec8e6955bc4c`.
- 운영 링크: `/opt/alpha/releases/0b7262c569faca1fbf0114191dbe66119ad9836c.jar`.
- 실제 Java 프로세스 PID는 2447633에서 2491934로 변경됐고, `-jar` 인자는
  `/opt/alpha/app.jar`다. 시작 시각은 2026-11-02 16:36:38 UTC
  (2026-11-03 01:36:38 KST)다. 기존 릴리스는 삭제하지 않았다.
- 기존 배포 진입점을 실행하기 전 기존/신규 JAR 해시와 health를 확인하고,
  배포 후 새 PID·링크·해시·내부 health UP·공개 OpenAPI 200을 확인했다.
  health 실패 시 기존 JAR와 배포 상태를 되돌리는 별도 wrapper를 적용했으며,
  이번 배포에서는 rollback이 발생하지 않았다.
- 운영 증거는 `/var/lib/alpha-deploy/releases/0b7262c569faca1fbf0114191dbe66119ad9836c/`
  아래 `before-activation.json`과 `activation-verification.json`에 남겼다.
- 후속 읽기 전용 DB 조회에서 분석 106~114 모두 `FAILED`, failure_code는
  `analysis_execution_timeout`, 해당 outbox도 모두 `FAILED`다. 기존 request/execution ID는
  9건 모두 동일하다. 107~114의 attempt_count는 계속 0이다. 106의 최종 누적 시도는
  14197로, 배포 준비 중 기존 바이너리에서 늘어난 횟수와 새 바이너리의 로컬 timeout
  판정 1회를 포함한다. 새 바이너리에서는 만료 원본을 worker에 재전송하지 않는다.
- 새 프로세스 로그 요약에 예외 타입이 없고, 실제 timeout 전이를 관측했다.
  신규 분석/실추론, 자동 회귀 테스트, 최초 503 내부 원인 규명은 수행하지 않았다.

소스는 별도 커밋에 보존했다. 사용자가 `voice-coaching/VC-BE`의
`fix/outbox-timeout-production-20261103` 브랜치 push와 검토용 PR 생성을 명시 승인했다.
GitHub main/develop 병합이나 CI 배포를 실행한 것으로 보고하지 않는다.
