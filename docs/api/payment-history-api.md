# 내 결제 내역 조회

공통 변경: [회원별 호출 제한](payment-rate-limits.md). 해당 작업 한도를 초과하면 `429 PAYMENT_RATE_LIMITED`와 `Retry-After`(초), 제한 저장소 오류 시 `503 PAYMENT_RATE_LIMIT_UNAVAILABLE`를 반환합니다. 두 응답은 `Cache-Control: no-store`이며 업무 처리를 실행하지 않습니다. 취소·환불 신청은 합산 5회, 세 결제 조회 API는 합산 60회, 주문 생성은 5회, 검증은 10회이며 각 고정 창은 첫 요청부터 60초입니다. 웹훅과 내 정보 조회에는 이 제한을 적용하지 않습니다.

> **GET `/api/users/me/payment-orders`**
>
> 설명: 로그인 사용자의 테스트 주문 내역을 생성 시각 DESC, 같은 시각이면 주문 ID DESC로 조회합니다. PENDING/PAID/FAILED/CANCELLED를 모두 포함합니다.
>
> 인증·권한: `Authorization: Bearer <accessToken>` 필수. TEST 결제 활성화 시 모든 로그인 회원이 본인의 TEST 내역을 조회할 수 있습니다. 별도 회원 허용 목록은 없습니다. 기능 비활성 시 403이며 실결제 내역 API는 아닙니다.

---

## Request

### Headers

| 이름 | 필수 | 설명 |
|---|---|---|
| Authorization | Y | `Bearer <accessToken>` |

### Path Parameters

없음. 조회 사용자 ID는 로그인 정보에서 가져옵니다.

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 제약 |
|---|---|---|---|---|
| page | integer(int32) | N | 0 | 0 이상, 0부터 시작 |
| size | integer(int32) | N | 20 | 1~100 |

`page * size`는 2147483647 이하여야 합니다. 정상 범위의 페이지가 마지막 페이지를 넘어가면 200과 빈 `items`를 반환합니다. 상태 필터나 정렬 변경 파라미터는 제공하지 않습니다.

### Body

없음.

## Request Example

```http
GET /api/users/me/payment-orders?page=0&size=20 HTTP/1.1
Authorization: Bearer <accessToken>
```

## Response Schema

| 필드 | 타입 | 설명 |
|---|---|---|
| result | boolean | 성공 여부 |
| message | string | `결제 내역을 조회했습니다.` |
| data.items | array | 본인의 저장된 TEST 주문 목록 |
| data.items[].orderId | string | 서버 생성 주문 ID. 기존 개별 주문 조회·검증에 사용 |
| data.items[].product | string | `SPEAKAI_MONTHLY_TEST_V1` |
| data.items[].orderName | string | `SPEAK AI 월간 이용권 (테스트)` |
| data.items[].totalAmount | integer(int64) | 원래 주문 금액 15000. 환불 후 순 결제액이 아님 |
| data.items[].currency | string | `KRW` |
| data.items[].status | string | `PENDING`, `PAID`, `FAILED`, `CANCELLED` |
| data.items[].environment | string | `TEST` |
| data.items[].createdAt | string(date-time) | 주문 생성 시각, ISO 8601 offset 포함 |
| data.items[].paidAt | string(date-time) 또는 null | 서버 검증·반영된 결제 완료 시각. 값이 없으면 null |
| data.items[].expiresAt | string(date-time) 또는 null | 해당 주문에 기록된 이용기간 만료 시각. 값이 없으면 null |
| data.page | integer(int32) | 요청 페이지 번호 |
| data.size | integer(int32) | 요청 페이지 크기 |
| data.totalElements | integer(int64) | 본인의 전체 주문 수 |
| data.totalPages | integer(int64) | 전체 페이지 수. 내역이 없으면 0 |
| data.hasNext | boolean | 다음 페이지 유무 |

`paidAt`과 `expiresAt`은 취소 후에도 과거 기록으로 남을 수 있습니다. 현재 회원등급과 유효한 이용권은 기존 `GET /api/users/me`의 `membershipType`, `membershipExpiresAt`, `membershipEnvironment`를 사용합니다. `PAID`라도 이용기간이 만료되면 회원등급은 free일 수 있습니다.

## Response Example

```json
{
  "result": true,
  "message": "결제 내역을 조회했습니다.",
  "data": {
    "items": [
      {
        "orderId": "test0123456789abcdef0123456789abcdef",
        "product": "SPEAKAI_MONTHLY_TEST_V1",
        "orderName": "SPEAK AI 월간 이용권 (테스트)",
        "totalAmount": 15000,
        "currency": "KRW",
        "status": "PAID",
        "environment": "TEST",
        "createdAt": "2026-10-28T08:00:00Z",
        "paidAt": "2026-10-28T08:01:00Z",
        "expiresAt": "2026-11-28T08:01:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

주문이 없으면 `items=[]`, `totalElements=0`, `totalPages=0`, `hasNext=false`입니다. 기존 개별 주문 API의 응답 필드는 변경하지 않습니다.

---

## Error

### 400 VALIDATION_ERROR — 페이지 범위·오프셋 오류

```json
{"result":false,"message":"결제 요청을 처리할 수 없습니다.","data":null,"code":"VALIDATION_ERROR"}
```

### 400 VALIDATION_ERROR — 정수가 아니거나 int32 범위를 넘는 query

```json
{"result":false,"message":"페이지 번호와 크기는 정수여야 합니다.","data":null,"code":"VALIDATION_ERROR"}
```

### 401 AUTHENTICATION_REQUIRED — 로그인 필요

```json
{"result":false,"message":"인증이 필요하거나 Access Token이 만료되었습니다.","data":null,"code":"AUTHENTICATION_REQUIRED"}
```

### 403 TEST_PAYMENT_DISABLED — TEST 결제 비활성

```json
{"result":false,"message":"결제 요청을 처리할 수 없습니다.","data":null,"code":"TEST_PAYMENT_DISABLED"}
```

## 연동 및 구현 범위

- 목록은 저장된 원장만 조회합니다. 포트원 호출, 결제 상태 갱신, 회원등급 변경은 하지 않습니다. 최신 상태 확인은 기존 `POST /api/payment-orders/{orderId}/verify` 또는 서명 검증 웹훅/활성화된 자동 복구가 담당합니다.
- 로그인 사용자 ID를 목록과 개수 쿼리에 모두 적용하며 idempotency key, API/채널 키, 카드·구매자 정보, 웹훅/PG 원문을 반환하지 않습니다.
- 조회 서비스는 read-only 트랜잭션을 사용합니다. V50은 `(user_id, created_at DESC, id DESC)` 복합 인덱스만 추가하며 기존 원장 데이터는 변경하지 않습니다.
- 결제 취소·환불 요청 및 취소 시각/환불금액 상세는 별도 담당 범위입니다. 기존 주문 ID와 상태를 공유합니다. 현재 부분 취소도 기존 원장 규칙에 따라 `CANCELLED`로 표시됩니다.
- 자동 갱신 여부나 다음 결제 예정일은 제공하지 않습니다. 현재 테스트 결제는 자동 갱신 없는 월간 이용권입니다.
- 목록을 넘기는 동안 새 주문이 생성되면 페이지 위치가 바뀔 수 있으므로 필요하면 첫 페이지부터 새로 조회합니다.

관련: #129, [기존 테스트 결제 계약](test-payments-api.md).
