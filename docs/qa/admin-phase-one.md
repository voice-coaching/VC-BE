# 관리자 1차 검증 기록

검증일: 2026-11-01. 기준 develop: `bd3d27d`. 작업 브랜치: `feat/admin-phase-one-api`.

## 검증 결과

- 일반 전체 테스트: 503개 중 427개 통과, 76개 조건부 제외, 실패·오류 0개.
- `bootJar`: 성공.
- 별도 로컬 PostgreSQL `AdminPostgresMigrationTest`: 1개 통과. V60→V61 적용, 기존 콘텐츠 수·기본 version 보존, native insert 호환, 감사 UPDATE·DELETE·TRUNCATE 차단.
- 관리자 API 18개와 endpoint index·API specification 경로 대응 확인, `git diff --check` 통과.

일반 실행의 조건부 제외에는 PostgreSQL·Redis 등 외부 실행 조건이 필요한 검증이 포함된다. 427개 결과를 모든 운영 환경 통합 검증 완료로 해석하지 않는다. 관리자 PostgreSQL 검증은 그중 한 테스트를 별도 실행한 결과다.

## 관리자 업무 검증

- 비로그인 차단, USER 차단, 테스트 등급 admin 차단, 실제 ACTIVE ADMIN 허용.
- ADMIN 권한이 담긴 인증 상태라도 DB의 역할 회수·정지 후 403.
- 회원 이메일 마스킹, 비밀번호·토큰 비노출, 상세 로그인 제공자 조회.
- 초안 생성·편집·공개·숨김, 공개 전후 사용자 목록 표시 조건.
- 공개했던 콘텐츠의 원문 수정 차단, private 콘텐츠 ID 직접 접근 차단.
- 오래된 version 변경 거절.
- 공지 비공개·미래 예약·현재 공개 조건과 사용자 조회 일치.
- 문의 답변의 사용자 조회 반영, ANSWERED→CLOSED 전환, 목록에서 본문·연락 이메일 제외.
- 감사 기록에 문의·답변 본문을 복사하지 않음.
- 감사 저장 실패 시 생성 콘텐츠 롤백.
- 페이지·enum·필수값 검증, 검색 와일드카드를 일반 문자로 처리.
- 캐시 무효화는 커밋 후 수행, 캐시 오류로 이미 커밋한 변경의 재실행을 유도하지 않음.
- Swagger 관리자 인증·201·409·필수 version 및 공지 요청 sections 스키마 검증.

## 재현 방식

일반 테스트와 JAR 빌드는 Java 21에서 실행한다.

```text
./gradlew test bootJar
```

관리자 PostgreSQL 검증은 별도 폐기 가능한 loopback 클러스터에서만 수행한다. `VC_BE_TEST_POSTGRES_URL`에 `jdbc:postgresql://127.0.0.1:<port>/<test-db>`, 별도 테스트 USER/PASSWORD를 설정하고 다음 테스트만 선택한다.

```text
./gradlew test --tests '*AdminPostgresMigrationTest'
```

전체 테스트에 해당 환경변수를 일괄 설정하면 기존 PostgreSQL 전용 테스트들도 활성화된다. 일부는 Spring datasource 재설정·DB 이름 등 별도 fixture 조건을 요구하므로 일반 전체 실행과 분리한다.

초기 확인에서 기존 로컬 AI 계약 파일 4개의 CRLF/LF 차이로 고정 해시 검사가 실패했다. 내용이 원격 develop과 동일하고 줄바꿈만 다름을 확인한 뒤 원격 기준 바이트로 복원했으며 계약 정의·기대 해시·검증 강도는 변경하지 않았다. 최종 일반 전체 테스트에서 해당 검사는 통과했다.

## 남은 운영 확인

- 운영 환경 V61 배포·실제 관리자 1명 지정·새 로그인.
- 운영 JWT·세션 저장소를 통한 실제 브라우저 권한 확인.
- 프론트 UI 연동과 실제 운영 캐시 무효화 확인.
- 캐시 장애 시 기존 TTL까지 남을 수 있는 응답 처리, 감사 기록 보존기간 등 운영 정책 확정.

이번 작업은 운영 DB에 SQL을 적용하거나 실제 계정을 승격하지 않았다. UI·운영 배포·결제 관리자 처리까지 완료했다는 검증 기록이 아니다.
