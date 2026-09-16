# 프로필 사진 API

이슈 #56, 상위 #52. 법적 동의·비밀번호 재설정·결제 구독은 제외한다.

## 계약

모두 사용자 Bearer 인증이 필요하다. `/api/users/me/profile-image`:

| Method | 응답 | 동작 |
| --- | --- | --- |
| GET | 200 | 미등록은 data:null, 등록 시 사진 메타데이터 |
| POST | 201 | multipart `file`로 최초 등록, 기존 사진은 409 PROFILE_IMAGE_EXISTS |
| PUT | 200 | multipart `file`로 교체, 사진이 없으면 404 PROFILE_IMAGE_NOT_FOUND |
| DELETE | 204 | 멱등 삭제, 본문 없음 |

메타데이터: `id`, `imageUrl`, `originalFileName`, `mimeType`, `sizeBytes`, UTC `updatedAt`.
GET /api/users/me에도 `profileImageUrl`(없으면 null)을 제공한다.
POST는 선택 Idempotency-Key(공백 없는 ASCII 1~128자)를 지원한다. 같은 사용자·키·파일 바이트·정규화 파일명은 최초 결과를 반환한다. 다른 요청이나 아직 진행 중/실패한 예약의 같은 키는 409 CONFLICT다. 실패한 업로드를 다시 시작할 때는 새 키를 사용한다. 성공한 POST의 재시도는 교체/삭제 이후에도 최초 메타데이터를 반환하며 현재 사진은 GET으로 확인한다.

## 검증 및 저장

- JPEG/PNG/WebP 파일 signature 및 실제 디코딩 검증. 요청 MIME이나 확장자는 신뢰하지 않는다.
- 파일 최대 5MiB, multipart 요청 전체 최대 6MiB. 크기 초과 413 PAYLOAD_TOO_LARGE.
- 가로·세로 각각 128~4096px. 지원하지 않는 형식·손상 파일·치수 오류는 400 INVALID_PROFILE_IMAGE.
- 중앙 정사각 crop 후 최대 512px PNG로 새로 인코딩한다. 원본 및 EXIF/추가 메타데이터는 저장하지 않는다. 원본 이미지의 EXIF 회전은 적용하지 않는다.
- 원본 파일명은 경로/제어문자를 제거해 표시용으로만 저장한다. UUID 기반 스토리지 키에 파일명이나 사용자 ID를 넣지 않는다.
- S3 AES256 암호화, 자격증명은 기본 서버 credential chain을 사용한다. HTTPS CDN URL에는 토큰 query를 사용하지 않는다.
- 요청 처리에서 외부 스토리지 I/O는 DB 트랜잭션 밖에서 수행한다. 예약을 먼저 영속화하고 사용자 잠금으로 활성화를 직렬화한다. 동시 변경으로 기준 사진이 달라지면 409다.
- 교체·삭제·탈퇴 시 즉시 현재 URL을 갱신/제거하고 이전 파일은 10분 유예 후 비동기 삭제한다. 미완료 업로드도 같은 삭제 대상으로 처리한다. 실패한 삭제는 다시 시도한다. 원본은 처음부터 저장하지 않으므로 파생 PNG만 삭제한다.

## 운영 설정

`PROFILE_IMAGE_ENABLED=true`, `PROFILE_IMAGE_BUCKET`, `PROFILE_IMAGE_REGION`, `PROFILE_IMAGE_CDN_BASE_URL`이 필요하다. CDN base URL은 HTTPS여야 하며 인증 정보/query/fragment를 허용하지 않는다. S3 bucket의 `profiles/` prefix를 읽는 CDN origin과 접근 정책은 인프라에서 구성해야 한다. 원본 S3 bucket의 공개 접근은 필요하지 않다.

미설정/스토리지 장애는 503 TEMPORARY_UNAVAILABLE다. 조회·삭제는 업로드 설정이 없어도 동작하며 파일 정리는 설정 복구 후 재시도한다. 버전 관리 bucket을 사용할 경우 noncurrent version 만료 정책도 설정해야 한다. CDN은 origin의 private,max-age=60을 존중하도록 설정하고 별도 장기 캐시 정책을 덮어쓰지 않는다.

## 검증 범위

자동 테스트: 이미지 경계·위장 파일·실제 디코딩, HTTP CRUD/소유권/멱등성, 동시 생성, 업로드 실패 정리, 탈퇴, 암호화 S3 요청, V0→V19 및 V18→V19 PostgreSQL migration, 기존 사용자/OpenAPI 회귀.

실제 운영 S3/CDN 연결 및 프론트 API_INTEGRATION.md 타입 대조는 설정/자료 제공 뒤 별도 검증이 필요하다. 스토리지 대역 테스트와 실제 운영 검증을 구분한다.

WebP 디코더는 [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) 3.13.1을 사용한다.
