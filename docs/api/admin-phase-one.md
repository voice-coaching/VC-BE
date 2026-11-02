# 관리자 1차 API 계약

## 적용 범위·인증

백엔드 구현 문서다. 프론트 UI·관리자 권한 부여·회원 정지/탈퇴·결제 처리·AI 재분석은 포함하지 않는다. 배포 시 V61가 필요하다.

추가 구현: 아나운서 문장에 수동 기준 음성을 업로드·연결하는 API 5개와 V62을 추가했다. [기준 음성 계약](admin-reference-audios.md)을 따른다. 아래 표는 기본 18개 API이며 TTS·클래스 예문 편집 전체를 구현한 것은 아니다.

모든 경로는 `Authorization: Bearer <accessToken>`을 요구한다. JWT 권한 `ROLE_ADMIN`과 현재 DB의 `users.role=ADMIN`, `status=ACTIVE`, `deleted_at IS NULL` 조건을 모두 만족해야 한다. 테스트 이용등급 `membershipType=admin`은 접근 권한이 아니다. DB 역할 변경은 이전 JWT에 즉시 반영되지 않으므로 최초 권한 부여 후 다시 로그인한다. 권한 회수·정지는 현재 DB 검사로 기존 ADMIN 토큰도 차단한다.

정상 응답은 `ApiResponse<T>`: `result:boolean`, `message:string`, `data:T`. `Cache-Control: no-store`를 반환한다. 생성 201, 조회·변경 200. 아래 표에 body가 없는 요청은 request body 없음이다. 각 path ID는 필수 정수다.

## 엔드포인트

| Method | Path | Query / Body | data |
| --- | --- | --- | --- |
| GET | `/api/admin/me` | 없음 | Access |
| GET | `/api/admin/users` | keyword, status, role, page, size | Page<User> |
| GET | `/api/admin/users/{userId}` | 없음 | User |
| GET | `/api/admin/content-categories` | **type 필수** | Category[] |
| GET | `/api/admin/contents` | keyword, type, category, status, difficulty, page, size | Page<Content> |
| GET | `/api/admin/contents/{contentId}` | 없음 | Content |
| POST | `/api/admin/contents` | `{content: ContentBody, reason}` | Content |
| PATCH | `/api/admin/contents/{contentId}` | `{version, content: ContentBody, reason}` | Content |
| PATCH | `/api/admin/contents/{contentId}/publication` | `{version, status, reason}` | Content |
| GET | `/api/admin/notices` | keyword, published, page, size | Page<Notice> |
| GET | `/api/admin/notices/{noticeId}` | 없음 | Notice |
| POST | `/api/admin/notices` | `{notice: NoticeBody, reason}` | Notice |
| PATCH | `/api/admin/notices/{noticeId}` | `{version, notice: NoticeBody, reason}` | Notice |
| GET | `/api/admin/inquiries` | keyword, status, category, page, size | Page<InquirySummary> |
| GET | `/api/admin/inquiries/{inquiryId}` | 없음 | Inquiry |
| PATCH | `/api/admin/inquiries/{inquiryId}/answer` | `{version, answer, reason}` | Inquiry |
| PATCH | `/api/admin/inquiries/{inquiryId}/status` | `{version, status, reason}` | Inquiry |
| GET | `/api/admin/audit-logs` | actorId, action, targetType, targetId, from, to, page, size | Page<Audit> |

### Query

모든 query는 표에서 필수로 표시한 type 외에는 생략할 수 있다. keyword는 최대 200자, 대소문자를 구분하지 않는 부분 일치이며 `%`, `_`, `!`는 검색 패턴으로 확장하지 않는다. 회원은 이메일·닉네임·문자열 ID, 콘텐츠·공지는 제목, 문의는 subject를 검색한다.

