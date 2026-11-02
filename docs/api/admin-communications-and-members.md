# 관리자 확장: 전체·개별 알림, 기간 팝업, 회원별 기록

## 범위와 권한

백엔드 API 구현이다. 프론트 UI와 운영 발송은 수행하지 않았다. 기본 관리자 API 및 아나운서 음성 관리에 이어 V64을 적용한다. 최신 develop `6ae98d1`의 코칭 사용량·인기 뉴스 기능을 보존한다.

관리자 경로는 JWT `ROLE_ADMIN`과 현재 DB의 `users.role=ADMIN`, `status=ACTIVE`, `deleted_at IS NULL`을 모두 확인한다. 테스트 이용등급 `membershipType=admin`은 관리자 권한이 아니다. 모든 정상 응답은 `ApiResponse`와 `Cache-Control: no-store`를 사용한다. 생성 201, 나머지 200이다. 목록은 `page=0`, `size=20`, 최대 100이다.

알림 채널은 **앱 내 알림함**이다. `PLAN`은 관리자가 작성하는 요금제 안내다. 웹 푸시·이메일·결제 완료/만료 시 자동 알림은 이번 구현에 포함하지 않았다. 광고·홍보성 알림 동의 관리도 구현한 것으로 간주하지 않는다.

## 1. 전체·개별 알림

| Method | Path | 입력 / 응답 data |
| --- | --- | --- |
| GET | `/api/admin/notification-campaigns` | page, size → Page<Campaign> |
| POST | `/api/admin/notification-campaigns` | 아래 생성 body → Campaign |
| GET | `/api/admin/notification-campaigns/{campaignId}` | Campaign, 초안의 현재 대상 인원 포함 |
| POST | `/api/admin/notification-campaigns/{campaignId}/dispatch` | `{version, reason}` → Campaign |

전체 안내 생성 예시:

```json
{
  "audience": "ALL_ACTIVE",
  "kind": "GENERAL",
  "title": "서비스 점검 안내",
  "body": "점검 시간 동안 일부 기능 이용이 제한됩니다.",
  "deepLink": "/notifications",
  "userIds": [],
  "reason": "정기 점검 안내 등록"
}
```

개별 요금제 안내는 `audience=SELECTED_USERS`, `kind=PLAN`, `userIds=[회원ID]`, `deepLink=/mypage`로 생성한다. 여러 회원을 지정할 수 있으며 최대 500명, 중복·없는 회원·비활성 회원은 생성 시 400이다. `ALL_ACTIVE`에는 userIds를 지정하지 않는다. 일반 안내 `GENERAL`과 요금제 안내 `PLAN`을 구분하며 등급 자체를 변경하지 않는다.

생성 직후에는 `status=DRAFT`이고 아직 알림이 전달되지 않는다. 상세 화면에서 내용·대상·`audienceCount`를 확인한 뒤 다음 요청을 보낸다.

```http
POST /api/admin/notification-campaigns/1/dispatch
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{"version": 0, "reason": "대상과 내용 확인 후 발송"}
```

응답 Campaign 필드:

```ts
type Campaign = {
  id: number;
  version: number;
  audience: "ALL_ACTIVE" | "SELECTED_USERS";
  kind: "GENERAL" | "PLAN";
  title: string;
  body: string;
  deepLink: "/home" | "/notifications" | "/mypage";
  userIds: number[];
  status: "DRAFT" | "SENT";
  audienceCount: number;
  sentCount: number;
  createdAt: string;
  sentAt: string | null;
};
```

- 전체 대상은 **발송 시점**의 활성·미탈퇴 회원이다. 관리자 계정도 활성 회원이면 포함한다. 초안 생성 후 가입한 회원도 포함된다.
- 선택 대상 중 발송 전에 탈퇴·정지된 회원은 제외한다. 초안의 대상 인원은 이후 바뀔 수 있으며 발송 후 `sentCount`가 실제 전달 수다. 발송 후 `audienceCount`도 실제 전달 수로 고정한다.
- 알림 본문은 최대 1,000자, 제목은 최대 200자, reason은 최대 500자다. HTML 입력을 지원하지 않는다. 프론트는 plain text로 렌더링한다.
- deepLink는 위 세 경로만 허용한다. 임의 외부 링크나 HTML을 보내지 않는다.
- 발송 중 행 잠금과 기존 `user_notifications`의 회원별 중복 키를 사용한다. 같은 campaign 재발송 요청은 기존 SENT 결과만 반환하고 새 알림을 만들지 않는다. 다른 내용의 새 발송은 새 초안을 만든다.
- 발송 전 stale version은 409다. 이미 SENT인 요청의 재시도는 이전 version으로도 기존 결과를 돌려준다. 새 초안 생성 자체의 중복 방지 키는 제공하지 않으므로 응답 유실 시 목록을 먼저 확인한다.
- 발송·상태 변경·성공 감사 기록은 동일 트랜잭션이다. 중간 실패 시 전부 롤백한다.
- 모든 수신자에게 한 트랜잭션으로 INSERT SELECT한다. 대규모 회원 수에서의 처리 시간·잠금 시간은 별도 부하 검증이 필요하다.

