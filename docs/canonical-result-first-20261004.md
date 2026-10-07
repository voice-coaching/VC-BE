# v5 결과 공개와 DB 저장 분리

후속: [결과 직접 공개](canonical-direct-result-20261004.md). delivery ON의 사용자 대기에서 파일 인계와 AWS 의미 재검증을 제거한다. 아래는 첫 결과 우선 공개 구현 당시의 경로다.

상태: 구현, 기본 OFF. 운영 활성화·실제 녹음 QA·성능 재측정은 별도다. DB migration은 추가하지 않는다.

## 원인

2026-10-04 testvideo1 분석 71은 인계 준비 후 서버 실패까지 694.435초가 걸렸다. 이 값은 단일 SQL 시간이 아니라 반복 timeout/rollback과 실행 기한 소진을 포함한다. 기존 inline 근거 5개 성공 경로는 정적으로 66 SQL 왕복이며, 운영 DB의 연결 유지 `SELECT 1` 표본 약 169ms를 적용하면 왕복만 약 11.18초다. 수신 트랜잭션과 RunPod HTTP timeout은 각각 10초였다. handoff·archive 행은 0건으로 B2 단계에는 도달하지 않았다.

## 결과 공개 경로

`RunPod → 인증/계약/hash 검사 → AWS 영속 파일 큐 fsync → RECEIVED`로 접수한다. 이 모드의 handoff POST/PUT/seal에는 SQL을 실행하지 않는다. 현재 서버 인증과 worker/문서 식별자 검사는 유지한다. 알려진 파일 큐 항목의 status는 파일 큐에서 읽고, 이전 인계나 없는 항목의 status 조회는 기존 DB 경로로 확인한다.

독립 verifier worker가 DB의 현재 소유자·선택 녹음·request/execution·원본 요청·수신 시점의 deadline/lease를 **조회**하고 기존 의미 검증기를 실행한다. 검증 후 현재 소유 관계를 다시 확인한 결과만 공개한다. 결과 API도 로그인·소유권·시도 변경을 재검사한다. DB 쓰기 완료를 기다리지 않지만 DB 읽기 및 독립 검증은 여전히 필요하다. DB 전체 장애에서도 새 결과를 공개한다는 계약이 아니다.

별도 save worker가 검증된 큐 항목을 기존 handoff DB에 멱등 저장한다. inline 원본은 근거별 반복 `stage/owned/active` 대신 다중 행 INSERT로 저장한다. 이후 기존 worker가 결과와 archive job을 함께 commit한다. 같은 프로세스에서 이미 검증한 handoff ID/digest는 의미 검증을 중복 실행하지 않는다. B2 보존·검증은 기존 후처리를 유지한다.

DB 저장 오류는 backoff(최대 60초)로 재시도하며 공개 결과를 대기 화면으로 되돌리지 않는다. 현재 시도 취소·교체 또는 검증 실패는 공개를 중단한다. 파일 수신 이후의 실행 timeout sweeper는 이 시도를 만료시키지 않는다. 제외 대상은 조회 단계에서 빼서 다른 만료 요청의 처리를 막지 않는다.

## API 변경

| API | 추가 동작 |
|---|---|
| `GET /api/training-sessions/{id}/analysis/status` | `resultAvailable=true`이면 DB status가 PROCESSING이어도 상세 결과 조회 가능. 저장 완료 여부를 뜻하지 않는다. |
| `GET /api/v3/analyses/{id}` | view v2에 선택 필드 `persistenceStatus` 추가. 기능 OFF에서는 필드를 생략한다. |
| 내부 handoff POST/PUT/seal | wire schema 및 URL은 그대로다. 기능 ON의 RECEIVED는 AWS 파일 큐에 원본이 영속 수신됐다는 뜻이다. 검증/DB commit 완료를 뜻하지 않는다. |

`persistenceStatus`: `NONE`(검증 결과 없음), `SAVING`(검증 결과 공개, DB 저장 진행), `RETRYING`(후처리 재시도 경험 있음), `SAVED`(DB 결과 조회). SAVING/RETRYING의 `jobStatus`는 검증된 분석 결과의 COMPLETED/FAILED다. `actions`는 모두 false, 사유는 `RESULT_PERSISTENCE_PENDING`이다. 점수·코칭·scoped ID·근거는 기존 검증된 projection을 사용한다. 저장 전 학습 완료나 다음 과정 완료를 승인하지 않는다. DB 저장 후 기존 완료 정책으로 돌아간다.

FE는 `resultAvailable`에서 분석 polling을 끝내고 점수·피드백을 먼저 렌더링한다. 저장 상태는 5초 간격으로 관찰한다. 화면을 닫아도 서버 저장은 계속된다. 학습 완료 요청은 화면에서 SAVED를 확인하거나 재진입했을 때 실행한다. 일반 학습 화면에서 이탈한 즉시 학습 완료가 보장되는 것은 아니다.

## 운영 준비와 복구