- page: 정수, 기본 0, 최소 0. size: 정수, 기본 20, 1~100. page×size가 정수 범위를 초과하면 400.
- 회원 status: ACTIVE / SUSPENDED / WITHDRAWN. role: USER / ADMIN.
- type: NEWS / SENTENCE / ANNOUNCER / CLASS_PRACTICE.
- 콘텐츠 status: DRAFT / PUBLISHED / HIDDEN. difficulty: BEGINNER / INTERMEDIATE / ADVANCED.
- category: 영문 저장 코드의 정확한 값. 콘텐츠 최대 100자, 문의 최대 50자. 등록용 목록은 공개 데이터가 없어도 code·label을 반환한다.
- 공지 published: boolean. false이면 비공개 설정인 공지를 조회한다. 공개 설정이 true지만 미래 공개 시각인 항목은 true 필터에 포함된다.
- 문의 status: RECEIVED / IN_PROGRESS / ANSWERED / CLOSED.
- 감사 actorId: 양의 정수. action: 최대 80자, targetType: 최대 30자, targetId: 문자열 최대 100자. 모두 정확 일치.
- 감사 from/to: ISO-8601 offset 시각, 양끝 포함, from≤to. 감사에는 keyword 검색이 없다.

목록은 회원·콘텐츠·문의의 생성 시각 내림차순, 공지의 공개 시각 내림차순, 감사의 생성 시각 내림차순이며 ID 내림차순을 보조 정렬로 사용한다. Category는 sortOrder·code 오름차순이다.

## 요청 필드

모든 변경의 reason은 필수·공백 불가·최대 500자다. 사유에 비밀번호·토큰·개인 원고·문의/답변 본문을 복사하지 않는다. version은 수정 대상 상세 응답의 정수값, 필수·0 이상이다. 생성에는 version을 보내지 않는다.

### ContentBody

| 필드 | 타입·필수 | 제약 |
| --- | --- | --- |
| type | enum, 필수 | 위 ContentType. 생성 후 변경 불가 |
| focus | enum, 필수 | PRONUNCIATION / INTONATION / BOTH |
| category | string, 필수 | 최대 100자. type에 등록된 code, CUSTOM 제외 |
| difficulty | enum, 필수 | BEGINNER / INTERMEDIATE / ADVANCED |
| title | string, 필수 | 공백 불가, 최대 255자 |
| description | string 또는 null, 선택 | 최대 1,000자 |
| scriptText | string, 필수 | 공백 불가, 최대 10,000자. 분석 접수의 별도 입력 한도까지 변경하는 설정이 아님 |
| targetPronunciations | string[], 필수 | 빈 배열 가능, 최대 100개, 항목별 공백 불가·200자 이하 |
| estimatedSeconds | integer, 필수 | 1~3,600 |

생성은 owner가 없는 공용 DRAFT로 저장한다. 수정은 공용·DRAFT·publishedAt=null인 항목만 허용한다. 공개 또는 공개 후 숨김 항목은 원문 직접 편집을 거절한다. 기존 내용을 수정하려면 새 초안을 만든다. 회원 전용 콘텐츠는 관리자 목록·상세·변경에서 제외하고 직접 ID로 접근해도 404다.

publication.status는 PUBLISHED/HIDDEN만 허용한다. DRAFT→HIDDEN과 DRAFT로의 복귀는 409다. 공개 시 필수값·분류를 다시 검증하고 최초 공개 시각을 기록한다. 숨김·재공개는 기존 공개 시각을 보존한다. TTS 생성 완료를 공개 성공으로 보장하지 않는다.

### NoticeBody

| 필드 | 타입·필수 | 제약 |
| --- | --- | --- |
| title | string, 필수 | 공백 불가, 최대 200자 |
| summary | string, 필수 | 공백 불가, 최대 500자 |
| sections | Section[], 필수 | 1~30개 |
| sections[].title | string, 필수 | 공백 불가, 최대 200자 |
| sections[].paragraphs | string[], 필수 | 1~50개, 항목별 공백 불가·4,000자 이하 |
| pinned | boolean, 필수 | 상단 고정 |
| published | boolean, 필수 | 공개 설정 |
| publishedAt | offset datetime, 필수 | 미래 예약 가능. 비공개 생성도 시각 지정 필요 |

