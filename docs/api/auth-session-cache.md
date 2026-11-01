# JWT 활성 세션 조회 최적화

이슈 #152. JWT 서명·만료·issuer/type 검증은 매 요청 유지한다. 기존에는 보호 요청마다 PostgreSQL의 활성 세션을 확인했다. 새 경로는 공유 Redis에서 현재 SID와 세션 만료 시각을 확인하고, 캐시 미스나 안전성을 확인할 수 없는 경우 DB를 조회한다. raw access/refresh token, refresh hash, 이메일, 비밀번호는 캐시에 넣지 않는다.

## 요청과 세션 변경

캐시 적중 시 세션 확인용 SQL은 0회다. 미스에서는 요청에 들어온 SID가 아닌 DB의 **현재** SID/만료 시각만 읽는다. 캐시가 알고 있는 SID와 토큰의 SID가 다르거나 세션이 만료되면 기존 401을 반환한다. JWT 검증 실패는 캐시/DB 조회 전에 기존대로 거부한다. 비동기 응답 재개 때의 검증도 유지한다.

성공 캐시와 부재 캐시의 기본 수명은 1분이다. 이 수명은 로그아웃 반영 지연 시간이 아니다. 로그인 교체, refresh 회전, 로그아웃, 탈퇴의 기존 RefreshTokenWriter를 통해 다음 절차를 수행한다.

1. application 트랜잭션 내 `SELECT 1`로 DB 트랜잭션이 실제 시작됐음을 보장한다. 초기화 과정이 아직 DB에 나타나지 않은 writer를 완료된 writer로 오인하지 않게 한다.
2. DB 세션 변경 전에 Redis 사용자 상태에 소유자가 있는 변경 표식을 추가하고 기존 값/진행 중 캐시 채우기를 무효화한다.
3. 변경 표식이 있는 동안 모든 인스턴스는 해당 사용자를 DB로 검증한다. 표식에는 만료를 두지 않아 장시간 트랜잭션 중에도 자동으로 유효 판정이 재개되지 않는다.
4. DB commit 또는 rollback이 끝나면 캐시를 다시 무효화하고 본인 표식만 제거한다. 동시 writer가 남아 있으면 DB 검증을 계속한다. 다음 요청이 현재 DB 상태를 다시 읽는다.

캐시 채우기도 Redis에서 발급한 소유권과 현재 세대를 확인한 경우에만 게시한다. 로그아웃과 교차한 이전 DB 조회가 오래된 세션을 다시 캐시에 넣을 수 없다. 기존 application 트랜잭션 안의 세션 조회는 오래된 DB snapshot을 게시하지 않도록 캐시를 우회한다. refresh는 같은 SID를 유지하므로 기존 access token이 만료 전까지 유지되는 기존 정책도 동일하다.

## 장애와 재시작

- 조회/캐시 게시 실패: DB 확인으로 처리한다. 유효성 확인 없이 허용하지 않는다.
- 변경 표식 기록 실패: DB 세션 변경을 진행하지 않고 503 `AUTH_SESSION_STORE_UNAVAILABLE`을 반환한다. `Cache-Control: no-store`, `Retry-After: 1`을 포함한다. Redis 장애 중에도 로그인/갱신/로그아웃을 항상 성공시키는 설계는 아니다.
- 완료 후 Redis 오류/불확실한 DB 완료: 표식을 성급하게 해제하지 않는다. DB 확인을 계속하며 다음 안전한 초기화에서 복구할 수 있다.
- 시작, Redis run_id 변경, 메타데이터 유실, eviction 증가: 기존 값을 그대로 신뢰하지 않는다. 공유 초기화 gate로 캐시를 막고, DB의 기존 트랜잭션이 끝난 것을 확인한 뒤 새 세대를 연다. 이전 세대의 캐시 값/완료되지 않은 과거 표식은 새 유효 판정에 사용하지 않는다. 초기화를 확인하지 못하면 DB 확인을 유지한다.
- DB barrier는 `pg_stat_activity`에서 현재 DB/접속 역할의 이전 트랜잭션을 확인한다. `track_activities`가 꺼져 있으면 캐시를 열지 않는다. 자신의 JDBC 트랜잭션은 제외한다. 실제 SQL 트랜잭션을 먼저 시작하는 1번 절차와 함께 사용해야 한다.

기존 PostgreSQL 세션 데이터가 진실의 원천이다. Redis에서 복구된 과거 snapshot에 유효 SID가 남아 있어도 run_id/세대/DB barrier 검증을 거친다. 서로 독립적인 writable Redis primary를 동시에 사용하는 split-brain 구성은 지원하지 않는다.

## 설정과 배포 조건

- `AUTH_SESSION_CACHE_ENABLED`: 기본 `true`. `false`이면 기존 DB 확인 경로를 사용한다. 테스트 기본 프로필은 false이며 전용 통합 테스트에서 실제 PG/TLS Redis로 활성화한다.
- `auth.session-cache.namespace`: 기본 `voice-coaching:auth:{sessions}:v1`. 동일 DB를 쓰는 backend들은 같은 namespace/Redis database를 써야 하며 다른 환경/DB에는 별도 namespace를 사용한다. Redis Cluster hash tag를 보존하지만 현재 client 설정은 standalone primary만 지원한다.
- `auth.session-cache.ttl`: 기본 `PT1M`, `loading-ttl`: `PT5S`, command/connect timeout: 각각 `PT0.3S`, bootstrap timeout: `PT3S`.
- 기존 `spring.data.redis`의 host/port/database/username/password/url/TLS 설정을 재사용한다. 별도 제한 시간의 전용 client를 소유하며 기존 Spring Cache/AI Stream factory Bean 선택을 바꾸지 않는다. TLS 인증서 검증은 유지한다. SSL bundle/Sentinel/Cluster/master-replica 별도 설정은 지원하지 않으므로 설정 시 명시적으로 시작을 거부한다.
- Redis primary의 `noeviction` 및 INFO/CONFIG GET/EVAL/SCRIPT 권한이 필요하다. 메모리 정책이 부적합하거나 DB barrier를 확인하지 못하면 공유 gate가 닫혀 DB 확인을 유지한다. 실제 운영 Redis를 읽기 전용으로 확인했으며 현재 primary/noeviction 조건을 충족한다. 정책/권한을 변경하지 않았다.
- rollout/rollback은 동일 DB의 모든 세션 writer가 같은 버전·설정으로 동작하도록 조율한다. 캐시를 사용하는 노드와 무효화를 하지 않는 과거/비활성 노드를 섞어 쓰지 않는다. 현재 단일 backend 서비스는 기존 중지/시작 배포 절차를 사용한다. 직접 DB 세션 수정·복원은 캐시 비활성화/전환을 함께 조율해야 한다.

신규 HTTP 경로나 DB migration은 없다. 기존 정상 응답/쿠키/JWT claim/Access·Refresh 만료 정책은 유지한다. 로그인·회원가입·SNS 로그인·토큰 갱신·로그아웃·탈퇴의 503 계약을 OpenAPI에 추가한다. 프론트 변경은 필요하지 않다. 운영 성능 개선 수치는 팀원 병합·배포 후 같은 조건에서 재측정해야 한다.

## 설계 근거

일반 cache-aside는 DB 조회와 무효화가 교차할 때 오래된 값을 다시 게시할 수 있다. 이 구현은 소유권·변경 표식·세대와 DB transaction barrier를 함께 사용한다. [Redis cache consistency](https://redis.io/blog/cache-consistency-strategies/), [Spring transaction synchronization](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html).
