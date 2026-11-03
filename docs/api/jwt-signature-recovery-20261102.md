# JWT 서명 발급·검증 복구와 Vercel 403

## 확인한 원인

2026-11-02 main fc537d0의 JwtTokenProvider는 PlainJWT로 alg=none 토큰을 발급하고 JWTParser로 claims만 읽었다. 프론트 main a660036은 이 토큰을 기존 Authorization: Bearer 헤더로 전달했다.

동일한 공개 NEWS 목록 API에서 실제 계정에 해당하지 않는 진단값으로 비교했다. 헤더 없음은200, 무효 일반 Bearer 문자열은200, 서명 없는 JWT 형식을 Authorization에 넣으면403 및 x-vercel-mitigated=deny였다. 같은 무효 payload에 HS256 형식·무효 서명을 둔 대조 요청은200이었다. 무서명 토큰을 refreshToken 쿠키에만 넣은 경우도200이었다. 이는 Vercel에서의 요청 차단을 비교한 결과이며, 무효 HS256 서명이 유효한 인증이라는 뜻이 아니다. 실제 Vercel 규칙 이름은 관리 로그 접근 없이 확인하지 않았다.

사용자가 제공한 실제 토큰은 재사용하거나 문서에 저장하지 않았다. 프론트의 프록시·방화벽 규칙을 바꾸거나 인증 헤더를 제거하지 않는다.

## 수정 범위

최신 원격 main fc537d0에서 만든 fix/jwt-signature-verification 브랜치다. 관리자 API/프론트 작업과 분리한다.

- 기존 Nimbus 라이브러리와 JWT_SECRET을 사용하여 Access·Refresh 모두 HS256으로 서명한다.
- decoder가 HS256만 허용하고 서명, issuer, exp/nbf를 검증한다. 기존 표준 시계 오차 허용을 유지한다.
- exp, 양의 숫자 sub, 유효한 role, 비어 있지 않은 sid, 요청한 type(access/refresh)을 확인한다.
- 서명 없는 토큰, payload 변조, 다른 키·issuer·알고리즘, 만료·필수 claims 누락을 거절한다.
- 잘못된 Access는 provider에서 UNAUTHORIZED, 잘못된 Refresh는 INVALID_REFRESH_TOKEN, 유효하게 서명됐으나 만료된 Refresh는 REFRESH_TOKEN_EXPIRED로 매핑한다.
- JWT_SECRET은 UTF-8 최소32바이트가 필요하다. 누락/짧은 키로 시작하거나 unsigned 토큰으로 대체하지 않는다. 비밀값을 출력하지 않는다.

엔드포인트·DTO·claims 이름(sub,role,iss,exp,type,iat,jti,sid), Access 응답 필드와 HttpOnly Refresh 쿠키 전달 방식, 기존 만료 설정은 유지한다. 새 DB 컬럼·마이그레이션·환경변수는 없다.

이번 수정은 JWT adapter에 한정한다. main의 SecurityConfig permitAll, X-User-Id 선택 방식, JwtAuthenticationFilter의 오류 처리, 비밀번호·세션 소유 검증 정책을 일괄 복원한 작업은 아니다. provider가 거절한다고 모든 API 접근이401로 차단되는 것으로 해석하지 않는다. 실제 Refresh API의 거절은 별도 HTTP 통합 테스트로 확인한다.

## 배포 및 프론트 적용

1. 배포 환경의 기존 JWT_SECRET이 최소32바이트인지 비밀값을 출력하지 않는 절차로 확인한다. 프론트 공개 환경변수에 키를 추가하지 않는다.
2. 백엔드를 이 수정이 포함된 커밋으로 배포한다. 실제 API 환경이 해당 릴리스를 사용하는지 확인한다.
3. 기존 alg=none Access·Refresh 토큰을 새로 로그인하여 교체한다. 이전 무서명 Refresh를 허용해 자동 교환하지 않는다.
4. 구 토큰 때문에 로그아웃 요청도 Vercel에서 막힌다면 기존 사이트 로그인 데이터를 지운 뒤 로그인 화면에서 다시 로그인한다. 프론트 로그인 API의 기존 skipAuth 경로는 유지한다.
5. 새 토큰 헤더의 alg=HS256, 세 번째 서명 구간이 비어 있지 않음, 실제 목록 API200을 확인한다. 토큰 전체를 이슈/채팅/로그에 붙여 넣지 않는다.
6. 여전히 차단되면 해당 시점의 X-Vercel-ID와 Firewall 매칭 규칙을 확인한다. 전체 방화벽 비활성화나 경로 전체 bypass를 해결책으로 적용하지 않는다.

프론트 API 경로나 화면을 수정할 필요는 없다. 토큰 발급·전달 형식 복구와 재로그인이 필요하며 실제 운영 성공은 배포 후 확인한다. 운영 배포·계정 로그인·Vercel 설정 변경은 이 작업에서 수행하지 않았다.

## 검증

JwtTokenProviderTest는 서명 실제 검증, access/refresh 구분, 무서명·변조·다른 키·issuer·HS384·만료·필수 claims 누락과 잘못된 키 설정을 확인한다.

JwtSignatureIntegrationTest는 H2의 테스트 계정으로 로그인과 Refresh HTTP 응답 모두 HS256인지 확인하고, 무서명/변조 Refresh 및 누락 쿠키의401 오류를 확인한다. 실제 운영 계정이나 토큰을 사용하지 않는다.

AuthControllerTest의 누락 쿠키 사례는 현재 구조대로 service/provider가 던지는 InvalidTokenException을 mock에 설정했다. 컨트롤러에 JWT 파싱을 다시 넣지 않으며 동일401 응답을 검증한다.

```powershell
.\gradlew.bat test --tests '*JwtTokenProviderTest' --tests '*JwtSignatureIntegrationTest' --tests '*AuthControllerTest' bootJar
```

전체 기존 테스트를 모두 실행한 결과나 Vercel 운영 환경 통과를 의미하지 않는다. 현재 main CI/deploy는 compileJava·compileTestJava·bootJar를 실행하고 test는 제외하고 있으므로 이 변경에서는 위 보안·HTTP 테스트를 별도로 실행한다. 2026-11-02 최종 검증에서 JWT 단위11개·HTTP 통합4개·기존 컨트롤러4개, 총19개가 실패/제외 없이 통과했다. 전체 production/test 소스 컴파일과 bootJar도 성공했다.
