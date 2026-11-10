# 관리자 API 권한 경계

공통 SecurityConfig에서 OPTIONS 처리 뒤 `/api/admin`과 `/api/admin/**`는 ROLE_ADMIN을 요구한다. 공통 JWT 필터가 서명·issuer·만료·access 용도·활성 세션을 검증한 뒤 만든 권한만 사용한다. 클라이언트가 전달한 X-User-Id나 미검증 role 값으로 Authentication을 만들지 않는다.

관리자 모듈의 기존 AdminAuthenticationAdvice/Service 검사도 유지한다. 서명된 ADMIN 토큰이어도 DB의 현재 활성 ADMIN이 아니거나 탈퇴·삭제된 계정이면 관리자 데이터에 접근할 수 없다. 이는 일반 이용등급(free/paid/admin 표시)과 별개의 `users.role` 권한이다. 일반 회원에게 열려 있는 `/api/users/me/popup`은 이 관리자 prefix 규칙의 대상이 아니다.

미인증/유효하지 않은 자격은 401 UNAUTHORIZED, 인증된 USER나 현재 관리자 권한이 없는 계정은 403 ACCESS_DENIED이며 기존 no-store 오류 envelope를 유지한다. API 경로/본문/DB 스키마/AI 계약 변경은 없다. CORS preflight는 유지한다.

검증: 실제 발급한 USER JWT로 관리자 목록 11개와 권한 변경 요청을 차단한다. 관리자 모듈 Advice가 적용되지 않는 테스트 전용 경로도 공통 JWT role 규칙이 보호한다. role payload 변조 후 미서명 토큰은 401, 유효한 옛 ADMIN 토큰의 DB 권한 해제는 403이다. 기존 관리자 API·권한 변경·JWT provider 회귀를 함께 실행한다. 운영 제보 계정의 JWT/DB role이 USER였는지와 당시 배포 버전은 이 로컬 검증만으로 확정하지 않는다.

로컬 검증 결과: AdminApiBoundaryIntegrationTest 4개, AdminApiIntegrationTest 20개, AdminUserRoleIntegrationTest 5개, JwtTokenProviderTest 11개로 총 40개 통과.
