# 관리자 1차 DB 변경: V62

V62는 `practice_contents`, `notices`, `inquiries`에 `version BIGINT NOT NULL DEFAULT 0`을 추가하고 `admin_audit_logs`를 생성한다. 기존 행은 version=0으로 유지하며 콘텐츠·회원·결제·학습 기록을 삭제하거나 재분류하지 않는다. 관리자 계정도 자동 부여하지 않는다.

## version

세 Entity에 JPA `@Version`을 적용한다. 관리자 수정은 현재 행의 쓰기 잠금, 요청 version 비교, Entity 변경·flush 순서로 처리한다. 응답에는 flush 후 version을 반환한다. 기존 native SQL insert는 version을 생략해도 DEFAULT 0을 사용한다. 외부 운영 SQL이 임의로 행을 수정하는 경우 JPA version이 자동 증가하지 않으므로 관리자 UI와 별개로 통제해야 한다.

## admin_audit_logs

담당 모듈: admin. 목적: 성공한 관리자 변경 추적. soft delete 없음. UI 수정·삭제 없음. PostgreSQL trigger는 UPDATE·DELETE·TRUNCATE를 거절한다. DBA가 trigger를 해제할 수 있는 권한까지 막는 별도 위변조 방지 저장소는 아니다.

| 컬럼 | 타입 | 필수·기본값 | 의미 |
| --- | --- | --- | --- |
| id | bigint identity | PK, 자동 생성 | 기록 ID |
| actor_user_id | bigint | 필수 | users.id FK, 회원 1:N 작업 기록. 기본 NO ACTION으로 참조 중 물리 삭제 차단 |
| action | varchar(80) | 필수 | 서버 정의 작업 코드 |
| target_type | varchar(30) | 필수 | CONTENT / NOTICE / INQUIRY |
| target_id | varchar(100) | 필수 | 대상 ID 문자열, 다형 대상이므로 FK 없음 |
| reason | varchar(500) | 필수, 공백 불가 CHECK | 처리 사유 |
| changed_fields | varchar(500) | 필수 | 필드명 목록, 본문·변경 전후 값 제외 |
| outcome | varchar(20) | 필수, SUCCESS CHECK | 성공한 업무 변경 기록 |
| correlation_id | varchar(36) | 필수 | 서버 생성 추적 UUID, 인증 비밀 아님 |
| created_at | timestamptz | 필수 | 서버 Clock 시각 |

PK 외 unique 제약은 없다. 최근 목록 `(created_at DESC,id DESC)`, 대상별 `(target_type,target_id,created_at DESC,id DESC)`, 관리자별 `(actor_user_id,created_at DESC,id DESC)` 인덱스를 둔다.

업무 변경과 감사 INSERT는 동일 트랜잭션이다. 실패하면 함께 롤백한다. 거절된 시도·읽기 요청은 이 테이블에 기록하지 않는다. 비밀번호·토큰·음성·개인 원고·문의·답변 본문을 복사하지 않는다. reason은 운영자가 민감정보를 입력하지 않도록 운영 정책으로 제한한다.

## 첫 관리자 지정

기존 테스트 이용등급 admin과 실제 users.role=ADMIN은 다르다. V52 테스트 등급 부여를 실제 관리자 부여로 해석하지 않는다.

운영자가 대상 계정을 별도로 확인한 뒤 승인된 ID 한 개에 대해서만 권한을 부여한다. 이 문서는 대상 ID를 정하거나 SQL을 실행하지 않는다. 부여 후 새 로그인으로 ROLE_ADMIN 토큰을 발급받고 `/api/admin/me`를 확인한다. 대조용 USER·테스트 이용등급 admin 계정은 403이어야 한다.

## 검증

격리된 PostgreSQL에서 V60→V62 적용, 기존 콘텐츠 수 보존·version 기본값, 기존 native insert 호환, 감사 UPDATE·DELETE·TRUNCATE 차단을 검증하는 `AdminPostgresMigrationTest`를 추가했다. 실제 운영 반영 여부는 배포 후 Flyway V62 성공 기록으로 확인한다.

API는 [관리자 1차 계약](../api/admin-phase-one.md), 화면·후속 범위는 [계획서](../architecture/admin-implementation-plan.md)를 따른다.

V65의 콘텐츠 삭제 시각·기준 음성 퇴역·세션 원문 보존 컬럼은 [콘텐츠 수정·삭제 DB 명세](admin-content-edit-delete.md)를 따른다.
