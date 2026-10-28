# 공용 PortOne TEST 환경

관련 이슈: #131. 기존 결제 API를 별도 DB·JWT·테스트 계정으로 실행하는 관리자용 운영 자료입니다. 신규 API나 운영 회원의 유료 등급을 추가하지 않습니다.

## 팀 연동

- API base: `https://api.voice-coaching.site/payment-test`
- Swagger: `https://api.voice-coaching.site/payment-test/swagger-ui/index.html`
- PortOne V2 **테스트** 웹훅: `https://api.voice-coaching.site/payment-test/api/payments/webhooks/portone`
- Content Type: `application/json`, 웹훅 버전: `2024-04-25`.

테스트 계정으로 위 base의 `POST /api/auth/login`을 호출한 후 반환된 Access Token을 사용합니다. 운영 로그인/토큰은 이 환경에서 사용할 수 없습니다. 계정 정보는 저장소·PR에 기록하지 않고 승인된 비공개 경로로 공유합니다. 같은 계정을 공유하면 단일 세션 정책으로 이전 로그인이 만료됩니다.

프론트의 테스트 빌드는 로그인과 결제 요청을 함께 이 base로 보내야 합니다. 현재 허용 Origin은 `https://vc-fe.vercel.app`, `http://localhost:3000`입니다. 쿠키 갱신에는 credentials 전송이 필요하며 Refresh Cookie 경로는 `/payment-test/api/auth`로 격리됩니다. 이번 작업에 프론트 수정은 포함되지 않습니다.

1. 테스트 로그인 → 주문 생성(`Idempotency-Key` 필수).
2. 기존 PortOne V2 SDK 계약으로 **테스트 채널** 결제창 호출.
3. 주문 검증 또는 웹훅 반영 후 `GET /api/users/me`에서 TEST 등급 확인.
4. `GET /api/users/me/payment-orders`로 내역 확인.

추가 코드: `POST /api/payment-orders/{orderId}/cancel`은 [전액 취소 계약](../../docs/api/payment-cancellation-api.md)을 따른다. V51/JAR/Nginx 변경을 서버에 적용하기 전에는 공개 환경에서 사용할 수 없다. 주문과 요청 원장을 공유하며 실제 취소 승인 테스트는 별도다.

후속 `POST /api/payment-orders/{orderId}/refund-requests`, `GET /api/refund-requests/{requestId}`는 [전액 환불 신청·조회](../../docs/api/payment-refund-api.md)를 따른다. JAR·Nginx와 `SPRINGDOC_PATHS_TO_MATCH`에 `/api/refund-requests/**`를 반영해야 한다. 설정 예시만 수정했으며 서버 환경변수를 자동 변경하지 않는다.

금액·응답·오류 계약은 [테스트 결제 명세](../../docs/api/test-payments-api.md), [내역 명세](../../docs/api/payment-history-api.md)를 따릅니다. 자동 갱신 없는 테스트 월간 이용권입니다. 웹훅 호출 테스트 200은 수신 확인이며 실제 승인 건의 등급 반영까지 증명하지 않습니다.

## 격리와 자원 제한

| 대상 | 구성 |
|---|---|
| backend | `alpha-payment-test.service`, 전용 OS 사용자, API loopback 18180 / 관리 19191 |
| DB | `alpha-payment-test-db`, 별도 PostgreSQL 18 DB·볼륨·비슈퍼유저 앱 역할, loopback 15435 |
| 비밀 | root 전용 `/etc/alpha-payment-test/`, 별도 DB 비밀번호·JWT 키/issuer, PortOne TEST 키 |
| 데이터 | `/var/lib/alpha-payment-test/`, 운영 DB/사용자/Redis/클라우드 자격 증명 사용 안 함 |
| 실행 JAR | `/opt/alpha-payment-test/app.jar`, 검증한 release 고정; 운영 배포와 자동 동기화 안 함 |
| 제한 | backend MemoryMax 384MiB / CPU 50%, DB 128MiB / CPU 25% |

Nginx는 결제·취소·환불·로그인·본인 프로필·Swagger의 명시된 경로만 허용합니다. 공개 회원가입·탈퇴·AI·내부 관리 경로는 404로 차단합니다. Swagger 노출은 `SPRINGDOC_PATHS_TO_MATCH`로 제한하며 실제 호출 허용 여부는 Nginx의 method/path 목록을 따릅니다. 요청 본문 상한 64KiB, 속도 제한, 접두사 전용 쿠키 경로를 적용합니다. 웹훅은 JWT 대신 기존 서명 검증을 거칩니다.

