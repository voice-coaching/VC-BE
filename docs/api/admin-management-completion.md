# 기존 관리자 기능 보완 (#188)

## 범위와 인증

최신 원격 main `b765411` 기준으로 기존 공지·아나운서 음성·알림 초안 관리만 보완한다. 프론트·배너·FAQ·클래스 편집·FCM·운영 DB 직접 변경은 제외한다. 서명된 Bearer Access Token과 유효 세션, 현재 DB의 활성 ADMIN 권한이 필요하다. 테스트 이용등급 admin은 관리자 권한이 아니다.

모든 신규 API는 query 없이 정수 path ID를 받는다. 성공은 HTTP 200, Cache-Control: no-store, 기존 ApiResponse(result=true, message=처리 안내, data=아래 결과)다. 요청은 Content-Type: application/json이다.

## 공통 입력과 오류

`version`은 필수·null 불가·0 이상 정수, `reason`은 필수·null 불가·공백 제외 1~500자 문자열이다. 최근 상세/목록의 버전을 사용한다. 관리자 작업과 사유·대상·변경 필드 감사 이력은 같은 트랜잭션으로 저장한다. 삭제 후 동일 대상 재요청은 404이며 별도 멱등 성공 계약은 없다. 응답 유실 시 목록·상세를 재조회한다.

| HTTP | code | 메시지·조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 필수값·JSON·타입·사유·대상·허용 주소 오류. 메시지는 기존 공통 입력 검증 처리기를 따름 |
| 401 | UNAUTHORIZED | 인증이 필요하거나 Access Token이 만료되었습니다. 토큰·세션 누락/검증 실패 |
| 403 | ACCESS_DENIED | 접근 권한이 없습니다. 현재 활성 관리자 아님 |
| 404 | RESOURCE_NOT_FOUND | 요청한 리소스를 찾을 수 없습니다. 미존재·이미 삭제·retired 음성 또는 공용 ANNOUNCER 관리 범위 밖 |
| 409 | ADMIN_EDIT_CONFLICT | 대상 상태가 변경되었거나 수정할 수 없습니다. 다시 조회해 주세요. 버전 충돌·SENT 변경 시도 포함. DB 잠금 충돌은 기존 처리기의 대상 상태 변경 안내를 사용 |
| 503 | ADMIN_STORE_UNAVAILABLE | 관리자 요청을 안전하게 저장하거나 조회할 수 없습니다. 저장소 장애 시 변경·감사 기록 함께 롤백 |

## 1. 공지 삭제

`DELETE /api/admin/notices/{noticeId}`

```json
{"version":0,"reason":"종료된 공지 정리"}
```

noticeId는 공지 ID, version은 공지 버전이다. 행 잠금 후 버전을 비교하고 공지 DB 행을 삭제한다. 공개·비공개 모두 가능하다. 사용자·관리자 목록/상세에서 제외되며 NOTICE_DELETE 감사 이력은 보존한다. data=null, 복원 API는 없다. 단순 비공개는 기존 PATCH를 사용한다. 학습·결제 데이터와는 독립된 삭제다.

## 2. 기준 음성 개별 삭제

`DELETE /api/admin/reference-audios/{audioId}`

```json
{"version":3,"reason":"잘못 등록한 음성 정리"}
```

audioId는 활성 공용 ANNOUNCER 음성 ID다. version은 음성 자체 버전이 아니라 음성 목록의 contentVersion 또는 콘텐츠 version이다. 부모 콘텐츠 잠금 후 버전·음성 활성 여부를 재검사하며 콘텐츠 수정·음성 등록·대표 변경과 같은 잠금을 사용한다.

- 기존 reference_audios.retired_at 기록 및 is_primary=false. 행·object_key·업로드 연결·과거 학습 기록·S3 원본 보존.
- 대표 삭제 시 남은 활성 관리자 아나운서 음성 중 ID가 가장 작은 음성을 대표로 승계한다. 비대표 삭제는 기존 대표 유지. 마지막 음성 삭제도 가능하다.
- 콘텐츠 버전 증가·기존 콘텐츠 캐시 무효화. 이후 사용자·관리자 목록, 재생 URL 신규 발급, 대표 재지정에서 제외한다.
- 이미 발급한 S3 URL은 만료 시각까지 유효할 수 있다. 삭제 음성에 대한 기존 업로드 완료 재시도는 음성을 복원하지 않는다.
- 감사 이력 REFERENCE_AUDIO_DELETE 기록. 신규 업로드는 기존 절차를 사용한다.