사용자는 기존 `GET /api/notifications`에서 확인한다. type은 `ADMIN_GENERAL` 또는 `ADMIN_PLAN`이다. 기존 읽음 처리 API를 그대로 사용한다. 연습 리마인더 설정은 이 운영 안내의 수신 제외 조건으로 사용하지 않는다. 별도 브라우저 푸시를 발송하지 않는다.

## 2. 기간 지정 전체 팝업

| Method | Path | 입력 / 응답 data |
| --- | --- | --- |
| GET | `/api/admin/popups` | page, size → Page<Popup> |
| POST | `/api/admin/popups` | `{popup, reason}` → Popup |
| GET | `/api/admin/popups/{popupId}` | Popup |
| PATCH | `/api/admin/popups/{popupId}` | `{version, popup, reason}` → Popup |
| GET | `/api/users/me/popup` | 활성 로그인 회원 → 현재 Popup 또는 null |

생성 body:

```json
{
  "popup": {
    "title": "신규 요금제 안내",
    "body": "마이페이지에서 이용 가능한 요금제를 확인해 주세요.",
    "deepLink": "/mypage",
    "priority": 10,
    "startsAt": "2026-11-02T09:00:00+09:00",
    "durationDays": 3,
    "enabled": true
  },
  "reason": "3일간 전체 안내"
}
```

`durationDays`는 1~365일이며 서버가 `endsAt=startsAt+durationDays`를 계산한다. priority는 0~100, deepLink는 null 또는 허용된 세 경로다. 제목·본문·reason 길이는 알림과 같다. startsAt에는 UTC 오프셋이 필요하다. 클라이언트 시각으로 게시 여부를 결정하지 않는다.

관리자 응답은 `id, version, title, body, deepLink, priority, startsAt, endsAt, enabled, createdAt`이다. 수정은 전체 popup 필드를 다시 보내며 `enabled=false`로 즉시 노출 중지할 수 있다. stale version은 409다.

사용자 응답 data는 `id,title,body,deepLink,startsAt,endsAt` 또는 null이다. 로그인하지 않으면 401이다. 활성 회원에게 서버 시각 기준 `enabled=true` 및 `startsAt <= now < endsAt`인 팝업 중 priority·startsAt·id 내림차순 첫 번째 한 개를 반환한다.

프론트는 로그인 후 이 API를 호출하고 data가 있으면 모달을 표시한다. 현재 방문에서 닫는 상태는 프론트가 관리한다. ‘오늘 그만 보기’, 회원별 영구 확인·클릭 통계, 비로그인 방문자 팝업은 구현하지 않았다. 팝업을 활성화해도 별도의 알림함 항목은 자동 생성하지 않는다.

## 3. 전체 회원 및 회원별 기록

