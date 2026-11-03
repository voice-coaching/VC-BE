# 관리자 공용 콘텐츠 수정·삭제 검증

검증일: 2026-11-02. 기준 main c7cf862, 이슈 #173.

## 통과한 검증

관련 선택 테스트 68개 중 67개 통과, 실제 코덱 실행 조건이 없는 테스트 1개 제외, 실패·오류 0개. bootJar와 git diff --check 통과. 전체 기존 테스트를 모두 통과했다는 의미는 아니다.

- NEWS/SENTENCE/ANNOUNCER/CLASS_PRACTICE × DRAFT/PUBLISHED/HIDDEN 수정·삭제
- status/최초 공개 시각 유지, 오래된 version 409
- 미인증/일반 회원 거부, 개인 콘텐츠 404, 잘못된 요청 400
- 삭제 후 일반/관리자 상세 404 및 신규 세션 생성 404
- 이미 캐시한 일반 상세를 삭제 커밋 후 재조회하면 404
- 기존 세션과 평가 원문/프롬프트 유지, 삭제 행과 연관 관계 보존
- 원문 수정 시 기존 음성 퇴역 및 TTS 개정 증가, 제목만 수정하면 음성 유지
- 감사 기록 실패 시 삭제와 감사 작업 롤백
- PostgreSQL V64→V65 업그레이드: 공용 세션 snapshot 이관, 개인 원문의 평문 복제 없음
- 삭제 뉴스가 이미 만들어진 인기 순위에서도 제외, 삭제 콘텐츠의 늦은 TTS 완료 차단
- 기존 카탈로그 페이지/인접 콘텐츠/분류/메타데이터 및 인기 뉴스 동시 요청 회귀
- 관리자·음성·알림·마이그레이션 및 OpenAPI 기존 회귀

OpenAPI operation 140개, 명시 설명 맵 137개. main의 직접 분석 이력 3개 경로는 기존 기본 설명을 유지한다.

## 별도 확인한 기존 테스트 불일치

확장 실행에서는 ContentCatalogIntegrationTest.httpAuthenticationValidationAndPageContract가 미로그인 공개 조회에 401을 기대하지만 현재 main SecurityConfig는 permitAll이므로 200을 반환했다. 전역 보안 설정과 해당 테스트는 이번 작업에서 수정하지 않았다. 위 최종 선택 실행에는 이 테스트를 포함하지 않고 같은 클래스의 데이터 조회 테스트 5개를 지정했다. 관리자 API의 별도 서명 토큰·세션·현재 ACTIVE ADMIN 검증은 유지하며 관련 인증 테스트는 통과했다.

## 재현 범위

Java 21. 관리자 패키지 전체, OpenApiDocumentIntegrationTest, OpenApiKoreanDocumentationTest, NewsPopularitySnapshotRegressionTest 및 ContentCatalogIntegrationTest의 위 HTTP 정책 테스트를 제외한 메서드 5개를 실행한다. PostgreSQL은 VC_BE_TEST_POSTGRES_URL/USER로 지정한 localhost 전용 임시 schema를 사용하고 종료 시 정리한다. 운영 DB에 마이그레이션·콘텐츠 변경·삭제를 실행하지 않았다.

실제 프론트 편집/삭제 버튼 연결, 운영 마이그레이션, 기존 S3 URL의 만료 전 접근 여부는 로컬 검증 완료와 구분한다. 프론트 후속 작업은 docs/api/admin-content-edit-delete.md에 정리했다.
