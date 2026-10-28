# TEST 전액 취소 원장

V51 `payment_test_cancellations`는 주문당 하나의 전액 취소 명령을 기록한다. `payment_test_orders`의 결제·등급 원장을 대체하지 않는다. owner module은 payment이며 soft delete는 없다.

| column | type | required/default | 의미·제약 |
| --- | --- | --- | --- |
| id | varchar(36) | 필수/기본값 없음 | 서버 UUID, PK, provider 멱등 키로 사용 |
| order_id | varchar(64) | 필수/기본값 없음 | 기존 주문 ID, UNIQUE 및 FK |
| reason | varchar(200) | 필수/기본값 없음 | 최초 사유, trim 길이 1~200. API 제어문자 차단 |
| status | varchar(16) | 필수/기본값 없음 | UNKNOWN/PENDING/SUCCEEDED/FAILED |
| requested_at | timestamptz | 필수/기본값 없음 | 최초 생성 시각 |
| updated_at | timestamptz | 필수/기본값 없음 | 마지막 상태 반영 시각 |

관계는 주문 1 : 취소 요청 0..1이다. FK의 기본 NO ACTION으로 주문 참조를 보존한다. 기존 주문과 기존 데이터는 변경하지 않으며 PK와 UNIQUE가 인덱스를 제공한다. 별도 사용자·카드·계좌·provider 응답은 저장하지 않는다. 기존 사용자→주문 잠금 아래에서만 요청 생성과 상태 쓰기를 수행한다.

처음에는 UNKNOWN, 접수 응답만 확인되면 PENDING, 실패 응답은 FAILED, 검증된 전액 취소는 SUCCEEDED다. SUCCEEDED는 최종 상태로 다른 상태로 돌아가지 않는다. 비밀·개인정보를 사유에 입력하지 않도록 안내하고, 사유는 API 응답/로그로 노출하지 않는다. 운영 보존·삭제 정책은 기존 TEST 원장과 함께 별도 수립해야 한다.

API·장애/중복 처리: [전액 취소 계약](../api/payment-cancellation-api.md). 운영 DB나 공용 TEST 서버에는 이번 코드 작업만으로 migration을 실행하지 않는다.
