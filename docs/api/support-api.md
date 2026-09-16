# 공지·문의 API

이슈 #52의 첫 구현 범위다. 법적 동의·비밀번호 재설정·결제 구독은 포함하지 않는다.

## 계약

모든 API는 Bearer 인증을 사용한다. 응답은 기존 `result`, `message`, `data`를 유지한다.
공지·문의 오류는 선택적 `code`를 추가한다. 성공 응답에는 code를 추가하지 않는다.
시간은 UTC ISO 8601이다. 구조화된 공지 sections와 문의 본문은 일반 텍스트로 렌더링한다.

| Method | Path | 응답 |
| --- | --- | --- |
| GET | /api/notices | 200, 게시된 공지 목록 |
| GET | /api/notices/{noticeId} | 200, 구조화 sections 포함 상세 |
| POST | /api/inquiries | 201, id/status/createdAt 접수 확인 |
| GET | /api/users/me/inquiries | 200, 본인 문의 목록 |
| GET | /api/users/me/inquiries/{inquiryId} | 200, 본인 문의 상세·답변 |

목록 query는 page=0, size=20이 기본이며 page≥0, size 1~100을 허용한다.
목록 data는 `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`를 제공한다.
공지 정렬은 pinned/publishedAt/id 내림차순, 문의는 createdAt/id 내림차순이다.
공지 상태가 공개이고 publishedAt이 현재 시각 이하일 때만 목록·상세에서 보인다.

문의 요청 필드는 category, subject, body, 선택 relatedSessionId/replyEmail이다.
category는 대문자 영문으로 시작하는 대문자 영문·숫자·밑줄 코드(최대 50자)이며 명세 예시는 ANALYSIS다.
전체 카테고리 taxonomy가 제공되지 않아 별도 enum을 추측해서 제한하지 않는다.
subject 1~100, body 1~2000 Unicode code point, 공백뿐인 문자열과 부적절한 제어문자를 거절한다.
replyEmail이 있으면 이메일 형식과 최대 254자를 검사한다. relatedSessionId는 본인 소유 세션이어야 한다.
문의 status는 RECEIVED/IN_PROGRESS/ANSWERED/CLOSED다.
다른 사용자 문의와 없는 문의는 모두 404 RESOURCE_NOT_FOUND다.
공지 작성·문의 답변용 관리자 API는 현재 프론트 명세 범위 밖이다.

## 중복 접수

POST는 선택 `Idempotency-Key` 헤더를 지원한다. 키는 공백 없는 ASCII 1~128자다.
같은 사용자·키·요청 필드값은 최초 201 접수 응답을 재사용한다. 다른 필드값은 409 CONFLICT다.
키가 없으면 매번 신규 문의를 생성한다. 키의 자동 만료는 없으며 문의가 보존되는 동안 재사용한다.
키와 요청 fingerprint는 SHA-256으로 저장한다. 본문 길이 접두사를 사용해 필드 경계 충돌을 막는다.
사용자 행 잠금과 사용자/키 unique 제약으로 동시 생성도 직렬화한다.
재시도 응답은 접수 당시 status=RECEIVED를 유지하며 최신 처리 상태는 상세 API에서 조회한다.
브라우저 CORS 허용 헤더에 Idempotency-Key를 추가했다.

## 오류

- 400 VALIDATION_ERROR: 형식·길이·페이지·헤더 오류.
- 401 AUTHENTICATION_REQUIRED: 로그인 필요/만료.
- 403 FORBIDDEN: 제한된 사용자 접수.
- 404 RESOURCE_NOT_FOUND: 공지·문의 미존재/숨김/소유권 불일치 또는 세션 미존재.
- 409 CONFLICT: 동일 멱등 키의 다른 요청.

## 기존 학습 완료 보강

이미 COMPLETED인 세션의 완료 재요청은 최초 완료 시각과 학습 시간을 유지한다.
ANALYZING에서만 최초 완료를 허용하고 음수 시간을 거절한다.
완료 쓰기는 기존의 세션 행 잠금을 사용한다. 분석 완료 여부와 소유권 검사는 유지한다.

## 검증

- SupportApiIntegrationTest: HTTP 계약, 인증, 소유권, 페이지, 공지 공개 조건, 멱등성, 잘못된 입력, CORS.
- InquiryConcurrencyIntegrationTest: 동시 요청 2개의 DB 결과가 하나인지 검증.
- SupportPostgresMigrationTest: PostgreSQL에서 V0→V17 신규 및 V16→V17 업그레이드, 기존 데이터 보존과 unique 제약 검증.
- PostgreSQL 검증은 독립 테스트 DB에 `VC_BE_TEST_POSTGRES_URL`과 선택 USER/PASSWORD 변수를 지정한다. 테스트별 임의 schema만 만들고 삭제한다.
- 전체 회귀 test/bootJar와 OpenAPI 한국어 문서 검증을 수행한다.

프론트 API_INTEGRATION.md/실제 타입의 최종 대조는 별도 필요하다. 현재 상세 응답은 전달된 신규 명세와 기존 items 기반 목록 관례를 따른다.
