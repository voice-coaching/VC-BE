# 분석 106 반복 전송이 후속 요청을 막는 문제 — 2026-11-02

> 후속 통합 안내(2026-11-03): 아래는 develop PR #176 작성 당시의 관측과 개선 설명이다.
> 이후 main PR #178에서 due-row 조회·행별 backoff와 함께 유한 재시도·deadline·실행 세대·
> 수신 완료 보호를 통합했다. 현재 운영 복구 근거는
> [HTTP 분석 outbox 운영 복구](outbox-production-deployment-20261103.md)를 우선한다.
> 최초 전송 오류는 HTTP 503이며 내부 원인은 미확정이다. 뒤늦은 409나 endpoint gate를
> 최초 장애의 확정 원인으로 해석하지 않는다. 브랜치 통합 작업은 이력을 보존하며,
> 새 분석·운영 DB 변경·수동 배포는 수행하지 않는다.

## 근거와 브랜치

사용자가 AWS 로그와 실제 `/opt/alpha/app.jar` 바이트코드를 대조한 관측을 제공했다.
2026-11-02 14:30~14:50 UTC에 분석 106의 전송 시도·HTTP 409 실패가 각각 494회였고,
다른 분석의 전송 이벤트는 없었다. 이는 사용자 제공 운영 근거이며 이번 작업에서
DB/JAR를 직접 조회한 결과로 표현하지 않는다.

운영 `main` 소스에는 첫 PENDING 한 건 선택, 실패 후 즉시 PENDING 복귀, retryable
무시가 존재한다. 반면 개발 브랜치 `develop`의 기준 `7e05c29`에는 재시도 가능 여부·
횟수 제한·deadline·현재 실행/terminal 확인이 이미 있다. `dev`는 없으므로 PR은
AGENTS의 Backend 인계 기준과 실제 개발 브랜치 이름에 따라 `develop`을 대상으로 한다.

## 수정 계획과 구현

1. DB 조회에서 RUNPOD_HTTP/PENDING이면서 `next_attempt_at <= now`인 행만 선택한다.
   ID 순서는 준비된 행 사이에서 유지한다. 대기 중인 첫 행을 읽고 반환하지 않는다.
2. 개별 행을 60초 예약한 뒤 트랜잭션 밖에서 HTTP를 호출하는 기존 구조를 유지한다.
   endpoint gate는 처리 후 현재 시각으로 해제한다. 한 행의 backoff가 endpoint 전체를
   잠가 준비된 다음 행까지 막지 않게 한다.
3. HTTP retry용 domain overload에 유한 시도 횟수와 30/60/120/240/300초 지수
   backoff를 둔다. Retry-After는 기존 client의 1~300초 범위에서 반영한다.
   최종 지연은 backoff와 Retry-After 중 큰 값이다. 누적 attempt_count는 보존한다.
4. 기존 `failure.isRetryable()`을 사용한다. 409 등 재시도 불가 오류는 해당 실패에서
   outbox FAILED가 되고, 아직 미인수된 현재 분석도 실패 처리한다. 일시 오류는 설정된
   `dispatchMaxAttempts`(기본 5)를 넘기지 않는다. 특정 capacity busy는 기존 별도
   지연 정책을 유지한다. Redis Stream의 2인자 failure 메서드는 변경하지 않는다.
5. 현재 실행·terminal 상태를 확인하고 claim으로 인수가 확인되면 응답 유실 때문에
   실행 중 분석을 실패시키지 않는 기존 develop 동작을 보존한다. HTTP를 기다리는 동안
   DB transaction/행 잠금을 유지하지 않는다. 새 실행 ID나 deadline을 만들지 않는다.

공개 API·DB schema·인증·동의 정책을 변경하지 않는다. 기록을 삭제하거나 106을
특수 처리하지 않는다. 보호 규칙 제거 commit 전체를 revert하지 않는다.

## 운영 main과 통합할 때 확인할 부분

운영 main에는 develop의 retryable/유한 시도/현재 실행/deadline 처리 일부가 없다.
따라서 이 PR을 main에 반영할 때 dispatcher 파일의 최종 구현에 해당 동작과 새 due-row
조회·endpoint 해제가 모두 남아 있어야 한다. 이 문서나 PR 병합만으로 운영 JAR가
고쳐졌다고 판단하지 않는다. 담당자는 최종 JAR/배포 revision의 실제 동작을 확인한다.

이미 만료된 107~114는 배포 후 자동으로 새 deadline을 받아 재실행되지 않는다.
기존 deadline 확인에 따라 종료될 수 있으며, 사용자의 정상 재시도로 새 시도를 만든다.
106의 HTTP 409 자체 원인과 후속 실행의 추론 성공은 별도 확인 대상이다.

## 확인·인계

자동 테스트·브라우저 자동화·자동 회귀·운영 DB 변경·분석 재실행·배포는 하지 않는다.
필요한 Java 컴파일과 소스/diff/문서 링크 정적 확인만 수행한다. 개발자는 409 행 뒤의
정상 요청 진행, 일시 오류 backoff 중 다른 due 행 진행, Retry-After, 시도 상한,
응답 유실 후 claim, 다른 세대/terminal 결과 보호를 직접 QA해야 한다.

이번 산출물은 새 branch·commit과 develop 대상 PR이다. 운영 변경·main 병합은 별도다.

실제 확인: JDK 21과 기존 Gradle cache로 `--offline --no-daemon compileJava -x test
-x compileTestJava`를 실행해 BUILD SUCCESSFUL을 확인했다. 문서·Git diff 정적 확인도
완료했다. 이는 실제 HTTP/DB 연동이나 회귀·품질 QA 통과를 뜻하지 않는다.
