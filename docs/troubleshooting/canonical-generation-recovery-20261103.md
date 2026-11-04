# 현재 실행 결과 조회와 영속 큐 거절 상태 복원 — 2026-11-03

## 확인한 문제

PR #181 검토 후 같은 결과 처리 경로에서 다음 기존 결함을 확인했다.

1. 파일 큐·PUBLISHED 메모리·DB 조회가 analysis ID만 비교하고 binding도 최근 등록
   행을 골랐다. 재시도 이전 결과가 선택될 수 있었다. 신규 commit도 현재 request,
   execution, worker와 terminal 상태를 확인하지 않아 늦은 결과가 상태를 덮을 수 있었다.
2. 큐 재시작은 rejected marker를 무시했다. 원본 재읽기는 byteSize/SHA와 read limit를
   확인하지 않았고 verify lane은 원본 확인 없이 preview를 열었다. 확정적인 계약
   오류도 재시도됐으며 DB worker의 invalid/revoked 분류는 항상 false였다.

소스·호출 경로에서 확인한 결함이며 운영에서 경합·파일 손상을 새로 발생시켜 재현하지
않았다. AI 정책·의미 검증기를 복원하는 변경은 아니다.

## 파일별 변경

- `CanonicalResultAttempt`: 현재 analysis/request/execution/worker/recording/content를
  비교한다. PENDING/PROCESSING preview와 이미 commit된 같은 canonical event만 조회한다.
- `CanonicalAnalysisQueryService`: 소유자를 확인하고 같은 analysis snapshot으로 조회한다.
- `CanonicalViewVisibility`: 응답 직전에 DB의 현재 request/execution/worker/status/event와
  snapshot을 대조한다. 조회 중 retry·timeout·숨김 상태 전이는 기존 409로 재조회시킨다.
  contract 조회도 view와 새 binding의 request/execution이 다르면 같은 409를 반환한다.
- `CanonicalPublishedResults`: path/worker identity와 재발행 raw 문서를 확인하고 현재
  실행만 반환한다. 같은 실행의 다른 결과로 메모리를 덮어쓰지 않는다.
- `CanonicalCommittedResultReader`: 현재 request/execution의 후보만 조회하고 나머지
  identity/event를 확인한다. 과거 실행을 fallback으로 반환하지 않는다.
- `CanonicalExecutionRegistryImpl`: active request/execution에 연결된 binding만 기존
  visible relation과 owner 조건으로 조회한다.
- `CanonicalCallbackCommitter`: 기존 동일 결과의 멱등 ACK는 유지한다. 신규 결과는
  분석 행을 잠근 상태에서 현재 identity와 PENDING/PROCESSING을 확인한다. 오래된
  결과는 EXECUTION_INACTIVE로 거절하여 새 실행·timeout을 덮지 않는다.
- `CanonicalDeliverySpool`: rejected marker를 복원하고 owner lock 실패 시 시작을
  중단한다. 파일은 limit+1까지만 읽는다. 조회는 수신·확인된 현재 실행의 비거절
  항목만 반환하고 verified 조회는 digest도 비교한다. 원본을 삭제하지 않는다.
- `CanonicalHandoffDocument`: 기존 원본 계약의 1~16 MiB 범위, kind, byteSize, SHA를
  공유 검사한다. 파일 큐와 DB handoff worker에서 같은 검사를 사용한다.
- `CanonicalDeliveryWorker`: 원본 확인 후 preview를 연다. 확정적인 400/422·명시된
  409 충돌/비활성 오류와 DB의 REJECTED/REVOKED는 거절 marker를 영속화한다.
  파일 접근·DB 일시 장애는 backoff 재시도를 유지한다.
- `CanonicalHandoffWorker`: 원본 확인 후 commit한다. 비활성 실행은 REVOKED,
  확정적인 400/422·결과 충돌은 REJECTED다. 같은 claim일 때만 마감하고 해당 실행의
  PENDING/PROCESSING만 canonical_handoff_invalid로 종료한다. 다른 실행·완료된 분석에
  실패 상태를 덮어쓰지 않는다.

URL/wire schema/DB migration/AI 모델·채점/새 재분석은 변경하지 않는다.

## PR #181 운영 관측

main 병합 c0f940ea2656a18ec859e500dc8d43b9cc793ee3의 Deploy production workflow
41489644445는 success였다. 2026-11-03 02:20:29 KST 읽기 전용 확인에서
alpha-backend active, app.jar 링크는 같은 SHA의 release JAR, management health 200이었다.

- 119: analysis COMPLETED / canonical result 존재 / handoff COMMITTED, attempts 1.
- 72: handoff COMMITTED 복구. 기존 누적 attempts 4399를 초기화하지 않았다.
- 106~114: FAILED / analysis_execution_timeout 유지. 재분석하지 않았다.
- 118: FAILED / analysis_execution_timeout. 분석 성공으로 보고하지 않는다.

후속 읽기 전용 대조에서 72·119의 현재 request/execution/worker/recording/content와
committed event/visible 조건이 모두 일치했고 파일 큐 원본은 각각 5개가 크기·SHA 검사를
충족했다. 결과 원문·사용자 식별자·자격증명은 출력하지 않았다.

이 관측은 PR #181 배포에 대한 것이다. 후속 수정의 배포는 Actions와 실행 JAR를 별도
대조한다. 새 추론이나 운영 DB 직접 수정은 수행하지 않는다.

## 검증과 제한

Java 21 `--offline --no-daemon compileJava compileTestJava bootJar -x test`가 성공했다.
기본 sandbox의 offline 의존성 접근 실패는 정상 Gradle 환경으로 전환했고 컴파일에서
발견한 content accessor 오류도 수정했다. diff·호출자·상태 전이·잠금·SQL을 정적으로
검토했다. 사용자 지시에 따라 test task, 새 테스트, 브라우저 자동화, 실제 AI 추론은
실행하지 않았다. PostgreSQL 동시성·파일 손상·재시작 경합의 실행 QA를 대신하지 않는다.

개발자 확인 경로: retry 후 늦은 결과, 동일 결과 ACK 재전달, timeout 후 preview,
rejected marker 재시작, 원본 손상/초과 크기, DB 일시 장애, claim 교체 중 실패 마감.
