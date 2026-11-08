# Android FCM

Android 앱은 로그인 사용자 기준 `PUT /api/users/me/native-fcm-devices`에 `{installationId, token}`을 제출하고, 로그아웃 전에 `DELETE /api/users/me/native-fcm-devices/{installationId}`를 호출한다. 설치 ID는 UUID이며 토큰은 응답·로그에 포함하지 않는다. 토큰은 AES-256-GCM으로 저장한다. 기기 토큰이 갱신되면 같은 설치 ID로 다시 등록한다.

V70는 알림함에 새로운 항목이 생기는 트랜잭션에서 등록된 기기별 outbox를 만든다. worker는 FCM HTTP 전송을 DB 트랜잭션 밖에서 수행한다. 실패는 최대 8회 지수 백오프로 재시도하고, 명시적 UNREGISTERED만 현재 토큰과 일치할 때 삭제한다. 외부 요청 성공 뒤 DB 확정 전에 장애가 나면 중복 발송이 가능하다. 생성 후 24시간이 지난 미발송 항목은 발송하지 않는다. 이 작업은 iOS 발송을 활성화하지 않는다.

운영 반영에는 DB migration, 서버와 프론트엔드 배포, Android 앱 재빌드가 필요하다. 기본적으로 worker는 비활성이다. EC2 서비스 환경에 다음 설정이 필요하다.

- `GOOGLE_APPLICATION_CREDENTIALS`: 기존 WIF ADC 구성 파일 경로.
- `NATIVE_FCM_PROJECT_ID`: Firebase 프로젝트 ID.
- `NATIVE_FCM_ENCRYPTION_KEY`: 안전한 비밀 저장소에서 생성·보관한 32바이트 무작위 키의 Base64 값. 변경하면 기존 토큰을 복호화할 수 없으므로 영구 보관·복구와 키 교체 계획이 필요하다. Git, 로그, 클라이언트에 넣지 않는다.
- `NATIVE_FCM_ENABLED=true`: migration과 인증·키 검증 후 worker 활성화.
- `PRACTICE_REMINDERS_ENABLED=true`: 연습 시간 알림이 필요한 경우 별도 활성화.

설정 전후 서비스 계정으로 ADC를 확인하고, 격리된 계정과 실제 Android 기기에서 등록·갱신·로그아웃·알림 권한 거부·포그라운드/백그라운드·터치·오프라인 재시도·잘못된 토큰 처리를 검증해야 한다. SDK의 성공 응답은 기기 표시를 보증하지 않는다. 운영 변경과 실제 발송은 이 문서 작성 시점에 수행하지 않았다.

## 로컬 검증

격리된 PostgreSQL 16에서 전체 Flyway migration과 실제 HTTP 인증, 기기 토큰 등록·중복 등록·갱신·타 계정 거부·암호화 저장·대기열 생성·삭제를 확인했다. 발송은 Firebase 네트워크 대신 mock을 사용해 worker의 대기열 처리와 완료 상태를 검증했다. 토큰 암호화·재시도 지연 파싱 및 알림 시간·중복 생성 방지 테스트를 별도로 실행했다. 실제 Firebase 발송, 운영 DB 적용과 Android 기기 수신은 수행하지 않았다.
