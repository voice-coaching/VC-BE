# 계약 검증 복원 후 영속 큐 초기화 장애 — 2026-11-03

## 시점과 확인 범위

2026-11-03 08:30 KST 이후 AWS 로그·현재 JAR·기존 DB/파일 큐·AI readiness를
읽기 전용으로 조사했다. 운영 재시작·배포·DB 쓰기·새 분석은 수행하지 않았다.

| KST | 관측 |
| --- | --- |
| 08:38:30 / 08:38:39 | 분석 120 / 121 생성 |
| 08:39:14 / 08:39:54 | 120 / 121 COMPLETED, canonical result와 handoff 수신 기록 존재 |
| 10:30:24 | VC-FE #99 병합 |
| 10:30:45 | VC-BE #184 병합, main `00f0f3b48362500fcbc9ecb6cda04956037e0fd7` |
| 10:31:03 | intelligentAI #53 병합 |
| 10:32:34 | 최초 canonicalDeliverySpool init 실패 기록 |
| 10:49:49 | 같은 초기화 실패와 NRestarts 각각 25회 확인 |

Backend 현재 app.jar 링크는 위 main release JAR다. Deploy production run
41560206931은 success였지만 실제 서비스는 시작 실패를 반복하고 management health에
연결되지 않았다. workflow 성공과 애플리케이션 readiness를 동일하게 해석하지 않는다.

## 직접 원인

#184는 RunPodContract의 JSON/schema 검증을 복원했다. CanonicalDeliverySpool.start는
기존 디스크 document.json에도 같은 parser를 호출하지만 문서별 예외 경계가 없었다.
한 문서의 422가 @PostConstruct 밖으로 전파돼 Spring context 전체가 종료됐다.

실제 운영 JAR의 Java parser로 기존 큐 18개를 읽기 전용 대조했다. 17개는 통과했고,
과거 분석 97의 v6 projection만 `RunPodContractException(422, VALIDATION_FAILED)`였다.
실패 호출 위치는 RunPodContract.validate → parse → CanonicalCallbackDocument.parse
→ CanonicalHandoffDocument.parse다. 배포된 networknt validator의 실패 경로는
`$.score`, `$.score.overallScore`, `$.score.validity`, `$.score.reason`의
oneOf/type/const/required 조건이다. 결과 값·원문은 출력하거나 변경하지 않았다.

97은 DB에서 이미 FAILED / CANONICAL_EXECUTION_FAILED이고 canonical result도 존재한다.
새 분석의 모델 실패가 아니라 과거 저장 자료가 새 시작 경로에서 전체 API를 막은 것이다.
FE #99의 타입·홈 진행률 수정은 이 Java 초기화 호출 경로에 없다.

## 별도의 AI 접수 차단 조건

기존 endpoint의 GET /health/handoff는 HTTP200이지만 executorConfigured=true,
admissionEnabled=false, supportedLearningFocuses 누락, schemaDigests 11개였다.
GET /health/audiovisual은 HTTP503이었다. #184의 peer 조건에서는 접수 불가다.
#53의 GitHub 병합은 확인했지만 현재 응답에 새 필드가 없으므로 실제 AI 배포 완료를
확정할 수 없다. 배포/재기동 누락 또는 다른 revision의 응답 가능성을 운영에서 확인해야 한다.
Backend를 복구한 뒤에도 AI 배포 및 실제 admission 조건 회복이 별도로 필요하다.
이를 Backend에서 무조건 true로 우회하지 않는다.

10:31:06의 UserReader.findById 관련 InvalidDataAccessApiUsageException 두 건도
관측됐지만 큐 초기화 실패의 root cause는 아니다. 그 개별 요청 원인은 미확정이다.

## 수정

`CanonicalDeliverySpool.java`에서 시작 시 문서별 확정 계약 오류 400/413/422를
격리한다. 해당 디렉터리에 status만 담은 `quarantined` marker를 atomic write/fsync하고
원본 document/receipt/artifact를 보존한다. 유효한 나머지 항목의 복구와 API 시작은 계속한다.
재시작 시 marker를 복원하고 격리 항목을 자동 재처리하지 않는다. 같은 handoff ID의 새
수신은 기존 409 RESULT_EVENT_CONFLICT로 거절해 원본 덮어쓰기를 막는다.
폴더 ID와 문서 ID도 일치해야 한다. 로그는 handoff ID/status/고정 reason만 남긴다.

계약 검증 자체·정상 결과·기존 DB 상태는 변경하지 않는다. 인프라 I/O 실패와 다른
오류를 계약 오류로 삼키지 않는다. 격리 해제·과거 결과 수정·재분석은 별도 검토 사항이며
자동 수행하지 않는다. 격리 원본은 디스크 공간을 계속 사용하며 자동 삭제하지 않는다.

## 검증과 제한

Java 21 `--offline --no-daemon compileJava compileTestJava bootJar -x test` 성공.
git diff --check 및 격리 marker/재시작/덮어쓰기/예외 범위의 정적 검토를 수행했다.
운영 자료 진단은 기존 배포 JAR parser와 validator를 사용했고 임시 classpath는 정리했다.
이는 수정본의 실제 기동 QA가 아니다. 자동 회귀 테스트·fixture·브라우저 자동화·새 추론은
실행/생성하지 않았다. 수정본은 PR로 제출하며 병합·배포·서비스 회복은 아직 수행하지 않았다.
