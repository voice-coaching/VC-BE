# 회원 이용등급: free / paid / admin

`GET /api/users/me`의 기존 회원 응답에 이용등급을 제공한다. endpoint·인증·다른 필드 계약은 유지하며 필드를 추가하지 않는다. `admin`은 테스트용 이용등급이다. `users.role=ADMIN`이나 관리자 API 접근 권한을 의미하지 않는다.

| 필드 | 타입 | 필수/null | 값과 의미 |
| --- | --- | --- | --- |
| membershipType | string enum | 필수/non-null | free / paid / admin |
| membershipExpiresAt | ISO-8601 offset datetime | 필수/nullable | paid는 기존 결제의 만료일. free/admin은 null |
| membershipEnvironment | string | 필수/nullable | paid/admin은 TEST, free는 null |

## 대상과 우선순위

1. 사용자 없음·탈퇴·정지: free.
2. V52 원장에 포함된 기존 활성 회원: admin, 만료일 null, TEST.
3. 나머지 회원: TEST 결제 기능 활성화 여부와 유효한 PAID 원장에 따라 paid 또는 free.

```json
{
  "membershipType": "admin",
  "membershipExpiresAt": null,
  "membershipEnvironment": "TEST"
}
```

V52를 실행하는 **각 DB의 그 시점 기존 ACTIVE 회원**을 한 번 기록한다. 회원 ID 범위나 고정 날짜로 판정하지 않는다. 신규 가입자는 원장이 없으므로 기본 free이며, 이 마이그레이션은 신규 가입자에게 grant를 만드는 trigger나 가입 로직을 추가하지 않는다. 일반·소셜 회원 모두 같은 규칙이다. 실제 관리자 권한이 있던 회원도 기존 role은 그대로다.

admin은 결제 기능 비활성 상태에서도 별도 테스트 grant로 반환한다. 결제 API의 인증·소유권·TEST 채널 검증을 우회하지 않는다. 결제나 환불이 이 grant를 생성·삭제하지 않으므로, admin 사용자가 결제를 취소해도 admin으로 표시된다. paid/free 전환을 검증하려면 V52 이후 생성한 일반 계정과 TEST 결제 활성화 설정을 사용한다.

프론트는 admin을 테스트용 이용등급으로 표시하고 실제 관리자 메뉴 접근 판단에 사용하지 않는다. 이번 변경은 이용등급 원장과 응답만 제공한다. 월별 사용량 원장·차감·새로운 무제한 서버 정책을 구현한 것은 아니다. 테스트 grant는 자동 만료하지 않으므로 테스트 종료 시 원장을 회수하는 별도 관리 작업이 필요하다.

기존 서비스의 DB·로그인을 그대로 사용한다. V52 적용 시 기록된 기존 ACTIVE 회원의 grant는 재생성·삭제·초기화하지 않는다. 이후 가입자는 grant가 없으므로 free로 시작하며 TEST 결제 시 paid가 된다. admin은 결제·취소·만료와 관계없이 유지하며 탈퇴·정지 중에는 free를 반환한다. 별도 `/payment-test` 환경의 계정·주문을 기존 DB로 복사하지 않는다.

DB 구조와 전환: [테스트 이용등급 원장](../database/test-membership-grants.md). 기존 paid 결제 계약: [TEST 결제](test-payments-api.md).
