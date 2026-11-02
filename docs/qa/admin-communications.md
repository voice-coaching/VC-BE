# 관리자 알림·팝업·회원 기록 검증

## 기준 및 실행 결과

브랜치 feat/admin-phase-one-api, 원격 develop 6ae98d1 위로 rebase. 기존 관리자 기반 변경은 마이그레이션 번호를 V61·V62로 조정했고 알림·팝업 V63을 추가했다. 사용자 문서 directory.md/schema.md는 rebase 전 백업과 SHA-256이 일치하며 기존 staged 삭제 의도를 보존했다. 프론트 코드·운영 DB·실제 알림 전송은 변경하지 않았다.

- 전체 test 및 bootJar: **553개 중 458개 통과, 95개 조건부 제외, 실패0**, 빌드 성공.
- 그 후 보완한 탈퇴 계정 차단 및 OpenAPI 관련 최종 테스트: **22개 통과**, bootJar 재빌드 성공. 이 실행에는 관리자 알림 통합 테스트8개가 포함된다.
- 활성 상태 enum 정리 후 관련 통합 테스트 **8개 재통과**, 최종 bootJar 성공. 로그 build/admin-extension-final-guard.log.
- 별도 격리 PostgreSQL 관리자 테스트: **3개 통과**. 관리자 V61, 음성 V62, 이번 V63 업그레이드·SQL 검증이다.
- 전체 테스트의 조건부 제외에는 PostgreSQL/Redis/실제 ffmpeg 등 환경 의존 테스트가 포함된다. 이번에 별도 실행한 PG3개 외 나머지를 검증 완료로 간주하지 않는다. 원격 AI 분석, 실제 S3 업로드/디코딩, 웹 푸시 전달 성공을 의미하지 않는다.

로그는 ignored build/admin-extension-full.log, build/admin-extension-final-targeted.log, build/admin-extension-final-postgres.log에 남겼다. 테스트 PostgreSQL은 127.0.0.1:16439의 임시 클러스터에서 테스트별 무작위 스키마를 만들고 삭제했다. 운영 DB에는 SQL을 실행하지 않았다.

## 검증한 기능

| 구분 | 확인 내용 |
| --- | --- |
| 권한 | 미로그인401, USER 및 거짓 ADMIN 토큰403, 실제 활성 관리자 허용 |
| 대상 | ALL_ACTIVE는 발송 시점 활성 회원, SELECTED_USERS는 선택된 활성 회원만, 탈퇴·정지 제외 |
| 입력 | 대상중복·비활성·없는ID·잘못된경로 거부, UUID형식 및 resource enum 검사 |
| 발송 | 초안은 알림 미생성, PLAN이 기존 알림함 ADMIN_PLAN으로 조회됨 |
| 중복 | 같은 campaign 재요청·실제 PostgreSQL 동시 요청2개가 알림1개·성공감사1개만 생성 |
| 원자성 | 발송 후 예외 발생 시 수신 알림·발송 상태·감사 기록 동시 rollback |
| 수정 | 초안 stale version409, 팝업 stale version409, 비활성화 즉시 반영 |
| 팝업 | 미래/만료 제외, 시작 포함·종료 제외, priority 선택, 로그인·탈퇴 차단, 없으면 null |
| 회원 기록 | 13개 고정 projection SQL 실행, 회원별 소유 분리, 원본 음성 URL 미포함 |
| UUID | 분석 request/execution 및 직접 분석 job UUID→기존 소유 회원 연결 |
| DB | V62→V63 기존 회원 수 보존, 기간 CHECK, 기존 감사 불변성 유지 |
| OpenAPI | 전체 엔드포인트135개, 새 API12개의 한글 계약 및 bearer 인증 유지 |

## 배포 후 확인

1. TEST 환경에 V61→V62→V63 적용, 실제 role=ADMIN 계정과 일반 계정으로 권한 확인.
2. 프론트 초안 검토→수동 dispatch→사용자 기존 알림함 표시·읽음 처리를 연결. 운영에서 테스트 안내를 무단 전체 발송하지 않는다.
3. 시작 시각·일수 지정 팝업의 사용자 표시, 종료·중지·우선순위와 null 응답 확인.
4. 기존 회원 목록에서 회원 상세·탭별 기록을 연결하고 UUID검색을 숫자 userId로 변환. 결제 기록은 TEST로 표시.
5. 실제 회원 수에서 전체 발송의 시간·잠금·오류 응답을 확인. 동기 대량 발송 성능은 이번 기능 테스트로 보장하지 않는다.
6. 결제 완료/만료 자동 알림, 브라우저 푸시, 광고 동의, 오늘 그만 보기와 관리 UI는 미포함이다. 필요하면 별도 범위·계약으로 진행한다.
