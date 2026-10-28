# PortOne V2 테스트 결제

관련 이슈: #125. 기존 사용자 API에 필드를 추가하고 테스트 주문 API 3개와 서명 인증 웹훅 1개를 추가한다.

이번 범위는 **NHN KCP V2 TEST 일반 카드 결제 1회**이다. 상품 스냅샷은 `SPEAKAI_MONTHLY_TEST_V1`, 15,000 KRW이며 자동 갱신, 빌링키, 실제 이용권 지급, 코칭 횟수 차감, 운영 배포는 포함하지 않는다. 월간 30회/최초 무료 체험 등 BM의 나머지 정책을 구현한 것으로 간주하지 않는다.

## 실행 조건과 격리

- 기본 `payment.test.enabled=false`. 비활성 상태에서 주문 API는 403이며 회원등급은 free다.
- 활성화하려면 Spring `payment-test` 프로필, `payment.test.enabled=true`, 양수 사용자 ID의 명시적 허용 목록 `payment.test.allowed-user-ids=1,2`, 포트원 설정이 필요하다.
- `prod`/`production` 프로필과 동시에 활성화하면 시작에 실패한다. **이 프로필을 운영 DB 연결과 함께 사용하지 않는다.** 프로필 검사 자체는 DB가 실제로 격리됐음을 증명하지 않는다.
- 환경변수: `PORTONE_STORE_ID`, `PORTONE_TEST_CHANNEL_KEY`, `PORTONE_API_SECRET`, 선택 `PORTONE_WEBHOOK_SECRET`. API Secret은 서버 전용이다. 웹훅 Secret 미설정은 웹훅 503으로 처리하며 사용자 요청 검증 API는 사용할 수 있다.
- 테스트 권한 원장은 `payment_test_orders`, 이벤트 수신 원장은 `payment_test_webhook_receipts`다. 운영 구독의 권한 원장으로 사용하지 않는다. 고객 이름, 카드번호, PG 원문 응답, 사용자 원문 웹훅을 DB에 저장하지 않는다.
- 로컬 실행 도구는 새로운 PostgreSQL DB·JWT 발급자·계정을 매 실행 생성하며 운영 DB나 기존 로그인 세션을 사용하지 않는다.

## API 계약

공통 응답은 `{result,message,data}`이고 오류에는 `code`가 추가된다. 주문 API 3개는 기존 로그인 JWT가 필요하다. 클라이언트 금액·회원등급·사용자 ID를 신뢰하지 않는다.

| Method | Path | 입력 | 동작 |
|---|---|---|---|
| POST | `/api/payment-orders` | 필수 `Idempotency-Key`: `[A-Za-z0-9_-]{16,64}`. Body 없음 | 고정 상품의 테스트 주문 생성, 200. 동일 사용자·키는 동일 주문 반환 |
| GET | `/api/payment-orders/{orderId}` | 본인 주문 ID | 저장된 상태 조회, 200. 포트원 재조회는 하지 않음 |
| POST | `/api/payment-orders/{orderId}/verify` | 본인 주문 ID. Body 없음 | 포트원에서 실제 상태 재조회·검증 후 반영, 200. 미승인 주문은 409 |
| POST | `/api/payments/webhooks/portone` | 원문 JSON ≤64KiB, `webhook-id`, `webhook-signature`, `webhook-timestamp` | JWT 대신 공식 SDK 서명·시각 검증, 결제 재조회, 200. 알 수 없는 주문/비거래 이벤트는 권한 생성 없이 무시 |

주문 ID는 KCP의 최대 40자 제한에 맞춰 `test` + 하이픈 없는 UUID 32자리(총 36자 영숫자)로 생성한다. 주문 `data`:

```json
{
  "orderId": "test<하이픈 없는 서버 생성 UUID 32자리>",
  "product": "SPEAKAI_MONTHLY_TEST_V1",
  "orderName": "SPEAK AI 월간 이용권 (테스트)",
  "totalAmount": 15000,
  "currency": "KRW",
  "status": "PENDING",
  "environment": "TEST",
  "expiresAt": null,
  "storeId": "<공개 상점 ID>",
  "channelKey": "<공개 테스트 채널 키>"
}
```

상태: `PENDING`, `PAID`, `FAILED`, `CANCELLED`. 사용자에게 결제 성공을 표시하기 전에 반드시 `verify`와 최신 프로필을 확인한다. 200 응답만으로 PAID라고 가정하지 않는다.

`GET /api/users/me` 추가 필드:

```json
{
  "membershipType": "paid",
  "membershipExpiresAt": "2026-11-28T00:00:00Z",
  "membershipEnvironment": "TEST"
}
```

