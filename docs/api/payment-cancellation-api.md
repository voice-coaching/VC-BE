# TEST 결제 전액 취소

`POST /api/payment-orders/{orderId}/cancel`

- 인증: 기존 Bearer 로그인, `payment-test` 프로필·기능 활성화·허용 사용자 조건. 본인 주문만 허용하며 탈퇴/정지 사용자는 요청 불가.
- path: `orderId` string, 기존 서버 생성 주문 ID.
- query: 없음.
- body: JSON 객체. `reason` string 필수, 앞뒤 공백 제거 후 1~200자, 제어문자 불가. 원문 입력 길이도 200자 이하여야 한다. 금액·통화·사용자 ID·상점·채널은 입력받아 결정하지 않는다. 사유에는 카드·계좌·연락처 등 개인정보를 입력하지 않는다.

```json
{"reason":"테스트 이용권 전액 취소 요청"}
```

성공 HTTP 200, envelope `{result:true,message:"테스트 결제 취소 요청을 확인했습니다.",data:{...}}`.

| data 필드 | 타입 | 필수/null | 의미 |
| --- | --- | --- | --- |
| requestId | string(UUID) | 필수/non-null | 주문당 하나의 취소 요청 식별자 |
| orderId | string | 필수/non-null | 본인 테스트 주문 ID |
| status | string enum | 필수/non-null | 아래 상태 |
| requestedAt | ISO-8601 offset datetime | 필수/non-null | 최초 요청 생성 시각 |
| updatedAt | ISO-8601 offset datetime | 필수/non-null | 마지막 상태 확인 시각 |

- `UNKNOWN`: 요청 원장을 생성했으나 외부 완료 여부 미확인. 네트워크/서버 중단 시에도 이 상태일 수 있다.
- `PENDING`: 취소 응답을 받았지만 전액 취소된 결제 상태를 아직 확인하지 못함.
- `SUCCEEDED`: 서버가 재조회한 결제의 CANCELLED 및 취소액=전체 금액을 확인함.
- `FAILED`: 제공자가 취소 실패를 응답하고 전액 취소 상태도 확인되지 않음.

200은 전액 환불 완료를 뜻하지 않는다. 카드사 청구·입금 반영 시점은 이 API가 보장하지 않는다. 취소 사유와 provider 원문·취소 키는 응답/로그에 포함하지 않는다.

## 상태 검증·중복 처리

서비스에서 소유권과 계정 상태를 확인한 후 포트원을 재조회한다. 기존 `PaymentTransactions.apply`가 ID·금액·통화·상점·채널 TEST 및 시각을 검증하고 원장을 반영한다. PAID·미취소 상태만 신규 취소 가능하며, 이미 전액 취소된 주문은 외부 취소 요청 없이 완료 기록을 반환할 수 있다. 부분 취소 상태는 거절한다.

사용자→주문 순서의 잠금과 주문별 UNIQUE 취소 기록으로 하나의 요청만 전송권을 갖는다. 최초 요청 사유를 저장하고 이후 다른 사유는 409로 거절한다. PortOne에 서버 주문 금액 15,000 KRW, 동일한 `currentCancellableAmount`, 설정된 storeId와 `requester=CUSTOMER`를 보낸다. 전송 직전 adapter도 TEST 채널과 금액을 다시 검사한다. 요청 UUID를 RFC 8941의 따옴표로 감싼 Idempotency-Key로 사용한다.

외부 GET/POST는 DB 트랜잭션 밖에서 수행한다. 전체 취소 전송 전에 기록을 커밋한다. 중복 요청이나 응답 유실 시 PG POST를 자동으로 재전송하지 않는다. 따라서 기록 커밋 직후 서버가 중단돼 실제 POST가 없었던 경우도 UNKNOWN으로 남으며, 운영자가 포트원 상태를 대조해야 한다. PortOne 멱등성 보장 기간이 지난 뒤 임의 재시도해서 이중 취소를 시도하지 않는다. 자동 재시도·부분 금액 변경 기능은 없다.

기존 verify/서명 웹훅/활성화된 recovery에서 전액 취소가 확인되면 같은 트랜잭션으로 주문 CANCELLED와 요청 SUCCEEDED를 반영한다. 늦은 응답은 SUCCEEDED를 되돌리지 않는다. 요청이 접수되었다는 이유만으로 회원등급을 회수하지 않고 검증된 주문 상태에 따라 계산한다. 다른 유효한 주문이 있으면 paid가 유지될 수 있다.

V52의 기존 회원 테스트 admin grant는 결제·취소와 별개다. 이 grant가 있는 회원은 취소 성공 후에도 admin으로 표시된다. 취소 성공 여부는 요청/주문 상태로 확인하고 paid/free 전환 검증에는 grant 없는 신규 테스트 계정을 사용한다.

## 오류

오류 envelope는 `{result:false,message:"결제 요청을 처리할 수 없습니다.",data:null,code:...}`를 따른다. 인증 오류는 기존 보안 공통 메시지를 사용한다.

| HTTP | code | 발생 조건 |
| --- | --- | --- |
| 400 | INVALID_CANCELLATION_REASON | 사유 없음/공백/200자 초과/제어문자 |
| 400 | VALIDATION_ERROR | JSON 입력 오류, 기존 공통 처리 |
| 401 | AUTHENTICATION_REQUIRED | 로그인 없음/만료 |
| 403 | TEST_PAYMENT_DISABLED | 테스트 비활성/허용 사용자 아님 |
| 403 | PAYMENT_USER_INACTIVE | 계정 없음/탈퇴/정지 |
| 404 | PAYMENT_ORDER_NOT_FOUND | 없는 주문/타인 주문 |
| 409 | PAYMENT_NOT_CANCELLABLE | 미결제/실패/부분 취소/현재 상태에서 취소 불가 |
| 409 | PAYMENT_CANCELLATION_CONFLICT | 기존 요청과 다른 사유 |
| 409 | PAYMENT_NOT_COMPLETED | 포트원에 결제 없음 |
| 409 | PAYMENT_MISMATCH / PAYMENT_CHANNEL_MISMATCH / PAYMENT_TIMESTAMP_INVALID / PAYMENT_STATUS_UNSUPPORTED | 기존 결제 검증 실패 |
| 502/503 | PAYMENT_PROVIDER_UNAVAILABLE | 사전 재조회 통신 실패 |
| 502 | PAYMENT_PROVIDER_INVALID_RESPONSE | 사전 재조회 응답 형식 오류 |
| 502 | PAYMENT_CANCELLATION_UNCONFIRMED | 취소 전송 경로/사후 확인 실패. 요청 원장은 UNKNOWN으로 보존 |

공용 테스트 환경의 Nginx 허용 경로에 cancel을 추가했다. 새 JAR와 V51 및 Nginx 설정을 실제 서버에 적용하는 것은 별도이며, 기존 설치 도구를 운영 서버에 임의 재실행하지 않는다.

공식 계약: [PortOne V2 REST와 멱등성](https://developers.portone.io/api/rest-v2), [결제 취소](https://developers.portone.io/api/rest-v2/payment), [취소·환불 안내](https://help.portone.io/content/cancel-guide-using-admin-console).
