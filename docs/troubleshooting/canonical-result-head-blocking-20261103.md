# 결과 저장 정체와 취소 전송의 스케줄러 점유 — 2026-11-03

## 근거와 범위

최초 503 내부 원인은 미확인으로 유지한다. 이번 작업은 추가 AI 수정이나 기존 요청
재분석이 아니라, 별도로 확인된 Backend 결과 저장 정체를 수정한다.

2026-11-03 01:50~01:58 KST AWS 읽기 전용 조사에서 전송기·timeout 수정이 유지된
운영 JAR와 신규 118/119의 IO timeout/502를 확인했다. 01:54:24에 역방향 터널 리스너가
교체된 뒤 접수·claim이 회복됐다. scheduling-1은 취소 HTTP를 기다렸고 취소 원장에
11건이 남아 있었다. 이 작업이 터널을 재시작한 것은 아니다.

119 결과는 파일 큐에 수신됐으나 DB handoff는 없었다. 선행 72는 분석 COMPLETED,
handoff RECEIVED/DELIVERY_UNAVAILABLE, attempts 4277이었다. committed_at은 2026-10-04
기록이 남아 있고 archive job 및 artifact 5건도 존재했다. 재등록이 COMMITTED를
RECEIVED로 바꾸며 archive INSERT도 중복을 처리하지 않는 소스를 운영 class hash와
대조했다. 실제 실패 SQL은 기존 catch가 상세를 숨겨 직접 확정하지 못했다.

## 파일별 변경

- `CanonicalDeliverySpool`: 동일 ID/identity/digest의 entry를 재사용하여 수신·submitted·
  committed·backoff 상태와 원본 파일을 초기화하지 않는다. 같은 artifact bytes는
  멱등 승인하고 다른 bytes/metadata/identity는 기존 계약 오류로 거절한다.
- `CanonicalHandoffStore`: analysis → handoff 순서로 잠그며 같은 문서는 현재 상태를
  반환한다. RECEIVED/VERIFYING/COMMITTED/REJECTED/REVOKED의 상태·claim·수신 시각·
  문서를 덮어쓰지 않는다. STAGING의 누락 원본만 검증 후 채우고 신규 원본은 다중 행
  INSERT로 넣는다. 전체 원본이 있어야 RECEIVED가 된다. V61 이후 보조 unique가 없는
  main에서도 같은 execution/event의 다른 handoff를 서비스 경계에서 거절한다.
- `CanonicalCallbackCommitter`: 같은 event/execution/identity 및 raw/payload digest의
  결과는 IGNORED_DUPLICATE다. 점수·분석 상태·완료 후처리를 재적용하지 않는다.
  다른 문서는 RESULT_EVENT_CONFLICT로 거절한다. archive job/artifact의 ON CONFLICT
  DO NOTHING은 기존 B2 checkpoint·상태를 보존한다.
- `CanonicalDeliveryWorker`: 재시작 시 DB terminal 상태부터 확인한다. 대기·오류 항목을
  건너뛰고 cursor로 순환하여 회차당 최대 25개를 확인한다. 신규 DB 인계는 회차당 한
  건이다. verifier가 save backoff를 초기화하지 않는다. 실패 지연은 1/2/4/8/16/32/60초,
  이후 60초다. 유효 원본을 자동 삭제하지 않는다.
- `CanonicalHandoffWorker`: 현재 VERIFYING claim과 유효 시간을 확인한 뒤 commit하고,
  실패 재예약도 같은 claim일 때만 수행한다. 예외 타입·SQLSTATE만 로그에 남긴다.
- `RunPodAnalysisCancellationOutboxDispatcher`: 전용 단일 daemon executor에서 취소
  HTTP를 수행한다. 동시에 한 batch만 실행해 공용 scheduler는 HTTP를 기다리지 않는다.
  기존 ID·DB 예약·취소 재시도 정책을 유지하며 종료 시 executor를 정리한다.

공개 URL/schema, 채점, AI 모델, DB migration, 기존 전송/timeout 정책은 변경하지 않는다.
전체 보안 정책이나 V61을 되돌리지 않는다. 로그에 SQL·URL·토큰·결과 원문을 넣지 않는다.

## 검증과 개발자 인계

기존 사용자 제약에 따라 자동 테스트·새 테스트 생성·실제 분석·운영 DB 쓰기·배포는
하지 않는다. Java 21 production/기존 test source 컴파일, bootJar와 diff·호출·잠금·SQL
정적 검토를 수행한다. 이는 동시성·실제 PostgreSQL·품질 QA 통과를 뜻하지 않는다.
최신 main ae254ff 기준 새 브랜치와 main 대상 PR로 인계하며 병합·배포는 별도다.

실제 확인: Java 21에서 `--offline --no-daemon compileJava compileTestJava bootJar -x test`
명령이 BUILD SUCCESSFUL이었다. 기존 test source를 최초 컴파일한 뒤 최종 production
변경에 대한 증분 컴파일·패키징도 통과했다. `git diff --check`, 잠금 순서·상태 전이·
재전달 호출자·SQL의 conflict target과 V61 이후 primary key 유지 여부를 정적으로
대조했다. test task는 실행하지 않았고 테스트 파일도 생성·수정하지 않았다.

개발자는 다음을 확인한다.

1. 완료 문서 재전달/재시작에서 terminal 상태·수신 시각·원본·archive checkpoint 보존.
2. 같은 결과는 점수·completed_at을 재적용하지 않고 다른 digest/identity는 거절.
3. 선행 대기/실패 중에도 다음 결과의 DB_RECEIVED → COMMITTED 진행과 backoff 유지.
4. 이미 RECEIVED로 되돌아간 72 같은 항목의 정상 멱등 commit 복구. 내용이 충돌하거나
   다른 저장 오류가 있으면 원본을 보존하고 새 안전 로그로 진단한다. 직접 행 수정이나
   재분석으로 우회하지 않는다.
5. 취소 HTTP 지연 중 신규 접수·timeout 스케줄링과 종료 중 중복 batch 방지.