전체 paragraph 문자 수는 30,000자 이하이며 HTML 태그 형태의 본문은 거절한다. 일반 텍스트 sections이며 임의 HTML·이미지 업로드·팝업 기능은 없다. 수정은 부분 필드 병합이 아닌 NoticeBody 전체 값을 보내는 방식이다.

### 문의 변경

- answer: string 필수, 공백 불가, 최대 4,000자. 저장 시 ANSWERED, answeredAt=서버 시각. CLOSED에서는 저장 불가.
- status: enum 필수. 상태 변경 API는 RECEIVED→IN_PROGRESS, ANSWERED→CLOSED만 허용한다. ANSWERED는 답변 API로만 전환한다. CLOSED 재개와 상태의 동일값 재전송도 409다.
- 답변은 사용자 본인 문의 조회에도 반영된다. 이메일·푸시 자동 발송은 하지 않는다.

## 응답 필드

아래 필드는 각 객체의 고정 응답 필드이며 nullable로 표기한 항목 외에는 non-null이다. 시각은 ISO-8601 offset 문자열이다.

| 객체 | 필드 |
| --- | --- |
| Page<T> | items:T[], page:int, size:int, totalElements:long, totalPages:int, hasNext:boolean. 기존 공통 PageResponse의 content 필드 대신 이 관리자 DTO는 items를 사용 |
| Access | id:long, nickname:string, role:string(ADMIN) |
| User | id:long, maskedEmail:string nullable, nickname:string, status:회원 상태 enum 문자열, role:USER/ADMIN, createdAt:datetime, membershipType:free/paid/admin, membershipExpiresAt:datetime nullable, membershipEnvironment:string nullable, loginProviders:string[] |
| Category | type:ContentType, code:string, label:string, sortOrder:int |
| Content | id:long, version:long, type:ContentType, focus:LearningFocus, category:string nullable(기존 데이터), difficulty:Difficulty, title:string, description:string nullable, scriptText:string, targetPronunciations:string[] nullable(기존 데이터), estimatedSeconds:int nullable(기존 데이터), status:PublishStatus, publishedAt:datetime nullable, updatedAt:datetime |
| Notice | id:long, version:long, title:string, summary:string, sections:Section[], pinned:boolean, published:boolean, publishedAt:datetime |
| Section | title:string, paragraphs:string[] |
| InquirySummary | id:long, version:long, userId:long, category:string, subject:string, status:문의 상태 문자열, createdAt:datetime |
| Inquiry | InquirySummary 필드 전체 + body:string, relatedSessionId:long nullable, replyEmail:string nullable, answer:string nullable, answeredAt:datetime nullable |
| Audit | id:long, actorId:long, action:string, targetType:string, targetId:string, reason:string, changedFields:string, outcome:string(SUCCESS), correlationId:string(UUID), createdAt:datetime |

User.loginProviders는 상세에서만 조회한다. 목록에서는 빈 배열이다. LOCAL 및 소셜 제공자 코드가 들어가며 외부 제공자 계정 ID·비밀번호·토큰은 반환하지 않는다. 이메일은 상세에서도 마스킹한다.

Audit.changedFields는 변경 대상 필드명들을 쉼표로 연결한 문자열이다. 원문·답변·변경 전후 값은 저장하지 않는다. action은 CONTENT_CREATE / CONTENT_EDIT / CONTENT_PUBLICATION / NOTICE_CREATE / NOTICE_EDIT / INQUIRY_ANSWER / INQUIRY_STATUS, targetType은 CONTENT / NOTICE / INQUIRY다. 1차는 성공한 변경만 감사 테이블에 저장한다. 인증 실패·검증 거절은 성공 감사 기록이 아니다.

기준 음성 확장에서는 REFERENCE_AUDIO_UPLOAD_REQUEST / REFERENCE_AUDIO_REGISTER / REFERENCE_AUDIO_PRIMARY 작업과 REFERENCE_AUDIO 대상을 추가한다. UPLOAD_REQUEST는 CONTENT를 대상으로 한 업로드 기록 생성이며 파일 연결 완료를 뜻하지 않는다.

