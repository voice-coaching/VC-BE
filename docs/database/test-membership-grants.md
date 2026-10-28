# 기존 회원의 테스트 이용등급 원장

V52 `user_test_membership_grants`는 기존 ACTIVE 회원에게 테스트 이용등급 admin을 부여한 사실만 저장한다. owner module은 payment이며, 사용자 관리자 권한이나 결제 원장을 대체하지 않는다.

| column | type | required/default | 의미·키·제약 |
| --- | --- | --- | --- |
| user_id | bigint | 필수/기본값 없음 | users.id FK 및 PK. 기존 회원별 1행, 고유 인덱스 제공 |
| granted_at | timestamptz | 필수/기본값 없음 | 마이그레이션에서 CURRENT_TIMESTAMP로 기록하는 부여 시각 |

관계는 users 1 : grant 0..1, FK 삭제 동작은 NO ACTION이다. enum·기본값·별도 인덱스·soft delete 컬럼은 없다. 기록 존재 여부로 테스트 등급을 판정하며 JPA가 사용자나 결제 상태를 바꾸지 않는다. 탈퇴·정지 상태에서는 원장이 있어도 admin으로 반환하지 않는다.

V52는 한 트랜잭션에서 users에 SHARE 잠금을 얻어 기존 회원을 기록한다. 잠금 중 가입 INSERT는 대기하고 커밋 이후 생성되는 신규 회원에는 grant가 없다. Flyway의 일회성 이력에 따라 애플리케이션 재시작으로 대상이 늘어나지 않는다. 이 파일을 독립적인 반복 실행 SQL로 사용하지 않는다.

기존 users 컬럼과 role, payment_test_orders, 취소·환불 원장은 변경하지 않는다. 테스트 기간 종료 시 회수 정책은 별도 관리 대상이며 자동 만료는 없다. grant를 회수하면 다음 회원 조회부터 기존 paid/free 계산 규칙을 적용한다. 배포 롤백은 신규 테이블을 임의로 삭제하거나 기존 Flyway 이력을 수정하는 방식으로 수행하지 않는다.

API와 프론트 처리: [이용등급 계약](../api/membership-api.md). 기존 회원의 범위는 백업 시점이 아닌 실제 V52 실행 시점이다. 이 작업에서 실제 서비스 DB 마이그레이션은 수행하지 않았다.
