# 관리자 회원 권한 변경

`PATCH /api/admin/users/{userId}/role` — Bearer 인증과 현재 DB의 활성 ADMIN 권한이 필요하다. `Cache-Control: no-store`.

요청 JSON의 모든 필드는 필수·non-null이다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| expectedRole | USER / ADMIN | 편집 화면에서 확인한 기존 권한 |
| role | USER / ADMIN | 변경할 권한 |
| reason | string, 공백 제외 1~500자 | 작업 이력 사유 |

200 응답은 `{result:true,message:"회원 권한을 변경했습니다.",data:{userId:123,role:"ADMIN"}}`. userId는 정수, role은 USER/ADMIN, 둘 다 필수·non-null이다. 같은 현재 권한으로 요청하면 성공하되 중복 감사 기록은 만들지 않는다.

400 VALIDATION_ERROR: 누락/enum/사유 오류. 401 UNAUTHORIZED: 인증 실패. 403 ACCESS_DENIED: 일반 회원 또는 해제된 관리자. 404 RESOURCE_NOT_FOUND: 없는/탈퇴/삭제 회원. 409 ADMIN_EDIT_CONFLICT: expectedRole 불일치, 비활성 대상, 본인 권한 변경. 503 ADMIN_STORE_UNAVAILABLE: 저장소 실패. 오류는 기존 공통 `{result:false,message,code}` 응답이다.

호출자와 대상 행을 ID 순으로 잠그고 최신 상태를 읽은 후 호출자 권한을 다시 확인한다. 서로를 동시에 해제하려는 두 요청은 모두 성공할 수 없다. 본인 변경은 금지하므로 마지막 관리자가 스스로 관리 권한을 없앨 수 없다. 변경과 USER_ROLE_CHANGE 감사 기록은 하나의 트랜잭션이다. 대상의 역할과 updatedAt만 수정하며 이용등급이나 결제 권한을 변경하지 않는다.

해제 즉시 기존 ADMIN 토큰도 관리자 API의 DB 재검사에서 거절된다. 새로 지정된 사용자는 새 ADMIN 토큰을 받도록 재로그인하거나 기존 인증 갱신을 수행해야 한다. 프론트는 재로그인을 안내한다. 세션/AI 요청/결과 스키마 및 Flyway 변경은 없다.

프론트 배포 전 이 API와 클래스 관계 기반 제외 필터를 먼저 배포한다. 자동으로 회원 권한을 바꾸는 배포 작업은 없다.

## 검증

AdminUserRoleIntegrationTest 5개: HTTP 지정/해제, 기존 토큰의 권한 해제 반영, 감사 이력, 미인증/일반 회원 차단, 본인/오래된 역할/잘못된 enum/빈 사유/비활성/탈퇴/없는 대상 차단, 동시 상호 해제 후 관리자 한 명 보존.

AdminCourseIntegrationTest 9개와 AdminApiIntegrationTest 20개를 함께 실행해 총 34개 통과. 로컬 H2·MockMvc이며 운영 회원 권한은 수정하지 않는다. 최신 main의 JDBC 전용 user_premium_trials가 Hibernate 테스트 스키마에 없어서 기존 관리자 API 테스트 fixture에만 테이블 생성을 추가했다. 운영 마이그레이션 변경은 없다.
