# 연습 알림 설정·알림함·Push 등록

이슈 #66, 상위 #52. 기존 Bearer 인증/envelope를 사용한다. 법적 동의·비밀번호 재설정·결제 구독은 제외한다.

## API

| Method / Path | 응답 data |
| --- | --- |
| GET /api/users/me/notification-preferences | practiceReminder {enabled,time,daysOfWeek,timezone}, marketing {enabled:false}, updatedAt |
| PATCH /api/users/me/notification-preferences | 변경 후 설정 |
| POST /api/users/me/push-subscriptions | 201 {id,deviceName,active,createdAt} |
| DELETE /api/users/me/push-subscriptions/{subscriptionId} | 200 null |
| GET /api/notifications?page=0&size=20&unreadOnly=false | items,page,size,totalElements,totalPages,hasNext,unreadCount |
| PATCH /api/notifications/{notificationId}/read | 최초 readAt을 보존한 알림 항목 |
| POST /api/notifications/read-all | {updatedCount} |

알림 항목은 id,type,title,body,deepLink,readAt,createdAt이다. 시간은 UTC, 미읽음 readAt은 null이다.
목록은 createdAt DESC/id DESC, 크기 1~100. unreadCount는 필터·페이지와 관계없는 본인 전체 미읽음 수다.
홈 배지는 별도 알림 목록 조회를 사용한다. 다른 사용자 항목과 미존재 ID는 모두 404다.

## 환경설정

최초 GET은 행을 생성하지 않고 enabled=false, time=21:00, 모든 요일, Asia/Seoul, 사용자 생성 시각 updatedAt을 반환한다.
PATCH는 전달한 하위 필드만 변경하며 생략/null은 기존 값 유지다. 빈 PATCH는 400이다.
time은 정확한 HH:mm, timezone은 JVM이 제공하는 IANA ID, 요일은 MON~SUN이며 중복은 불허한다. 활성화 시 최소 한 요일이 필요하다.
marketing.enabled=true는 동의 기능 제외 상태이므로 409 MARKETING_CONSENT_REQUIRED다. 동의 저장·변경 API를 추가하지 않는다.

## 앱 내 연습 알림

PRACTICE_REMINDERS_ENABLED=true로 스케줄러를 활성화한다(기본 false). 1분마다 활성 설정을 100명씩 조회하고 사용자별 트랜잭션에서 생성한다.
지정 현지 요일·시간 이후 5분 이내에 앱 내 알림을 한 번 생성한다. 서버가 5분 이상 중단됐을 때 지난 알림을 소급 생성하지 않는다.
날짜별 deduplication key와 사용자 DB 잠금으로 다중 인스턴스·재시도·시간대 변경 시 같은 현지 날짜의 중복을 방지한다.
DST로 사라지는 시각은 Java ZoneRules에 따라 gap 길이만큼 이동하고, 겹치는 시각은 앞선 offset을 사용한다.
설정 변경·생성·읽음·탈퇴는 같은 사용자 잠금 순서를 사용한다. 마케팅 알림은 생성하지 않는다.
제목·본문은 실제 연습 안내이며 추천 기사 수처럼 확인하지 않은 수치를 포함하지 않는다. deepLink는 /home,/notifications,/mypage만 허용한다.

## Push 등록과 암호화

요청은 endpoint, keys {p256dh,auth}, userAgent, 선택 deviceName이다. endpoint는 HTTPS, 최대 2048자이며 사용자정보/fragment/비표준 port를 허용하지 않는다.
허용 호스트는 fcm.googleapis.com, updates.push.services.mozilla.com 및 *.push.services.mozilla.com, web.push.apple.com, *.notify.windows.com이다.
키는 base64url P-256 uncompressed point 65바이트(곡선 위 점 검증), auth 16바이트다. userAgent 최대 512자, deviceName 최대 100자. 요청 내용을 로그에 남기지 않는다.
같은 endpoint는 같은 사용자에게 같은 ID로 재등록/활성화된다. 다른 사용자의 endpoint를 가져오지 않고 409를 반환한다. 사용자별 최대 10개(비활성 포함)다.
선택 Idempotency-Key는 1~128자 printable ASCII다. 같은 사용자·키·본문은 최초 응답을 재생하고 본문 변경은 409다. 등록 삭제 후 같은 키 재생은 404로 부활을 막는다.
삭제 재시도는 404이며 사용자 탈퇴 시 설정·알림·등록·멱등 원장을 모두 삭제한다.

PUSH_SUBSCRIPTION_ENCRYPTION_KEY는 Base64로 인코딩한 32바이트 키다. 미설정/잘못된 키는 등록을 503 PUSH_STORAGE_UNAVAILABLE로 차단한다.
endpoint/브라우저 키/userAgent는 AES-256-GCM 랜덤 nonce로 암호화하고 endpoint SHA-256과 요청 HMAC만 중복 검사에 사용한다. 원문 fallback은 없다.
암호화 payload v1은 endpoint,p256dh,auth,userAgent,deviceName 순서의 UTF-8 길이 접두사 필드이며 null 길이는 -1이다. nonce 12바이트와 GCM ciphertext를 base64로 저장한다. AAD는 VC-BE:push:v1이다.
키 교체에는 기존 payload/요청 fingerprint 이관이 필요하다. 이번 작업은 운영 키를 설정하지 않는다.
recordPushResponse 경계는 404/410 응답을 받은 등록을 비활성화한다. 발송 시각에 읽은 updatedAt과 현재 값이 같을 때만 적용하여 갱신된 등록을 오래된 응답으로 비활성화하지 않는다.

**이번 구현은 Push 등록 API와 만료 처리 경계다. 실제 Web Push 발송 어댑터·VAPID 키·브라우저 서비스 워커 종단 검증은 남아 있다.**
앱 내 알림 생성은 실제 브라우저 Push 전달을 의미하지 않는다. 운영 스케줄러 설정도 변경하지 않았다.

## 오류와 검증

400 VALIDATION_ERROR, 401 기존 인증 오류, 탈퇴/정지 사용자 403 FORBIDDEN, 404 RESOURCE_NOT_FOUND,
409 CONFLICT/MARKETING_CONSENT_REQUIRED, 429 PUSH_SUBSCRIPTION_LIMIT, 503 PUSH_STORAGE_UNAVAILABLE.
V24는 새 4개 테이블과 조회/중복 제약을 추가하며 기존 데이터는 변경하지 않는다.
시간대/DST/요일·부분 갱신·동시 등록/중복 알림·사용자 격리·읽음·암호화·탈퇴·HTTP 계약과 PostgreSQL fresh/upgrade를 검증한다.