현재 작은 EC2의 여유 메모리는 제한적입니다. 부하 시험이나 운영급 수용량을 검증한 환경이 아닙니다. `alpha-payment-test-guard.timer`는 30초마다 가용 메모리 128MiB 미만, 루트 디스크 여유 512MiB 미만 또는 테스트 backend 실패를 확인하면 **테스트 backend와 테스트 DB만** 중지합니다. 자동 재개하지 않습니다. 운영 서비스는 중지하지 않습니다.

## 설치 도구 사용 범위

`bootstrap_private.bootstrap(payload)`는 root로 최초 1회 실행하는 함수입니다. payload에는 40자리 검증 release, `files`(환경 예시·service·nginx 두 파일), `credentials`(PortOne TEST 필드 네 개)가 필요합니다. 비밀은 관리자 SSH stdin 등 비공개 경로로만 전달하고 명령행·CI·로그에 넣지 않습니다. 로컬에 존재하는 `postgres:18-alpine` 이미지를 ID로 고정하고 pull하지 않습니다. 초기 여유 메모리 460MiB 이상을 요구합니다.

`publish.publish(release)`는 비공개 backend 검증, loopback Nginx 검증, 기존 배포 lock 획득, 원본 백업, 후보 설정 검사 후 reload, HTTPS 검증 순서로 공개합니다. 검증 실패 시 원래 Nginx 설정을 복원합니다. 실제 PortOne 미결제 조회를 수행하며 PENDING 테스트 주문이 생성됩니다. 실제 카드 승인을 요청하지 않습니다.

두 함수는 기존 환경에 반복 적용하는 업그레이드 명령이 아닙니다. 기존 디렉터리/설정 변경이 감지되면 중단합니다. 관리자 설치 시 Python 모듈·guard unit도 전용 경로에 배치하고 systemd 설정을 검사한 뒤 timer를 활성화해야 합니다. 운영 자동 배포 workflow는 변경하지 않습니다.

## 상태 확인과 중지

```sh
sudo systemctl status alpha-payment-test.service alpha-payment-test-guard.timer
sudo docker inspect --format '{{.State.Status}}' alpha-payment-test-db
curl --fail http://127.0.0.1:19191/internal/actuator/health
sudo nginx -t
```

비밀이 포함된 env, 계정 파일, 프로세스 환경 전체를 출력하지 않습니다. 테스트 로그도 필요한 진단만 비공개로 확인합니다.

중지할 때는 timer의 동작과 자동 재시작을 함께 중지합니다. 데이터는 보존됩니다.

```sh
sudo systemctl disable --now alpha-payment-test-guard.timer
sudo systemctl disable --now alpha-payment-test.service
sudo docker update --restart no alpha-payment-test-db
sudo docker stop alpha-payment-test-db
```

공개 경로를 제거하려면 관리자가 **현재** Nginx 설정에서 전용 include 두 줄만 제거하고 `nginx -t` 후 reload합니다. 설치 당시 백업 `/etc/alpha-payment-test/nginx.production-before.conf`는 이후의 운영 변경을 포함하지 않으므로 무조건 덮어쓰지 않습니다. 데이터·비밀 삭제는 별도 승인 범위입니다. 서버 재부팅/플랫폼 재생성 복구는 아직 검증하지 않았습니다.

## 2026-10-28 확인 결과

- 운영 release `4de72eabac04ea643d1cb47553ed33639786124f` 유지, 운영 backend 재시작 없이 Nginx reload만 수행.
- 외부 HTTPS 로그인·내역·Swagger, Refresh Cookie 격리, CORS, 운영 토큰 경계, 미인증/차단 경로 및 웹훅 서명 검증 통과.
- PortOne 콘솔 V2 테스트 호출 200/성공은 사용자 확인. 이 공용 환경에서 실제 카드 승인 후 등급 반영은 아직 미검증.
- 최종 운영·테스트 health 모두 UP, 재시작 횟수 0. 호스트 가용 메모리 약 212MiB, 루트 디스크 여유 약 4.27GiB(시점 값).
- 설정 자동 테스트 4개 및 기존 Java bootJar 통과. 동일 Java release의 선행 전체 회귀 360개 중 351 통과/환경 조건 skip 9/실패 0. 재부팅·부하 검증은 포함하지 않음.