free이면 만료·환경은 null이다. `UserRole` USER/ADMIN과 분리되며 JWT에 유료 상태를 고정하지 않는다. 유효한 PAID 원장과 활성 사용자 상태를 조회한다. 기본 운영환경에서 이 테스트 기능이 회원 권한을 부여하지 않는다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | INVALID_IDEMPOTENCY_KEY | 헤더 누락 또는 형식 오류 |
| 401 | AUTHENTICATION_REQUIRED | 주문 API 로그인 없음 |
| 401 | WEBHOOK_SIGNATURE_INVALID | 웹훅 서명·시각·필수 헤더 오류 |
| 403 | TEST_PAYMENT_DISABLED / PAYMENT_USER_INACTIVE | 비활성·허용 목록 외 사용자 / 비활성 계정 신규 주문 |
| 404 | PAYMENT_ORDER_NOT_FOUND | 미존재·다른 회원 주문 |
| 409 | MEMBERSHIP_ALREADY_ACTIVE | 새 키로 주문하려는데 유효한 이용권 존재 |
| 409 | PAYMENT_NOT_COMPLETED | 포트원에 아직 해당 결제 없음 |
| 409 | PAYMENT_MISMATCH / PAYMENT_CHANNEL_MISMATCH | ID·금액·통화 / 상점·채널·TEST 불일치 |
| 409 | PAYMENT_TIMESTAMP_INVALID / PAYMENT_STATUS_UNSUPPORTED | 비정상 시각 / 지원하지 않는 상태 |
| 409 | WEBHOOK_EVENT_CONFLICT | 동일 이벤트 ID의 본문 해시 불일치 |
| 413 | WEBHOOK_BODY_TOO_LARGE | 웹훅 64KiB 초과 |
| 502/503 | PAYMENT_PROVIDER_UNAVAILABLE | 포트원 오류·지연·연결 문제 |
| 502 | PAYMENT_PROVIDER_INVALID_RESPONSE | 필수 응답 필드·형식 오류 |
| 503 | WEBHOOK_NOT_CONFIGURED | 웹훅 비활성 또는 Secret 미설정 |

## 멱등성·권한 정책

사용자 잠금 → 주문 잠금 순서를 고정한다. 결제 API 네트워크 호출은 트랜잭션 밖에서 수행하고, 금액·통화·ID·상점·채널·TEST 여부를 확인한 뒤 짧은 트랜잭션으로 반영한다. 이벤트 ID와 본문 SHA-256만 저장하며 이벤트 처리와 상태 갱신은 함께 커밋한다. 중복 검증·웹훅은 기간을 연장하지 않는다.

이번 테스트 이용 기간은 포트원 `paidAt` 기준 **UTC 달력 한 달**이다. 월말은 다음 달의 마지막 유효일로 보정된다. 실제 BM/운영 구독 확정 정책을 대신하지 않는다. 취소 및 부분 취소는 테스트 권한을 회수하고, 취소 상태는 늦은 PAID 응답으로 복원하지 않는다. 만료와 탈퇴/정지는 조회 시 free다. 다른 유효한 테스트 주문이 있다면 그 주문의 권한은 유지된다.

기본 설정에서는 `verify`를 다시 호출하거나 웹훅을 받아야 변경이 반영된다. 아래의 TEST 자동 복구를 활성화하면 웹훅 누락을 주기 재조회로 보완한다. 운영 사용 전에는 실제 HTTPS 웹훅 종단 검증·환불 API·운영 대사/관측/보존 정책·실제 구독/횟수 정책을 별도로 완성해야 한다.

## TEST 결제 상태 자동 복구 (#127)

새 공개 API나 응답 필드 변경은 없다. 격리된 테스트 DB와 기존 `payment-test` 활성화 조건을 갖춘 환경에서 추가로 `payment.test.recovery.enabled=true`를 설정한다. 환경변수는 `PAYMENT_TEST_RECOVERY_ENABLED=true`이며 기본은 비활성이다. 운영 DB/프로필에 활성화하지 않는다.

