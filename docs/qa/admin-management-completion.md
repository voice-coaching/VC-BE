# 관리자 기존 기능 보완 검증 (#188)

## 자동 검증

- 실제 서명 토큰·세션과 현재 DB 권한으로 신규4개 API 접근 검사.
- 공지 삭제 후 사용자·관리자 상세 제외, 감사 이력 보존.
- 음성 retired_at 기록·object_key 보존, 대표 승계·마지막 음성 삭제, 재생·대표 재지정 차단.
- 알림 초안 대상·내용 교체 후 수정된 회원에게만 발송, 삭제 시 대상 FK 연결 제거.
- 필수 버전·사유·발송 대상·연결 주소 검사, 오래된 버전·SENT 변경 거절.
- localhost 전용 PostgreSQL에서 동시 수정/삭제 대 발송 경합 및 트랜잭션 롤백 검증.

## 수동 배포 확인

1. 공지 삭제 후 사용자·관리자 목록과 상세에서 제외되는지 확인.
2. 음성2개 중 대표 삭제 후 남은 대표·미리듣기·콘텐츠 버전 갱신 확인.
3. 마지막 음성 삭제 후 빈 목록 확인. 기존 파일·학습 이력은 보존.
4. 초안 수정 후 새 버전으로 발송하고 이전 대상에게 잘못 발송되지 않았는지 확인.
5. 초안 삭제 후 조회 제외, 발송 완료 알림의 수정·삭제 거절 확인.
6. 일반 회원·권한 해제 관리자·폐기된 세션 접근 거절 확인.

운영 DB·외부 파일 삭제나 실제 회원 알림 발송은 로컬 검증에서 수행하지 않는다. 실행 결과는 PR에 기록한다.

## 실행 결과 (2026-11-03)

- 최종 관리자 전체: 63개 중62개 통과·실패0·조건부 제외1. 제외는 로컬 ffmpeg/ffprobe가 없는 실제 디코더 테스트다.
- 관리자 PostgreSQL5개 테스트 전부 통과. 알림 동시 수정/삭제 대 발송 경합, 선택 대상 FK 제거·트랜잭션 롤백 포함.
- bootJar 성공. Windows 한글 작업 경로의 Gradle 클래스 로딩 문제를 피하기 위해 빌드 산출물만 임시 영문 경로로 지정했다.
- 관리자·공지·콘텐츠로 넓힌 최초 회귀: 102개 중83개 통과·18개 실패·1개 제외. 실패는 ContentCatalogIntegrationTest, CustomContentIntegrationTest, CustomContentUnavailableIntegrationTest, NewsPopularityPostgresTest, PracticeContentRecommendationIntegrationTest, PrivateTextEncryptionTest, InquiryConcurrencyIntegrationTest, SupportApiIntegrationTest에 속한다.
- 수정 전 main b765411의 해당8개 클래스25개 테스트를 동일 환경에서 실행했으며 동일한18개 테스트가 실패했다. 변경 전후 실패 이름 집합 일치, 이 변경으로 추가된 실패 없음. 저장소 전체 테스트 통과로 해석하지 않는다.
- 신규 삭제/편집 테스트는 공지·음성 감사 기록 저장 실패 시 삭제·대표·버전 롤백을 포함한다.
- 사용한 PostgreSQL은 127.0.0.1:55488의 격리된 임시 DB다. 운영 DB와 외부 파일·회원 알림 발송은 사용하지 않았다.
