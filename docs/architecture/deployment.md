# Deployment Notes - voice

이 문서는 EC2 배포 환경에서 운영 보조 서비스에 접근하는 방식을 기록한다.

## Redis and Redis Insight

Redis와 Redis Insight는 Docker Compose로 실행한다.

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
- 현재 적용 범위: 학습 콘텐츠 목록, 상세, 다음 콘텐츠, 콘텐츠 기반 추천, 기준 음성 목록, 클래스 목록, 클래스 상세, 클래스 단계 목록, 분석 결과 상세, 학습 세션 기준 분석 결과, 분석 세그먼트 목록, 마이페이지 학습 기록 목록/상세, 학습 통계, 강점 및 약점, 점수 변화 추이, 약점 기반 추천

TTL은 cache name별로 애플리케이션 설정에서 관리한다. 현재 학습 콘텐츠 목록, 다음 콘텐츠, 콘텐츠 기반 추천은 10분, 상세와 기준 음성 목록은 30분을 사용한다. 클래스 목록은 10분, 클래스 상세와 단계 목록은 20분을 사용한다. 분석 결과 상세와 세그먼트 목록은 30분, 학습 세션 기준 분석 결과는 10분을 사용한다. 마이페이지 학습 기록 목록과 학습 통계는 5분, 학습 기록 상세·강점 및 약점 집계·점수 변화 추이·약점 기반 추천은 10분을 사용한다.

클래스 조회 캐시는 사용자별 진도 정보를 포함하므로 cache key에 `userId`를 포함한다. 클래스 시작, 진도 수정, 완료 처리 시 관련 클래스 상세와 단계 목록 캐시는 해당 사용자/클래스 기준으로 무효화하고, 클래스 목록 캐시는 전체 무효화한다.

분석 결과 조회 캐시는 완료된 분석 결과만 저장한다. 분석 진행 중이거나 실패한 결과는 캐시하지 않는다. 종합 피드백 재생성은 분석 상세 캐시를 무효화한다.

마이페이지 조회 캐시는 사용자별 학습 기록과 통계 결과를 포함하므로 cache key에 `userId`와 조회 조건을 포함한다. 학습 세션 완료/취소와 마이페이지 학습 기록 삭제는 마이페이지 조회 캐시를 전체 무효화한다.

## Environment

EC2의 `.env`에는 Redis 비밀번호를 설정해야 한다.

```env
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<redis-password>
```

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
