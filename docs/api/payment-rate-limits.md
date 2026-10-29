# 결제 API 호출 제한

관련: #141. 결제 기능이 활성화된 서비스의 로그인 회원에게 적용한다. 테스트 이용등급 `admin`도 같은 한도를 사용한다. 신규 API나 환경변수는 추가하지 않는다.

| 작업 | Method / Path | 회원별 한도 |
|---|---|---|
| 주문 생성 | POST `/api/payment-orders` | 60초 창당 5회 |
| 포트원 검증 | POST `/api/payment-orders/{orderId}/verify` | 60초 창당 10회 |
| 결제 조회 | GET `/api/payment-orders/{orderId}`, GET `/api/users/me/payment-orders`, GET `/api/refund-requests/{requestId}` | 세 API 합산 60초 창당 60회 |
| 취소·환불 신청 | POST `/api/payment-orders/{orderId}/cancel`, POST `/api/payment-orders/{orderId}/refund-requests` | 두 API 합산 60초 창당 5회 |

각 회원·작업의 첫 요청이 고정 60초 창을 시작한다. 창이 끝난 뒤 첫 요청이 새 창을 시작한다. 슬라이딩 윈도우가 아니므로 두 창의 경계에는 요청이 몰릴 수 있다. 회원·작업별 한도는 서로 독립적이다. 주문 ID, Idempotency-Key, 브라우저, 로그인 토큰 또는 IP를 바꿔도 동일 회원의 동일 작업 한도는 공유한다.

## 초과 응답

HTTP `429 Too Many Requests`:

```http
Retry-After: 12
Cache-Control: no-store
```

```json
{"result":false,"message":"결제 요청을 처리할 수 없습니다.","data":null,"code":"PAYMENT_RATE_LIMITED"}
```

`Retry-After`는 해당 창이 끝날 때까지 남은 시간을 초 단위로 올림한 정수다. 제한에 걸린 요청은 기존 창을 연장하지 않는다. 프론트는 해당 시간 동안 같은 작업을 자동으로 반복하지 않고 버튼/폴링을 잠시 대기시킨다. 허용된 CORS Origin에는 `Retry-After`를 노출한다. 헤더의 대기 시간은 새로운 결제·취소를 자동 실행하라는 뜻이 아니다. 기존 멱등 키와 주문/요청 ID를 유지하고 상태를 확인한다.

## 호출 제한 저장소 오류

HTTP `503 Service Unavailable`, `Cache-Control: no-store`:

```json
{"result":false,"message":"결제 요청을 처리할 수 없습니다.","data":null,"code":"PAYMENT_RATE_LIMIT_UNAVAILABLE"}
```

제한 상태를 확정할 수 없으면 요청을 진행하지 않는다. 해당 요청에서는 주문 생성, 포트원 조회·취소, 회원등급 변경을 실행하지 않는다. 내부 오류나 DB 정보를 응답에 노출하지 않는다. 이 503에는 `Retry-After`를 보장하지 않는다.

## 집계와 트랜잭션

- Spring이 인증·필수 헤더·Body/Query 바인딩을 완료하고 Controller 메서드에 진입한 요청부터 집계한다. 미인증 요청, CORS preflight, 바인딩 이전에 거부된 입력은 이 회원 한도에 포함하지 않는다. 결제 비활성 상태에서는 기존 403을 유지한다.
- 정상 응답, 멱등 재요청, Controller 진입 후 입력·소유권·포트원 오류로 실패한 요청도 한 번으로 집계한다. 비활성·미존재 회원에는 제한 행을 생성하지 않고 기존 업무 권한 검사를 유지한다.
- `payment_request_quotas`는 회원·작업별 최대 한 행, 회원당 최대 네 행을 재사용한다. 기존 회원 정보·주문·회원등급은 마이그레이션으로 변경하지 않는다. 실제 회원 행 삭제 시 FK `ON DELETE CASCADE`로 제한 행도 제거한다.
- 사용자 행을 짧게 잠가 최초 행 생성과 동시 횟수 갱신을 직렬화한다. 제한 횟수는 별도 트랜잭션에서 먼저 확정하므로 업무 처리 실패로 되돌아가지 않는다. 트랜잭션 제한은 3초, 행 잠금 대기 한도는 2초다.
- 사용자 잠금을 해제한 뒤 주문 처리와 포트원 호출을 실행한다. 프로세스 메모리로 허용량을 관리하지 않으므로 동일 DB를 사용하는 인스턴스와 재시작 사이에서 한도를 공유한다. 서버 시계는 동기화되어 있어야 한다.
- 내역·환불 상태 조회의 업무 트랜잭션은 계속 read-only이며, 호출 제한 상태만 앞선 독립 트랜잭션에서 갱신한다.

## 제외 범위

`POST /api/payments/webhooks/portone`, 내부 자동 복구, `GET /api/users/me`에는 이 회원 한도를 적용하지 않는다. 웹훅은 기존 공식 SDK 서명·시각·Body 크기 검증 및 포트원 실제 상태 검증을 계속 사용한다. 회원의 한도 초과 때문에 결제 결과 재전송을 거부하지 않는다.

이 기능은 회원의 반복 업무 요청을 줄인다. 비인증 HTTP 폭주, 회원가입 남용, 전역 DDoS 방어, 웹훅 전용 인프라 제한, PENDING 주문 만료·개수 제한은 별도 범위다. 기존 주문·취소의 멱등성 및 소유권 검사를 대체하지 않는다.
