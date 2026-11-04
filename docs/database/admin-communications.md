# 관리자 알림·팝업·회원 기록 DB

## 변경 기준

V64__create_admin_communications.sql. develop의 V59(코칭 사용량)·V60(인기 뉴스), 관리자 V62·음성 V63 이후 적용한다. users/학습/결제 테이블 데이터는 변경하거나 삭제하지 않는다. 사용자 PK는 기존 bigint다. API 계약은 [관리자 확장](../api/admin-communications-and-members.md)을 따른다.

## 신규 테이블

| 테이블 | 역할·관계 | 주요 컬럼·제약 |
| --- | --- | --- |
| admin_notification_campaigns | 관리자 생성 알림 초안/발송 원장. users(created_by) N:1 | id identity PK, version bigint 기본0, audience ALL_ACTIVE/SELECTED_USERS, kind GENERAL/PLAN, title200, body1000, deep_link 허용경로, created_by, created_at, sent_at nullable, sent_count bigint>=0 |
| admin_notification_recipients | 선택 대상 목록. campaign N:1, users N:1 | (campaign_id,user_id) 복합 PK·FK. ALL_ACTIVE에서는 행 없음 |
| admin_popups | 활성 로그인 회원 공통 팝업. users(created_by) N:1 | id identity PK, version bigint 기본0, title200, body1000, deep_link nullable, priority0~100, starts_at, ends_at, enabled, created_by, created_at |

팝업은 ends_at>starts_at 및 최대365일 DB CHECK, 시작·끝 시각 timestamptz다. 게시 검색용 부분 인덱스는 enabled 행의 `(priority DESC,starts_at DESC,id DESC)`다. 삭제·수신 확인·클릭 로그 테이블은 추가하지 않는다.

## 기존 테이블 연동

발송은 user_notifications에 INSERT SELECT한다. 기존 FK, `(user_id,deduplication_key)` unique, 알림함/읽음 인덱스를 사용한다. type은 ADMIN_GENERAL 또는 ADMIN_PLAN, deduplication_key는 `admin-campaign:{id}`다. 연습 리마인더 설정과 push_subscriptions는 변경하지 않는다.

campaign을 PESSIMISTIC_WRITE로 잠근 뒤 미발송만 처리한다. INSERT SELECT 대상은 ACTIVE·deleted_at NULL인 회원이고 선택 발송은 recipients의 존재 조건을 추가한다. 수신자 삽입, sent_count/sent_at/version, admin_audit_logs의 NOTIFICATION_DISPATCH는 동일 JPA 트랜잭션이다. 동시 재발송은 잠금 이후 SENT를 읽어 기존 결과를 반환한다. 발송 실패/감사 실패는 함께 롤백한다. 초안 생성의 HTTP 재전송 중복 방지 키는 없다.

팝업 생성·수정은 POPUP_CREATE/POPUP_EDIT, 초안은 NOTIFICATION_CREATE로 감사 기록을 남긴다. target_type은 NOTIFICATION_CAMPAIGN 또는 POPUP이며 본문이나 수신자 목록을 감사 행에 복사하지 않는다. 기존 감사 불변 트리거를 유지한다.

## 회원 관련 기록

새 회원 원장이나 UUID PK는 만들지 않는다. application은 현재 관리자 권한 확인 후 infrastructure의 고정 projection을 사용한다. TRAINING_SESSIONS/RECORDINGS/ANALYSES는 세션 소유자를, 결제·취소는 주문 소유자를, 직접 분석은 history_links의 회원 연결을 기준으로 조회한다. 직접 분석 history는 아직 없고 link만 있는 경우도 left join으로 유지한다.

UUID resolve는 analysis_results(active_request_event_id,active_execution_id,canonical_result_event_id)와 direct_analysis_history(_links)의 job_id/execution_id 연결만 사용한다. 요청 식별자는 bind parameter, 자원 enum은 서버의 고정 테이블·컬럼 목록으로 변환한다. 다른 회원의 레코드와 비밀정보를 섞지 않는다. overview 건수는 분류별 count이며 현재 13개 소규모 관리 조회다. 대량 전체 덤프 API는 제공하지 않는다.

## 검증·운영

격리 로컬 PostgreSQL에서 V63→V64 업그레이드, 기존 회원 수 보존, 동시 dispatch 중복 방지, 실패 rollback, 기간 경계, DB CHECK, 13개 projection SQL과 UUID 소유 연결을 검증한다. 운영 DB 변경과 실제 발송은 수행하지 않았다. ALL_ACTIVE 동기 발송은 회원 수 증가 시 별도 부하 검증 후 배치/비동기 확장을 검토한다.
