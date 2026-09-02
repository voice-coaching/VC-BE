# Deployment Notes - voice

이 문서는 EC2 배포 환경에서 운영 보조 서비스에 접근하는 방식을 기록한다.

## Redis Streams for AI analysis

AI analysis uses a dedicated private Redis endpoint configured by
`ANALYSIS_REDIS_*`. It is separate from the Redis endpoint used by Spring Cache.
VC-BE starts the Stream outbox dispatcher and result consumer only when
`ANALYSIS_STREAM_ENABLED=true`. 이 값이 false이면 분석 요청 API는 `503`으로
fail-closed하며 PENDING 결과나 outbox event를 저장하지 않는다.

- Request stream: `analysis:request:v1`
- Result stream: `analysis:result:v1`
- Result DLQ: `analysis:result:dlq:v1`
- Cancellation tombstone: `analysis:canceled:v1:<opaque-request-event-id>`
- Result consumer group: `backend-analysis-result-workers`
- Authentication: Redis ACL plus TLS for cross-host traffic
- Resource bounds: 5-second connect/command timeouts, 64 KiB payload cap, bounded result DLQ
- Admission: `ANALYSIS_MAX_CONCURRENT_PER_USER` (default `3`), serialized by a PostgreSQL user-row lock
- Stale job recovery: `ANALYSIS_EXECUTION_TIMEOUT=PT15M`, `ANALYSIS_TIMEOUT_SWEEP_INTERVAL=PT1M`

The analysis worker must run on the same private network. Neither Redis endpoint is
publicly exposed. Secrets are injected at deployment time and never committed in an
environment file tracked by Git.

See [the versioned Backend-AI contract](../api/ai-redis-stream-contract.md) for
payloads, ACK timing, reclaim and retry policy.

The timeout must exceed the approved worst-case worker wall time plus queue and result
ingestion delay. Lowering it during a backlog can deliberately fail valid jobs, so
change it only after checking Redis group lag and pending counts. A session cancel,
history deletion, user withdrawal, or timeout makes the DB generation terminal and
persists a cancellation outbox record. Its Redis tombstone stops or skips the matching
AI generation; later Stream delivery is ACKed without restoring the result.

Redis TLS certificate verification is mandatory. Install a private CA through the JVM
trust store (`-Djavax.net.ssl.trustStore=...`) mounted from the deployment secret
mechanism; never use an insecure trust-all client. The command timeout must remain
greater than `ANALYSIS_RESULT_BLOCK`.

Management health and Prometheus endpoints bind to `127.0.0.1:9091` by default under
`/internal/actuator`. A reverse proxy or agent on the same host may scrape them. If
`MANAGEMENT_SERVER_ADDRESS` is changed to a non-loopback address, security-group and
network-policy rules must restrict `MANAGEMENT_SERVER_PORT`; it is not a public API.

Do not apply an unconditional `MAXLEN` to the request or result Stream. Before pruning,
operators must check consumer-group lag and pending entries, preserve the incident
window, and trim only IDs that every required consumer has ACKed and PostgreSQL has
persisted. The result DLQ alone uses approximate automatic trimming with
`ANALYSIS_RESULT_DLQ_MAXIMUM_LENGTH`.

`scripts/run_analysis_redis_integration.sh`는 digest-pinned 임시 Redis와 합성
TLS 인증서를 만들고 request XADD, result ingest 후 ACK를 검증한 뒤 모두 제거한다.
운영 자격 증명이나 실제 음성은 사용하지 않는다. 실행 전에 `JAVA_HOME`을 설치된
Java 21 JDK로 지정한다.

## Redis Cache and Redis Insight

Redis와 Redis Insight는 Docker Compose로 실행한다. 이 인스턴스는 Spring Cache 전용이며
AI 분석 Stream 트래픽에는 사용하지 않는다.

- Redis: `127.0.0.1:6379`
- Redis Insight: `127.0.0.1:5540`
- Redis password: `.env`의 `REDIS_PASSWORD`
- Application usage: Spring Cache 기반 조회 캐시

`docker-compose.yml`은 두 포트를 EC2 localhost에만 바인딩한다. 따라서 EC2 보안 그룹에서 `6379`, `5540`을 외부에 열지 않는다.

## Redis Cache Usage

Spring Boot 애플리케이션은 Redis를 조회 캐시 저장소로 사용한다.