성공 data는 기존 관리자 음성 목록 ListDto다: `{ "items": [], "contentVersion": 4 }`.

| 응답 필드 | 타입·null | 의미 |
| --- | --- | --- |
| items | array, null 불가 | 남은 활성 음성. 없으면 빈 배열 |
| contentVersion | integer, null 불가 | 변경 후 콘텐츠 버전 |
| items[].audioId, contentId | integer, null 불가 | 음성·콘텐츠 ID |
| items[].speakerName | string, null 불가 | 화자 이름 |
| items[].speakerType | string, null 불가 | ANNOUNCER |
| items[].durationMs | integer, null 가능 | 음성 길이 |
| items[].primary | boolean, null 불가 | 대표 여부 |
| items[].contentVersion | integer, null 불가 | 변경 후 콘텐츠 버전 |
| items[].createdAt | ISO date-time string, null 가능 | 기존 생성 시각 |

## 3. 발송 전 알림 초안 수정

`PATCH /api/admin/notification-campaigns/{campaignId}`

```json
{"version":0,"audience":"SELECTED_USERS","kind":"PLAN","title":"이용권 안내","body":"이용권 내용을 확인해 주세요.","deepLink":"/mypage","userIds":[14],"reason":"발송 전 대상 정정"}
```

campaignId는 알림 ID다. 일부 필드 수정이 아닌 다음 편집 필드 전체 교체 계약이다.

| 요청 필드 | 타입·필수·null | 제약 |
| --- | --- | --- |
| version | integer, 필수, null 불가 | 현재 알림 버전, 0 이상 |
| audience | string, 필수, null 불가 | ALL_ACTIVE 또는 SELECTED_USERS |
| kind | string, 필수, null 불가 | GENERAL 또는 PLAN |
| title | string, 필수, null 불가 | 공백 제외 1~200자, HTML 금지 |
| body | string, 필수, null 불가 | 공백 제외 1~1000자, HTML 금지 |
| deepLink | string, 필수, null 불가 | /home, /notifications, /mypage 중 하나 |
| userIds | integer array, 선택, null 허용 | null=빈 목록. 중복 없는 양수 최대500개. 전체는 빈 목록, 선택은 최소1명이며 활성·미삭제 회원만 가능 |
| reason | string, 필수, null 불가 | 공백 제외 1~500자 |

발송과 동일한 행 잠금 후 DRAFT·버전 검사 및 생성과 같은 입력 검증을 한다. 수정만으로 발송되지 않는다. SENT는 최신 버전이어도409다. 수정 후 버전으로 발송하며 오래된 발송 요청은409다. 기존 발송 재시도의 중복 방지 계약은 유지한다. 감사 이력 NOTIFICATION_EDIT를 기록한다.

성공 data는 기존 CampaignDto다. id/version(integer·필수), audience(ALL_ACTIVE/SELECTED_USERS), kind(GENERAL/PLAN), title/body/deepLink(string·필수), userIds(integer array·필수·정렬·전체는 빈 배열), status(DRAFT/SENT, 수정 성공 시 DRAFT), audienceCount(integer·현재 대상 수·발송 시 재평가), sentCount(integer·초안은0), createdAt(ISO 시각·필수), sentAt(ISO 시각·null 가능·수정 성공 시null)을 반환한다. sentAt 외 null은 허용하지 않는다.

## 4. 발송 전 알림 초안 삭제

`DELETE /api/admin/notification-campaigns/{campaignId}`

```json
{"version":0,"reason":"중복 초안 정리"}
```

campaignId는 알림 ID다. 발송과 같은 잠금 후 DRAFT·버전을 확인하고 초안과 선택 대상 연결을 함께 삭제한다. SENT는409이며 도착한 회원 알림의 삭제·회수 기능이 아니다. data=null, NOTIFICATION_DELETE 감사 이력 보존.

## 프론트 연결·배포

이번 작업은 백엔드만 변경한다. 연결 시 최신 버전·사유 전달, 성공 후 상세·목록·작업 이력 캐시 갱신이 필요하다. 음성 삭제 응답의 contentVersion과 대표 여부를 반영한다. 초안 수정·삭제 버튼은 DRAFT에서만 제공하되 서버 검증을 대체하지 않는다.

신규 마이그레이션·환경변수·외부 서비스 설정은 없다. 기존 main 스키마를 사용한다. 사용자 팝업 표시·예약 발송·FCM은 포함하지 않는다.