기존 `GET /api/admin/users?keyword=&status=&role=&page=0&size=20`으로 전체 회원을 조회한다. status를 생략하면 활성·정지·탈퇴 회원 모두 조회하며 이메일은 마스킹한다. 회원 데이터 삭제·권한 변경 API는 추가하지 않는다.

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/admin/users/resolve?identifier={value}` | 양의 숫자 userId 또는 표준 UUID로 회원 찾기 |
| GET | `/api/admin/users/{userId}/overview` | 회원 정보, 분류별 건수, 현재 코칭 사용량 |
| GET | `/api/admin/users/{userId}/records?resource=ANALYSES&page=0&size=20` | 해당 회원 소유 관련 기록 |

**현재 users.id는 UUID가 아닌 bigint다.** UUID를 회원 PK로 새로 추가하지 않는다. resolve는 다음 연결만 사용한다.

- 숫자: 기존 회원 ID. 없으면 404.
- UUID: 분석 requestId·executionId·canonical resultEventId → 녹음 → 세션 → 회원.
- 직접 분석 jobId·executionId → 기존 회원 이력 연결 → 회원. 연결 전 작업은 회원을 추정하지 않는다.

UUID 조회 응답 data는 `{userId, source: "ANALYSIS" | "DIRECT_ANALYSIS" | "USER_ID"}[]`다. 관련 회원이 없으면 빈 배열이고 여러 연결이면 여러 항목을 반환한다. source가 다르면 같은 회원이 두 번 나올 수 있으므로 프론트는 userId로 선택지를 묶을 수 있다. 비표준 UUID·SQL 문자열은 400이다. 연결이 보존된 과거 분석만 조회 가능하며 변경 전의 모든 execution UUID 이력을 복원하는 API는 아니다.

overview data:

```ts
type MemberOverview = {
  user: User; // 기존 관리자 회원 상세 계약
  counts: {resource: string; count: number}[];
  coachingUsage: {
    membershipType: string; environment: string | null; unlimited: boolean;
    startsAt: string; expiresAt: string | null; limit: number | null;
    used: number; reserved: number; remaining: number | null;
    canRequestCoaching: boolean;
  } | null;
  usageUnavailableReason: "ACCOUNT_INACTIVE" | null;
};
```

코칭 사용량은 develop의 기존 CoachingUsageService를 재사용한다. 비활성 회원도 관련 이력은 조회할 수 있으나 현재 사용 가능량은 null이다. 무제한 TEST 이용등급은 별도의 실제 관리자 권한이 아니다.

records는 기존 페이지 envelope를 사용하며 items 각 항목은 `{fields: {...}}`다. 아래 whitelist 분류의 고정 필드만 반환한다. 페이지는 해당 회원 소유 범위에서 계산한다. count는 해당 분류 전체 건수이며 삭제 상태 메타데이터도 포함할 수 있다.

| resource | fields |
| --- | --- |
| TRAINING_SESSIONS | id, contentId, courseStepId, status, startedAt, completedAt |
| RECORDINGS | id, sessionId, attemptNo, mimeType, durationMs, qualityStatus, createdAt, deletedAt |
| ANALYSES | id, recordingId, status, failureCode, requestId, executionId, resultEventId, createdAt, analyzedAt |
| PAYMENTS | id, product, amount, currency, status, createdAt, paidAt, expiresAt |
| CANCELLATIONS | id, orderId, status, requestedAt, updatedAt (취소·전액 환불 공용 요청 원장) |
| INQUIRIES | id, category, status, createdAt, answeredAt (본문은 기존 관리자 문의 상세에서 조회) |
| NOTIFICATIONS | id, type, title, readAt, createdAt |
| COURSE_PROGRESS | id, courseId, lastStepId, status, progressPercent, updatedAt |
| TITLES | userId, rank, minimumTrainingCount, updatedAt |
| TITLE_EXAMS | id, targetRank, status, sessionId, analysisId, score, createdAt, evaluatedAt |
| DIRECT_ANALYSES | jobId, executionId, contentId, linkedAt, archivedAt |
| COACHING_USAGE | analysisId, recordingId, requestId, state, startsAt, expiresAt |
| PRIVATE_CONTENTS | id, contentType, status, createdAt, updatedAt (암호화 개인 원문 제외) |

PAYMENTS와 CANCELLATIONS는 현재 TEST 결제 원장이다. 운영 실결제 내역인 것처럼 표시하지 않는다. UUID·시각은 문자열, 상태는 기존 DB 코드, nullable 필드는 null이다. 결과 전문·개인 원고·원본 음성 URL·비밀번호/해시·토큰·push secret·이력 claim은 반환하지 않는다. 임의 테이블명/컬럼명을 받아 DB 전체를 덤프하는 기능이 아니다.

## 4. 프론트 연결 순서 및 오류

1. 관리자 `/api/admin/me` 확인 후 메뉴에 회원·알림·팝업 관리를 배치한다.
2. 알림은 초안 생성 → 상세 검토 → 명시적 발송 버튼 → SENT 및 실제 발송 수 확인 순으로 연결한다.
3. 기존 사용자 알림함에서 `ADMIN_GENERAL`, `ADMIN_PLAN`을 표시한다.
4. 팝업 편집 폼은 시작 시각·일수·우선순위·활성 여부를 받는다. 사용자 화면은 `/api/users/me/popup`을 조회한다.
5. 회원 목록의 숫자 id를 상세에 연결하고, 별도 검색란에서 UUID resolve 결과의 userId를 사용한다. overview의 counts로 탭별 건수를 표시하고 탭을 열 때 records를 페이지 단위로 조회한다.

401은 로그인, 403은 실제 관리자 권한·현재 회원 상태 확인, 400은 필드/분류/대상 확인, 404는 대상 재조회, 409는 최신 version 재조회, 503은 저장소 장애 안내와 재시도다. 발송 응답 유실 시 새 campaign을 만들지 않고 기존 ID의 상세를 확인하거나 같은 ID dispatch를 재시도한다. reason에 비밀정보를 입력하지 않는다.

## 5. 반영 상태

별도 브랜치 `feat/admin-phase-one-api`에서 구현했다. rebase 기준 원격 develop은 `6ae98d1`이다. 기존 사용자 문서 변경은 보존했고 프론트 수정·실제 발송·운영 DB 실행·배포는 수행하지 않았다. V62/V63/V64을 적용하는 백엔드 배포와 프론트 UI 연결 후 환경별 확인이 필요하다. 실제 관리자 계정 지정도 별도 관리 절차를 따른다.