1. 새 FE를 먼저 배포한다. 구 FE의 strict view parser는 추가 필드를 거절하므로 먼저 기능을 켜면 안 된다.
2. 신규 분석 접수를 잠시 끄고 기존 STAGING/진행 요청을 drain한다. 영속 EBS에 서비스 사용자 소유의 전용 디렉터리를 만들고 POSIX 0700을 적용한다. 예: `/var/lib/alpha-canonical-delivery`. 심볼릭 링크·상대 경로·공유 소유자를 허용하지 않는다.
3. `analysis.canonical.delivery.directory`와 `analysis.canonical.delivery.budget-bytes`를 설정한다. 기본 한도는 512 MiB이며 85,065,728~17,179,869,184 bytes만 허용한다. 기존 DB handoff budget과 별도다. 실제 파일 시스템 여유와 최대 요청 예약량도 readiness가 확인한다.
4. 새 BE 배포 후 `analysis.canonical.delivery.enabled=true`로 활성화하고 readiness를 확인한 뒤 신규 접수를 연다. BE develop merge의 자동 배포와 이 설정 활성화는 별개다. RunPod 계약/모델/이미지 변경은 필요하지 않다.
5. 개발자가 실제 요청의 `AVAILABLE` → 화면 결과 → `DB_RECEIVED` → DB COMMITTED/SAVED 및 B2 후처리를 확인한다. 저장 지연·DB 복구·서버 재시작·재시도/취소·로그인 변경도 확인한다. 이 문서는 QA 통과를 선언하지 않는다.

프로세스는 디렉터리의 배타적 파일 잠금을 획득한다. **단일 EC2/단일 BE 인스턴스 전용**이며 다중 인스턴스·EC2 디스크 소실까지 견디는 분산 큐가 아니다. EBS 및 백업을 유지해야 한다. 재시작 시 원본을 다시 검증하며 DB에 동일 digest가 COMMITTED라면 재저장하지 않는다. verifier/save lane을 별도로 두어 느린 DB 쓰기가 다음 결과 검증을 직렬로 막지 않는다.

미저장 항목이 남은 상태에서 디렉터리를 바꾸거나 기능을 끄거나 이전 JAR로 되돌리지 않는다. 원본은 0600 파일로 기록하며 자동 삭제하지 않는다. 완료·거절·불완전 항목도 보존하므로 사용량과 미처리 연령을 운영에서 관리해야 한다. 한도 도달 시 신규 접수는 차단된다. `.staging-*` 잔여도 자동 삭제하지 않으며 실제 디스크 여유 검사에 반영된다. 별도 보존/정리 정책을 승인하기 전 이 큐를 무제한 운영 가능한 저장소로 간주하지 않는다.

## 확인

Java 21 `compileJava compileTestJava` 범위로 컴파일 확인한다. 기존 timeout sweeper 테스트의 생성자만 새 의존성에 맞췄다. 자동 회귀 테스트·브라우저 자동화·실제 추론은 실행하지 않는다. 로그는 analysis/execution ID, 단계, 시도 수, 경과 시간만 기록하고 원본·토큰은 기록하지 않는다. 개선 후 초 단위 수치는 운영 재측정 전 미확인이다.

## 2026-10-04 기존 STAGING 인계 재개 보완

71번의 최초 inline 인계 시간 초과는 위 영속 파일 큐와 다중 행 INSERT 경로로 해결되어, 이후 72·73번의 DB 인계와 B2 보존이 운영에서 완료됐다. 다만 기존 `STAGING` 행에 동일 인계를 다시 POST하는 경로는 남은 원본마다 `stage()`의 소유권 조회와 잠금을 반복했다. 영속 수신 뒤 DB 저장을 재시도할 때에도 이 경로가 Pod lease를 요구해, 유효한 파일 큐 소유권이 있어도 재개하지 못할 수 있었다.

후속 수정은 기존 `STAGING`의 원본 메타데이터를 한 번 잠가 읽고, 전달된 원본의 크기·SHA와 이미 저장된 bytes의 동일성을 확인한 뒤 누락 원본을 한 SQL 문으로 채운다. 직접 인계는 기존 실행·lease 검사를 유지하고, 검증된 영속 수신의 DB 재시도는 파일 큐 소유권과 현재 사용자·녹음·시도를 검사한다. 전체 원본이 모이면 같은 트랜잭션에서 `RECEIVED`로 전이한다. URL·schema·응답·보존 정책과 DB migration은 바뀌지 않는다. 이 재개 경로의 운영 실행·부하 수용과 새 소요 시간은 아직 확인되지 않았다.

영속 파일 큐의 원본을 다시 읽을 때 크기·SHA 검증 실패는 422로 유지해 해당 항목을 거절한다. 파일 접근 실패는 기존 503 재시도 경로에 둔다. DB handoff 검증 worker에서도 계약 파싱·원본 검증의 확정적인 422 오류를 `REJECTED`와 `canonical_handoff_invalid`로 마감한다. 일시적인 저장소·검증기 장애는 계속 재시도한다. 이 분류의 운영 재현과 결과 품질 QA는 아직 수행하지 않았다.