- 시작 60초 후, 이전 배치 완료 60초 후마다 최대 10개를 재확인한다. 별도 스케줄러 스레드 설정이 없으면 다른 예약 작업과 스레드를 공유할 수 있다.
- DB에 이미 존재하는 PENDING 주문과 만료 전 PAID 주문만 ID 순서로 순환한다. 포트원에 없는 미결제 주문이나 실패한 조회도 다음 순환에서 재시도한다. 오래된 미결제 주문을 자동 실패 처리하거나 삭제하지 않는다.
- 허용된 테스트 사용자만 조회한다. 금액/통화/상점/TEST 채널 검증과 기존 사용자→주문 잠금, 취소 우선 규칙을 그대로 사용한다. 외부 GET 중에는 DB 트랜잭션을 열지 않는다.
- 클라이언트 검증이 누락된 PAID를 복구하며 재조회로 이용기간을 연장하지 않는다. PAID 주문의 포트원 취소 상태도 기존 규칙으로 반영하지만 **취소/환불을 요청하지는 않는다**.
- FAILED/CANCELLED 및 만료된 PAID는 자동 재조회 대상이 아니다. FAILED 주문을 재사용한 결제 시도는 수동 검증 또는 웹훅이 필요하다. 취소·환불 담당자는 기존 주문 ID와 `PaymentTransactions.apply`를 통한 상태/회원등급 계산을 재사용하고 별도 회원등급 필드를 만들지 않는다.
- 한 주문의 통신 오류나 검증 불일치는 다른 주문 처리를 막지 않는다. 오류 원문과 사용자/주문 식별자는 로그에 기록하지 않는다.
- Micrometer `payment.test.recovery.orders` (`outcome=applied|not_completed|failed|skipped`) 및 `payment.test.recovery.batch.failures`로 집계한다. `applied`는 상태 검증·반영 경로가 성공했다는 뜻이며 새 결제 건수는 아니다. 기존 내부 Actuator 접근 정책을 유지한다.
- 커서는 프로세스 메모리에 보관한다. 재시작 시 처음부터 순회하며 중복 반영은 기존 멱등 규칙으로 방지한다. 여러 인스턴스에서는 중복 GET이 발생할 수 있으므로 테스트 환경의 한 인스턴스에서만 활성화한다. 분산 lease와 영구 재시도 원장은 이번 범위에 없다.
- 배치가 여러 개면 한 바퀴에 여러 분이 걸리며, 외부 API 지연과 장애 시 더 오래 걸린다. 이 작업은 웹훅 설정을 대체하지 않는다. 실제 외부 웹훅 수신은 별도 HTTPS 테스트 환경에서 확인해야 한다.

## 직접 테스트

1. `bootJar`를 빌드한다. Python 3.12+, Java 21, PostgreSQL 실행 파일이 필요하다.
2. Git에서 무시되는 `tmp/portone-test.local.env`에 포트원 테스트 설정을 넣는다. 파일을 커밋하지 않는다.
3. `python scripts/portone_test_checkout.py --env-file tmp/portone-test.local.env` 실행. 기본 도구 위치는 이 작업공간의 `tmp/jdk21/jdk-21.0.12+8`, `tmp/postgresql17-runtime/pgsql/bin`이다. 다른 설치 위치는 `--java-home`, `--pg-bin`으로 지정한다.
4. 열리는 `http://127.0.0.1:18081/`에서 테스트 채널임을 확인하고 주문 생성 → 결제창 → 서버 검증 → `paid (TEST)`를 확인한다. 실제 카드 입력은 사용자가 공식 PG 화면에서 수행한다.
5. `Ctrl+C`로 종료하면 해당 도구가 시작한 백엔드·DB를 정지한다. 진단 데이터는 무시되는 `tmp/portone-checkout-*`에 남는다. 다음 실행은 새 계정·DB이며 이전 실행의 테스트 이용권은 이어지지 않는다.

승인 없이 로컬/실제 포트원 연결만 검사하려면 `--smoke --no-browser`를 사용한다. 이 모드는 주문 생성 후 미결제 상태의 GET 조회만 확인하며 PG 승인 요청을 보내지 않는다. 로컬 웹훅은 외부에서 접근할 수 없다. 실제 수신 검증에는 별도의 HTTPS 수신 환경과 콘솔 설정이 필요하다.

테스트 도구는 PC 브라우저 검증용이며 VC-FE의 상용 결제 UI를 변경하지 않는다. Next.js+Capacitor 통합 시 동일 API를 사용하고, 모바일 `redirectUrl`·양 플랫폼 앱 복귀·사용자 인증 세션 회복·중단 주문 재검증을 구현해야 한다. 리다이렉트 파라미터를 결제 확정 증거로 사용하지 않는다. iOS/Android 기기 결제는 이 작업의 로컬 검증으로 대체되지 않는다.

공식 참고: [V2 인증 결제](https://developers.portone.io/opi/ko/integration/start/v2/checkout), [NHN KCP 제약](https://developers.portone.io/opi/ko/integration/pg/v2/kcp-v2?v=v2), [V2 웹훅](https://developers.portone.io/opi/ko/integration/webhook/readme-v2), [모바일 결제](https://developers.portone.io/opi/ko/extra/mobile-payment/readme-v2), [공식 JVM SDK](https://github.com/portone-io/server-sdk/tree/main/jvm).
