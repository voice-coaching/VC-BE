# 기존 SpeakAI 서비스의 TEST 결제 전환 (#137)

기존 서버·PostgreSQL·JWT 로그인을 사용한다. 신규 API나 DB 마이그레이션은 없다. 이 문서는 배포 절차이며 문서 추가만으로 운영 결제를 활성화하지 않는다.

## 계약과 회원등급

- 모든 로그인 회원이 TEST 결제를 이용한다. `payment.test.allowed-user-ids`/`PAYMENT_TEST_ALLOWED_USER_IDS`는 더 이상 사용하지 않는다. 별도 `payment-test` 프로필은 필요하지 않다.
- `PAYMENT_TEST_ENABLED`는 기본 false다. true일 때 Store ID·TEST Channel Key·API Secret·Webhook Secret 네 항목이 모두 필요하며 누락되면 시작을 거부한다.
- 기존 V52 시점 ACTIVE 회원의 `user_test_membership_grants`는 유지한다. admin은 결제·취소·만료 후에도 admin이며 관리자 API 권한을 부여하지 않는다. 그 이후 가입한 회원은 free, 검증된 유효 TEST 주문이 있으면 paid, 만료/취소 후 다른 이용권이 없으면 free다. 탈퇴·정지 중에는 free다.
- Store/Channel Key와 포트원 조회의 `channel.type=TEST`, 주문 ID·15,000 KRW·통화·시각을 서버에서 검증한다. 웹훅 서명·재전송 멱등성·본인 주문 제한·취소 우선 규칙을 유지한다. 활성화가 실결제 허용을 뜻하지 않는다.
- 기존 별도 테스트 DB의 계정·주문은 복사하지 않는다. 기존 회원 grant를 다시 생성하거나 전체 회원을 admin으로 갱신하지 않는다.

## 병합 후 운영 순서

1. 코드/자동 테스트 검증 및 PR 병합 후 기존 서버에 해당 release가 배포됐는지 확인한다. 코드 배포만으로 기능은 켜지지 않는다.
2. 관리자만 접근 가능한 기존 backend 환경 설정에 [설정 예시](../../scripts/payment-shared.env.example)의 항목을 추가한다. DB/JWT/기존 환경파일을 별도 sandbox 파일로 덮어쓰지 않는다. 비밀은 Git·채팅·로그에 넣지 않으며 기존 노출 키는 교체한다.
3. PortOne에서 **V2·테스트** 웹훅을 `https://api.voice-coaching.site/api/payments/webhooks/portone`으로 전환한다. JSON/2024-04-25 계약과 해당 TEST 웹훅 secret을 맞춘다. 기존 `/payment-test` 주소와 혼동하지 않는다.
4. 기존 backend에서 `PAYMENT_TEST_ENABLED=true` 적용 후 정해진 운영 배포 절차로 재시작한다. 활성화와 웹훅 전환 사이의 짧은 구간에는 테스트 주문 생성을 안내하지 않는다. 수신 테스트 200 및 미서명 요청 거부를 확인한다. 자동 복구는 필요하면 별도로 활성화하며 한 인스턴스에서만 수행한다.
5. 프론트는 기존 API base `https://api.voice-coaching.site`, 기존 로그인 token을 사용한다. 결제/내역 화면에 “테스트 결제”와 TEST 이용등급을 표시하고 서버가 반환한 Store ID/Channel Key/주문 ID/금액으로 결제창을 연다. 채널을 임의 실연동으로 변경하지 않는다. 카드정보는 PG 화면에만 입력한다.
6. V52 이후 가입한 일반 계정으로 free → TEST 결제 → paid → 전액 취소 → free, 결제 내역·웹훅·중복 verify를 확인한다. 기존 admin 계정은 결제·취소 후에도 admin인지 확인한다. 실제 승인/취소 단계는 사용자가 수행한다.
7. 통합 검증 후 별도 sandbox 공개 경로/서비스 정리는 별도 운영 작업으로 진행한다. 그 DB와 비밀의 삭제는 별도 승인 없이 수행하지 않는다.

장애 시 기능을 false로 돌리고 정해진 배포 절차를 따른다. 기능 비활성 시 결제 API는 403이며 신규 회원의 paid 조회는 free로 보일 수 있다. admin grant는 유지되고 주문·취소 기록도 삭제되지 않는다. 이미 승인된 TEST 주문은 재활성화 후 verify/웹훅/복구로 대조한다. 기존 서비스의 인증·학습·회원 DB 설정은 유지한다.

상세 응답·오류는 [테스트 결제](test-payments-api.md), [회원등급](membership-api.md), [내역](payment-history-api.md), [취소](payment-cancellation-api.md), [환불](payment-refund-api.md)을 따른다. 프론트 화면 구현, 실결제/정기결제, 운영 활성화 및 실제 PG 종단 검증은 이 코드 PR 완료와 별개다.