## 오류·동시 수정·재시도

실패 응답은 `{result:false,message:string,data:null,code?:string}`이다. 기존 공통 입력 검증 응답은 code가 생략될 수 있다.

| HTTP | code | 발생 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR 또는 공통 검증 응답 | 필수값, enum, 길이, 페이지, 등록되지 않은 category |
| 401 | AUTHENTICATION_REQUIRED 등 기존 인증 코드 | 토큰 없음·만료·세션 무효 |
| 403 | FORBIDDEN 또는 ACCESS_DENIED | JWT ROLE_ADMIN 없음 / 현재 계정 비활성·삭제·권한 회수 |
| 404 | RESOURCE_NOT_FOUND | 대상 없음, owner가 있는 콘텐츠 |
| 409 | ADMIN_EDIT_CONFLICT | version 불일치, 원문 잠금, 허용하지 않는 상태 전이, DB 잠금 충돌 |
| 503 | ADMIN_STORE_UNAVAILABLE | 관리자 권한 조회·관리 데이터·감사 저장소 오류 |

수정은 DB 행 잠금과 version 비교 후 수행하며 감사 기록과 같은 트랜잭션으로 커밋한다. 감사 저장 실패 시 업무 변경도 롤백한다. 409에서는 최신 상세를 다시 읽고 사용자가 변경 내용을 확인한다.

생성은 Idempotency-Key 계약을 추가하지 않았다. 생성 요청의 네트워크 응답을 잃으면 제목·시각·감사 목록으로 반영 여부를 확인하고 무조건 POST를 반복하지 않는다. 수정도 응답 유실 시 상세를 먼저 조회한다.

콘텐츠 캐시는 DB 커밋 후 무효화한다. 캐시 장애는 경고 로그로 남기고 이미 커밋한 작업을 실패했다고 응답하지 않는다. 장애 시 일부 기존 캐시 응답은 기존 TTL까지 남을 수 있으므로 운영자는 캐시 상태와 사용자 조회를 함께 확인한다. 이 변경은 운영 DB에 자동 접속하거나 관리자를 일괄 생성하지 않는다.

## 호출 예시

```http
POST /api/admin/contents
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "content": {
    "type": "SENTENCE",
    "focus": "PRONUNCIATION",
    "category": "FINAL_CONSONANT",
    "difficulty": "BEGINNER",
    "title": "받침 연습 문장",
    "description": "받침을 빠뜨리지 않고 읽습니다.",
    "scriptText": "밤이 되면 따뜻한 밥을 먹습니다.",
    "targetPronunciations": ["밤", "밥"],
    "estimatedSeconds": 8
  },
  "reason": "일반 문장 콘텐츠 보완"
}
```

생성 응답의 id·version으로 공개한다. 숫자는 예시이며 실제 반환값을 사용한다.

```http
PATCH /api/admin/contents/123/publication
```

```json
{"version": 0, "status": "PUBLISHED", "reason": "내용 검수 완료"}
```

프론트는 먼저 `/api/admin/me`로 접근 여부를 확인한다. 모든 목록·상세를 서버 응답으로 구성하고 role·version·코드값을 임의 생성하지 않는다. UI 배치는 [구현 계획서](../architecture/admin-implementation-plan.md)를 참고한다.


## 관리자 알림·회원 기록 추가 구현

전체·개별 앱 내 알림, 수동 요금제 안내, 기간 지정 전체 팝업, 회원별 기록·분석 UUID 조회를 추가했다. API 12개(관리자11·사용자1), V63 신규 테이블3개이며 프론트는 수정하지 않았다. [API 계약](admin-communications-and-members.md), [DB 구조](../database/admin-communications.md)를 따른다. 웹 푸시·결제 자동 알림·회원 PK UUID 전환은 포함하지 않는다.
