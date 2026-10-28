# TEST 전액 환불 신청·처리 상태 조회

카드 전액 취소와 전액 환불은 PortOne의 같은 취소 API를 사용한다. 별도 환불 실행기·금액 원장을 만들지 않고 V51 `payment_test_cancellations`를 공유한다. 기존 cancel 응답의 requestId도 같은 환불 상태 조회에 사용할 수 있다. 실결제·부분 환불·환불 심사/관리자 승인·정기결제 해지는 이번 범위에 없다.

## 전액 환불 신청

`POST /api/payment-orders/{orderId}/refund-requests`

- auth: Bearer 로그인 + TEST 결제 활성화 + 허용 사용자. 탈퇴/정지 계정의 신청은 차단한다.
- path: `orderId` string 필수, 본인의 서버 생성 테스트 주문 ID.
- query: 없음.
- body: `reason` string 필수/non-null, 원문 1~200자·제어문자 불가·앞뒤 공백 제거 후 비어 있으면 거절. 기존 취소와 동일한 사유 검증을 사용한다. 금액·통화·사용자 ID를 클라이언트 입력으로 결정하지 않는다.

```json
{"reason":"테스트 이용권 전액 환불 신청"}
```

신규 요청은 기존 전액 취소 서비스로 처리한다. 이미 해당 주문에 요청이 있으면 같은 사유에 한해 동일 ID와 저장 상태를 반환한다. 재신청은 PG GET/POST 없이 처리하므로 최초 응답이 유실된 경우도 같은 body를 다시 보내 requestId를 회수할 수 있다. 다른 사유로 새 요청을 생성하지 않으며 409다. 다른 사용자는 주문 존재 여부를 확인할 수 없다.

HTTP 200, Cache-Control: no-store. 메시지는 `전액 환불 신청을 확인했습니다.`다. 200 자체를 환불 완료로 표시하지 않는다. 최초 PG 전송/사후 확인이 불명확하면 502이며 원장은 UNKNOWN으로 보존된다.

## 처리 상태 조회

`GET /api/refund-requests/{requestId}`

- auth: 기존 Bearer 로그인 + TEST 허용 사용자 + 본인 요청 소유권.
- path: `requestId` 소문자 UUID string 필수. 없는 ID·잘못된 형식·타인 요청은 모두 404.
- query/body: 없음.
- HTTP 200, Cache-Control: no-store. 메시지 `전액 환불 처리 상태를 조회했습니다.`.
- 저장된 원장만 읽는다. PG 호출·추가 환불 요청·회원등급 변경을 수행하지 않는다. 원장의 주인은 요청에 연결된 기존 주문의 userId로 검증한다. 사유·API Secret·멱등 키·카드 정보·PG 원문은 반환하지 않는다.

## 공통 응답

기존 `{result,message,data}` envelope를 따른다. 두 API의 data는 동일하다.

| 필드 | 타입 | 필수/null | 의미 |
| --- | --- | --- | --- |
| requestId | string(UUID) | 필수/non-null | 취소·환불 공통 요청 ID |
| orderId | string | 필수/non-null | 본인 TEST 주문 ID |
| status | string enum | 필수/non-null | UNKNOWN/PENDING/SUCCEEDED/FAILED |
| amount | integer(int64) | 필수/non-null | 고정 전액 15000. 계좌 입금 완료 금액을 의미하지 않음 |
| currency | string | 필수/non-null | KRW |
| environment | string | 필수/non-null | TEST |
| requestedAt | ISO-8601 offset datetime | 필수/non-null | 최초 취소·환불 요청 시각 |
| updatedAt | ISO-8601 offset datetime | 필수/non-null | 마지막 저장 상태 반영 시각 |

UNKNOWN은 외부 결과 미확인, PENDING은 접수 응답을 받았으나 전액 취소 확인 대기, FAILED는 취소 실패 응답, SUCCEEDED는 서버가 결제 재조회에서 전액 취소를 확인한 상태다. 상세한 상태·재시도·사유 보존 정책은 [전액 취소 계약](payment-cancellation-api.md)을 따른다. 완료 상태는 늦은 응답으로 되돌리지 않는다.

웹훅·자동 복구·기존 `POST /api/payment-orders/{orderId}/verify`에서 검증된 전체 취소가 반영되면 상태 조회도 SUCCEEDED를 반환한다. UNKNOWN/PENDING을 완료로 표시하지 않으며 PG POST를 자동 재전송하지 않는다. 기록 커밋 직후 프로세스 중단 등으로 실제 전송 여부가 모호한 경우 운영 대조가 필요하다. 공용 테스트 환경에 자동 복구가 비활성이거나 기존 복구 대상이 아닌 만료 주문이면 웹훅 또는 수동 verify가 필요하다.

## 오류

기존 결제 오류 envelope를 사용한다. 인증 오류는 공통 인증 메시지, 그 외 PaymentException 메시지는 `결제 요청을 처리할 수 없습니다.`다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | INVALID_CANCELLATION_REASON | 신청 사유 누락/공백/길이 초과/제어문자 |
| 400 | VALIDATION_ERROR | JSON 요청 형식 오류 |
| 401 | AUTHENTICATION_REQUIRED | 로그인 없음/만료 |
| 403 | TEST_PAYMENT_DISABLED | 테스트 비활성/허용 사용자 아님 |
| 403 | PAYMENT_USER_INACTIVE | 탈퇴/정지/없는 계정의 신청 |
| 404 | PAYMENT_ORDER_NOT_FOUND | 신청 대상 주문 없음/타인 주문 |
| 404 | PAYMENT_CANCELLATION_NOT_FOUND | 조회 ID 없음/형식 오류/타인 요청 |
| 409 | PAYMENT_CANCELLATION_CONFLICT | 기존 요청과 다른 사유로 재신청 |
| 409 | PAYMENT_NOT_CANCELLABLE | 신규 신청 대상이 미결제/실패/부분 취소 상태 |
| 409 | PAYMENT_NOT_COMPLETED / PAYMENT_MISMATCH / PAYMENT_CHANNEL_MISMATCH / PAYMENT_TIMESTAMP_INVALID / PAYMENT_STATUS_UNSUPPORTED | 신규 신청 사전 결제 검증 실패 |
| 502/503 | PAYMENT_PROVIDER_UNAVAILABLE | 신규 신청 사전 재조회 통신 실패 |
| 502 | PAYMENT_PROVIDER_INVALID_RESPONSE | 신규 신청 사전 재조회 형식 오류 |
| 502 | PAYMENT_CANCELLATION_UNCONFIRMED | 취소 전송 경로 또는 사후 검증 실패 |

## 프론트 연결

공용 TEST base로 로그인한 토큰을 사용한다. 신청 사유를 제출한 뒤 받은 requestId로 상태를 조회한다. 네트워크 오류 시 새 사유/새 금액으로 요청하지 말고 같은 body로 재신청해 기존 요청을 확인한다. SUCCEEDED 후 프로필과 결제 내역을 새로 조회하며 다른 유효한 이용권이 있으면 paid가 유지될 수 있다. 카드사 반영 시점이나 이용기간에 따른 일할 환불 정책은 제공하지 않는다.

신규 JAR·V51·Nginx 허용 경로·Swagger 필터를 서버에 적용한 뒤 공용 환경 검증이 필요하다. 이번 구현과 푸시는 서버 배포나 실제 카드 취소 승인을 포함하지 않는다.