- 공통 설정: `common/config/CacheConfig`
- 공통 key 유틸리티와 TTL provider 계약: `common/cache`
- 기능별 cache name/key/TTL provider: 각 기능의 `infrastructure/cache`
- 현재 적용 범위: 학습 콘텐츠 목록, 상세, 다음 콘텐츠, 콘텐츠 기반 추천, 기준 음성 목록, 클래스 목록, 클래스 상세, 클래스 단계 목록, 분석 결과 상세, 학습 세션 기준 분석 결과, 분석 세그먼트 목록, 마이페이지 학습 기록 목록/상세, 학습 통계, 강점 및 약점, 점수 변화 추이, 약점 기반 추천, 홈 오늘 학습 상태, 홈 추천, 홈 최근 학습, 홈 최근 클래스 진행률

TTL은 cache name별로 애플리케이션 설정에서 관리한다. 현재 학습 콘텐츠 목록, 다음 콘텐츠, 콘텐츠 기반 추천은 10분, 상세와 기준 음성 목록은 30분을 사용한다. 클래스 목록은 10분, 클래스 상세와 단계 목록은 20분을 사용한다. 분석 결과 상세와 세그먼트 목록은 30분, 학습 세션 기준 분석 결과는 10분을 사용한다. 마이페이지 학습 기록 목록과 학습 통계는 5분, 학습 기록 상세·강점 및 약점 집계·점수 변화 추이·약점 기반 추천은 10분을 사용한다. 홈 오늘 학습 상태와 최근 학습은 3분, 홈 최근 클래스 진행률은 5분, 홈 추천은 10분을 사용한다.

클래스 조회 캐시는 사용자별 진도 정보를 포함하므로 cache key에 `userId`를 포함한다. 클래스 시작, 진도 수정, 완료 처리 시 관련 클래스 상세와 단계 목록 캐시는 해당 사용자/클래스 기준으로 무효화하고, 클래스 목록 캐시는 전체 무효화한다.

분석 결과 조회 캐시는 완료된 분석 결과만 저장한다. 분석 진행 중이거나 실패한 결과는 캐시하지 않는다. 종합 피드백 재생성은 분석 상세 캐시를 무효화한다.

마이페이지 조회 캐시는 사용자별 학습 기록과 통계 결과를 포함하므로 cache key에 `userId`와 조회 조건을 포함한다. 학습 세션 완료/취소와 마이페이지 학습 기록 삭제는 마이페이지 조회 캐시를 전체 무효화한다.

홈 조회 캐시는 사용자별 대시보드 구성 데이터를 포함하므로 cache key에 `userId`와 추천 조회 조건을 포함한다. 학습 세션 생성/상태 변경/완료/취소, 클래스 진도 변경, 온보딩 목표 변경, 마이페이지 학습 기록 삭제는 관련 홈 조회 캐시를 무효화한다.

## PostgreSQL migration

운영 애플리케이션은 Flyway migration을 먼저 적용하고 Hibernate `ddl-auto=validate`로
entity/schema 일치를 확인한다. 빈 PostgreSQL에는 V0 core baseline부터 현재 version까지
순서대로 적용한다. 기존 pg_dump 기반 non-empty DB는 `baseline-on-migrate=true`,
`baseline-version=0`으로 표시한 뒤 V1 이상만 적용하므로, V0은 기존 table을 다시 만들지 않는다.
V1 이상 Flyway 이력이 이미 존재하는 배포에는 V0이 뒤늦게 추가된 과거 migration으로 보이므로
`ignore-migration-patterns=*:ignored`를 사용한다. 이 예외는 installed version보다 낮은 migration에만
적용하며 pending, missing, failed, checksum mismatch는 계속 배포를 실패시킨다.
배포 전에는 빈 PostgreSQL migration, 동일 DB 재기동 시 no-op migration, Hibernate validate를
모두 검사한다. migration 실패 상태에서 `ddl-auto=update`로 우회하지 않는다.

## Environment

EC2의 `.env`에는 Redis 비밀번호를 설정해야 한다.

```env
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<redis-password>

ANALYSIS_STREAM_ENABLED=true
ANALYSIS_REDIS_HOST=<private-analysis-redis-host>
ANALYSIS_REDIS_PORT=6379
ANALYSIS_REDIS_USERNAME=<analysis-acl-user>
ANALYSIS_REDIS_PASSWORD=<analysis-acl-password>
ANALYSIS_REDIS_SSL_ENABLED=true
ANALYSIS_REQUEST_STREAM=analysis:request:v1
ANALYSIS_RESULT_STREAM=analysis:result:v1
ANALYSIS_RESULT_CONSUMER_NAME=<unique-host-or-pod-name>
ANALYSIS_CANCELLATION_KEY_PREFIX=analysis:canceled:v1:
ANALYSIS_CANCELLATION_OUTBOX_POLL_INTERVAL=PT1S
ANALYSIS_AUTHORIZATION_KEY_ID=<active-key-id>
ANALYSIS_AUTHORIZATION_SIGNING_SECRET_BASE64=<secret-manager-value>
ANALYSIS_CONSENT_POLICY_REVISION=<active-consent-revision>
ANALYSIS_AUTHORIZATION_GRANT_TTL=PT5M
OBJECT_STORAGE_ENABLED=true
OBJECT_STORAGE_BUCKET=<private-recording-bucket>
OBJECT_STORAGE_REGION=ap-northeast-2
OBJECT_STORAGE_ENDPOINT=<https-provider-endpoint-if-required>
OBJECT_STORAGE_PATH_STYLE_ACCESS=false
OBJECT_STORAGE_RECORDINGS_PREFIX=recordings/
STORAGE_DELETION_DISPATCH_INTERVAL_MS=30000
STORAGE_UPLOAD_INTENT_SWEEP_INTERVAL_MS=60000
MEDIA_NORMALIZATION_ENABLED=true
MEDIA_NORMALIZATION_WORKSPACE_ROOT=/var/lib/voice-coach/media-normalization
MEDIA_NORMALIZATION_FFMPEG_BINARY=/usr/bin/ffmpeg
MEDIA_NORMALIZATION_FFPROBE_BINARY=/usr/bin/ffprobe
MEDIA_NORMALIZATION_PROCESS_TIMEOUT=PT30S
MEDIA_NORMALIZATION_MAXIMUM_INPUT_BYTES=104857600
MEDIA_NORMALIZATION_MAXIMUM_OUTPUT_BYTES=20971520
MEDIA_NORMALIZATION_MINIMUM_DURATION_MS=500
MEDIA_NORMALIZATION_MAXIMUM_DURATION_MS=180000
```

Stream 분석이 켜져 있는데 실제 S3 호환 저장소 또는 서명 설정이 빠지면 VC-BE는
개발용 URL로 대체하지 않고 기동에 실패한다. SDK 자격 증명은 배포 환경의 기본
credential chain으로 주입하며 `.env`나 문서에 값을 기록하지 않는다.
VC-BE storage role은 owner-bound upload presign/HEAD 외에 원본 GET, canonical WAV PUT,
처리 원본 및 실패 canonical 객체 DELETE 권한이 필요하다. 클라이언트는 normalized
prefix에 대한 PUT 권한을 받지 않는다. ffmpeg/ffprobe는 고정된 절대 경로의 검토된
binary를 사용하고 workspace root는 서비스 계정만 접근할 수 있어야 한다.

Spring Boot 애플리케이션을 EC2 호스트에서 JAR로 직접 실행하면 `REDIS_HOST=localhost`를 사용한다.

Spring Boot 애플리케이션을 Docker Compose 내부 서비스로 함께 실행하는 경우에는 Redis 컨테이너 이름을 기준으로 `REDIS_HOST=redis`를 사용한다.

## Start Services

```bash
docker compose up -d
```

`REDIS_PASSWORD`가 설정되어 있지 않으면 Redis 컨테이너는 시작되지 않는다.

## Local Access Through SSH Tunnel

로컬 PC에서 Redis Insight에 접근할 때는 SSH 터널을 연다.

```bash
ssh -i <pem-key> -L 5540:127.0.0.1:5540 ubuntu@<ec2-public-ip>
```

브라우저에서 아래 주소로 접속한다.

```text
http://localhost:5540
```

Redis Insight에서 Redis 서버를 등록할 때는 다음 값을 사용한다.

```text
Host: redis
Port: 6379
Username: default
Password: <redis-password>
```

Redis Insight에서 `redis` 호스트를 찾지 못하면 `voice-redis`를 사용한다.

## Security Group

권장 인바운드 규칙은 다음과 같다.

| Port | Source | Purpose |
| --- | --- | --- |
| 22 | Developer IP only | SSH and SSH tunnel |
| 80 | Public, only when needed | HTTP |
| 443 | Public, only when needed | HTTPS |

다음 포트는 외부에 공개하지 않는다.

- `6379`: Redis
- `5540`: Redis Insight

Redis Insight를 터미널 없이 상시 외부 접근해야 하는 경우에는 `5540`을 직접 공개하지 않고 HTTPS reverse proxy와 별도 인증을 먼저 구성한다.
