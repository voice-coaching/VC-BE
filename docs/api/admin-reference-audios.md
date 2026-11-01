# 관리자 아나운서 문장·기준 음성 등록 API

## 범위와 사전 조건

관리자가 한국어 문장과 직접 녹음한 아나운서 음성을 등록한다. 공용 `ANNOUNCER` 콘텐츠만 지원하며 일반 문장·클래스·회원 개인 원고에는 이 API를 사용하지 않는다. 프론트 UI·클래스 묶음·AI 비교 채점·TTS 생성 기능을 추가한 작업은 아니다.

문장 생성·공개는 [관리자 1차 API](admin-phase-one.md)를 재사용한다. 배포에는 V61·V62이 필요하다. 권한은 JWT ROLE_ADMIN과 현재 DB ACTIVE ADMIN을 모두 요구한다. 테스트 이용등급 admin은 접근 권한이 아니다. 음성 파일은 private S3, DB에는 재생 URL 대신 object key를 저장한다.

## 등록 흐름

```text
ANNOUNCER 문장 초안 생성
→ 업로드 URL 요청
→ 반환 URL에 파일을 직접 PUT
→ 업로드 ID로 검증·등록 완료 요청
→ 관리자 목록·미리듣기로 원문과 음성 확인
→ 문장 공개
→ 기존 사용자 목록·재생 API로 사용
```

이미 공개된 콘텐츠(예: 콘텐츠 279)에도 새 기준 음성을 연결할 수 있다. 이 경우 원문은 변경하지 않는다. 문장의 원문을 바꾸려면 기존 공개 원문 잠금 정책에 따라 새 초안을 만든다.

새 문장은 `GET /api/admin/content-categories?type=ANNOUNCER`의 code를 선택한 뒤 기존 생성 API로 만든다. 예시:

```http
POST /api/admin/contents
```

```json
{
  "content": {
    "type": "ANNOUNCER",
    "focus": "PRONUNCIATION",
    "category": "ANNOUNCER_PRACTICE",
    "difficulty": "BEGINNER",
    "title": "아나운서 따라 읽기",
    "description": "기준 음성을 듣고 같은 문장을 읽습니다.",
    "scriptText": "오늘 아침 서울의 날씨를 전해 드립니다.",
    "targetPronunciations": [],
    "estimatedSeconds": 8
  },
  "reason": "아나운서 연습 콘텐츠 등록"
}
```

반환된 id·version으로 아래 업로드를 진행한다. 콘텐츠 279나 음성 64를 새 등록에 하드코딩하지 않는다. 서버의 원문 해시 검사는 발급 후 문장이 바뀌었는지 확인하는 것이며, 음성의 실제 발화 내용과 문장이 일치하는지 자동 판독하는 기능은 아니다. 관리자가 미리듣기로 확인한다.

## 공통 HTTP 계약

- 모든 API: `Authorization: Bearer <accessToken>`, 변경 본문은 `Content-Type: application/json`.
- 공통 응답: `{result:boolean,message:string,data:T,code?:string}`. 실패 data=null.
- 응답 헤더: `Cache-Control: no-store`.
- contentId/audioId: 필수 정수. uploadId: 서버 발급 UUID.
- 요청 version: 콘텐츠 상세 또는 관리자 음성 목록의 현재 contentVersion을 전달한다. 음성 자체의 버전이 아니다.
- reason: 필수, 공백 불가, 최대 500자. 비밀번호·토큰·원문·서명 URL을 사유에 넣지 않는다.
- 공통 오류: 400 입력 오류, 401 로그인 필요, 403 현재 관리자 아님, 404 대상/소유 업로드 없음/ANNOUNCER 범위 밖, 409 버전·원문·등록 내용 충돌 또는 만료, 422 파일 검증 실패, 503 저장소·디코더·DB 오류.

## 1. 업로드 URL 발급

```http
POST /api/admin/contents/{contentId}/reference-audios/upload-url
```

Path: contentId 필수. Query: 없음.

```json
{
  "version": 0,
  "fileName": "announcer-01.m4a",
  "contentType": "audio/mp4",
  "fileSizeBytes": 123456,
  "sha256": "<파일 바이트의 소문자 SHA-256 hex 64자리>",
  "reason": "아나운서 기준 음성 등록"
}
```

| 필드 | 타입·필수 | 조건 |
| --- | --- | --- |
| version | long, 필수 | 0 이상, 현재 콘텐츠 version |
| fileName | string, 필수 | 최대 255자, `/`, `\`, NUL 금지. 키 생성에는 사용하지 않고 확장자만 확인 |
| contentType | string, 필수 | MP3는 audio/mpeg, AAC M4A는 audio/mp4 |
| fileSizeBytes | long, 필수 | 1~20,971,520 bytes(20MiB) |
| sha256 | string, 필수 | 실제 파일 전체 바이트의 소문자 hex 64자리 |
| reason | string, 필수 | 공통 사유 조건 |

성공 201. data:

| 필드 | 타입·nullable | 의미 |
| --- | --- | --- |
| uploadId | UUID string, non-null | 관리자·문장·파일 정보에 연결된 서버 발급 ID |
| uploadUrl | string, non-null | 해당 임시 객체에만 PUT 가능한 만료 URL |
| requiredHeaders | object<string,string>, non-null | PUT에 그대로 보낼 S3 서명 헤더 |
| expiresAt | offset datetime, non-null | 발급 후 약 10분. 등록 완료도 저장된 기한 내에 수행 |

SHA-256은 파일명이나 문장 원문의 해시가 아니다. 프론트는 원본 파일 바이트로 계산한다. 필수 헤더를 임의로 추가·삭제하지 않으며 S3 요청에 백엔드 JWT·쿠키를 보내지 않는다. 브라우저가 Content-Length를 설정하므로 requiredHeaders에는 Host·Content-Length를 넣지 않는다.

```text
PUT {uploadUrl}
Headers: requiredHeaders
Body: 원본 파일 바이트
```

임시 키는 서버가 `reference-audios/uploads/{uploadId}.{ext}` 형태로 생성한다(기본 prefix 기준). 체크섬과 create-only 조건을 서명한다. URL을 재사용해 다른 파일을 덮어쓰지 않는다. 동일 PUT 재시도에서 412가 나오거나 응답을 잃었다면 같은 업로드 ID·같은 파일의 완료 요청으로 서버 검증을 받는다.

저장소 또는 ffmpeg/ffprobe가 준비되지 않았다면 발급 전 503을 반환한다. 업로드 기록 생성 후 S3 서명 발급이 실패한 경우 해당 PENDING 기록은 남을 수 있으나 기준 음성으로 노출되지 않는다.

## 2. 파일 검증·음성 등록

```http
POST /api/admin/contents/{contentId}/reference-audios
```

Path: contentId 필수. Query: 없음.

```json
{
  "uploadId": "<발급받은 UUID>",
  "version": 0,
  "speakerName": "전문 아나운서",
  "primary": true,
  "reason": "문장과 녹음 내용 검수"
}
```

모든 필드가 필수다. speakerName은 공백 불가·최대 255자, primary는 boolean이다. speakerType은 서버가 ANNOUNCER로 정한다. durationMs·objectKey·audioUrl·외부 URL을 요청에서 받지 않는다.

서버는 소유 관리자·콘텐츠·기한·원문 해시·version을 확인한다. S3에서 길이·MIME·전체 바이트 SHA-256을 검증한 뒤 실제 컨테이너와 코덱을 확인하고 전체 오디오 프레임을 디코딩한다. 허용 조건은 MP3/mp3 또는 M4A/AAC, 오디오 스트림 1개, 영상 없음, 1~2채널, 8,000~192,000Hz, 500~180,000ms다.

검증과 S3 I/O 중에는 DB 트랜잭션·콘텐츠 잠금을 유지하지 않는다. 최종 등록 시 다시 권한·원문·기한·version을 확인하고 콘텐츠 행 잠금 안에서 음성·완료 상태·감사 기록을 함께 저장한다. 등록된 음성의 S3 키는 서버 전용 `reference-audios/announcer/{contentId}/{uploadId}.{ext}`이며, 클라이언트가 업로드 가능한 임시 키와 다르다. 검증한 바이트만 final 객체에 create-only로 저장한다.

신규 연결 201, 동일 완료 요청 재조회 200. data는 아래 Audio다. 최초 대표 아나운서 음성이 없으면 primary=false 요청이어도 첫 음성을 대표로 지정한다.

동일 uploadId·speakerName·primary·reason으로 재요청하면 기존 연결을 반환한다. version은 재조회 식별에 포함하지 않으며 이미 완료됐다면 만료 후에도 재조회할 수 있다. 다른 등록 내용은 409다. 재조회는 이후 변경된 대표 음성을 되돌리지 않고 현재 상태를 반환한다. 요청 응답 유실 시 새 음성 등록·AI 분석을 생성하지 말고 같은 완료 요청을 재시도한다.

실패 시 파일이 연결됐다고 표시하지 않는다. 409 버전 충돌은 최신 콘텐츠를 읽고 같은 uploadId로 재시도할 수 있지만, 발급 후 원문이 바뀌었다면 새 업로드가 필요하다. 검증 도중 기한이 지나도 등록은 거절한다.

## 3. 관리자 음성 목록

```http
GET /api/admin/contents/{contentId}/reference-audios
```

Path: contentId 필수. Query/body 없음. 성공 200. 공용 ANNOUNCER 콘텐츠라면 DRAFT·HIDDEN에서도 조회한다. 이 목록은 object key가 있는 ANNOUNCER 음성만 반환한다. TTS·COACH 음성의 편집 메뉴가 아니다.

data: `{items: Audio[], contentVersion: long}`. 대표 우선·audioId 오름차순 정렬. items가 없으면 빈 배열이다.

### Audio 응답

| 필드 | 타입·nullable | 의미 |
| --- | --- | --- |
| audioId | long, non-null | 사용자 재생 API에도 사용할 음성 ID |
| contentId | long, non-null | 연결된 문장 ID |
| speakerName | string, non-null(신규 등록) | 화자 표시명 |
| speakerType | string, non-null | ANNOUNCER |
| durationMs | integer, nullable | 신규는 서버 검증값. 기존 음성은 null일 수 있음 |
| primary | boolean, non-null | 현재 대표 여부 |
| contentVersion | long, non-null | 다음 콘텐츠 수정·음성 변경에 사용할 콘텐츠 version |
| createdAt | offset datetime, non-null | 등록 시각 |

관리자 업로드와 대표 변경은 콘텐츠 version을 증가시킨다. 이미 열어 둔 콘텐츠 편집 폼도 최신 상세를 다시 조회해야 한다. 기존 원문·학습 기록은 변경하지 않는다.

## 4. 대표 음성 지정

```http
PATCH /api/admin/reference-audios/{audioId}/primary
```

Path: audioId 필수. Query 없음. Body: `{version: long, reason: string}` 모두 필수. 성공 200, data=Audio. 해당 공용 ANNOUNCER 콘텐츠의 기존 대표 표시를 해제하고 선택한 음성을 대표로 지정한다. stale version은 409다. 음성 삭제·파일 덮어쓰기 기능은 제공하지 않는다.

## 5. 관리자 미리듣기

```http
GET /api/admin/reference-audios/{audioId}/playback-url
```

Path: audioId 필수. Query/body 없음. 성공 200. data는 `audioId:long`, `playbackUrl:string`, `expiresAt:offset datetime`이며 모두 non-null이다. private S3의 약 10분 재생 URL이다. 공개 전 초안·숨김 콘텐츠도 실제 관리자만 미리 들을 수 있다. 이 URL을 DB·감사 기록·영구 클라이언트 상태에 저장하지 않는다.

공개 후 일반 사용자는 기존 API를 그대로 사용한다.

```http
GET /api/practice-contents/{contentId}/reference-audios
GET /api/reference-audios/{audioId}/playback-url
```

일반 사용자 API에서 DRAFT·HIDDEN 콘텐츠를 열어 주는 변경은 없다. 기준 음성을 등록해도 원문·사용자 녹음 기반 AI 분석 또는 지원 학습 초점이 바뀌지는 않는다.

## 오류 코드

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR 또는 기존 입력 오류 | 확장자/MIME 쌍·크기·파일명·해시 형식·필수값 오류 |
| 401/403 | 기존 인증·권한 코드 | 로그인·현재 ADMIN 검증 실패 |
| 404 | RESOURCE_NOT_FOUND | 다른 관리자의 업로드, 다른 콘텐츠, private/비ANNOUNCER 대상 |
| 409 | ADMIN_EDIT_CONFLICT | version·원문·완료 요청 내용 충돌 |
| 409 | ADMIN_AUDIO_UPLOAD_EXPIRED | 아직 완료되지 않은 업로드의 기한 초과 |
| 422 | ADMIN_AUDIO_INVALID | 객체 없음·파일 크기/MIME/해시 불일치·가짜/손상/영상 포함·길이/코덱 오류 |
| 503 | ADMIN_AUDIO_UNAVAILABLE | S3·ffmpeg/ffprobe 사용 불가 또는 검증 동시 처리 한도 |
| 503 | ADMIN_STORE_UNAVAILABLE | DB·감사 저장 실패 |

기존 업로드 ID를 모르는 상태로 objectKey만 보내 다른 객체를 연결할 수 없다. 관리자 페이지의 파일 선택뿐 아니라 서버 검증 결과를 기준으로 완료를 표시한다.

## 배포·S3 설정

- `OBJECT_STORAGE_ENABLED=true`, bucket·region·reference-audios prefix와 기존 S3 credentials provider/IAM 역할을 사용한다.
- ffmpeg/ffprobe의 절대 실행 경로는 기존 `MEDIA_NORMALIZATION_FFMPEG_BINARY`, `MEDIA_NORMALIZATION_FFPROBE_BINARY` 설정을 사용한다. 등록에 실제 실행 파일이 필요하다. 관리자 기준 음성 검증은 일반 녹음 normalization의 enabled 플래그와 별개다.
- 프로세스별 최대 30초, probe 출력 64KiB, 서버 인스턴스당 동시 검증 최대 2건이다. 파일·해시 검사와 디코딩이 있는 동기 완료 요청이므로 프론트 프록시의 요청 시간 제한을 확인하고 응답 유실 시 멱등 재조회 흐름을 사용한다.
- IAM은 임시 prefix의 Put/Get/Head와 최종 prefix의 Put/Get/Head가 필요하다. 공개 ACL을 추가하지 않는다. 읽기 전용 기존 역할이면 업로드 권한을 별도로 보완한다.
- S3 CORS에는 실제 관리자 UI origin의 PUT·GET과 Content-Type, If-None-Match, x-amz-checksum-sha256 등 반환된 signed header를 허용한다. 서버 API CORS와 S3 CORS는 별개다.
- `reference-audios/uploads/`만 대상으로 임시 객체 lifecycle을 설정한다. 완료·만료된 업로드의 임시 파일을 영구 보관하지 않는다. 최종 announcer prefix 전체를 같은 만료 정책으로 지우면 안 된다.
- S3 저장 후 DB 등록 충돌·실패가 나면 연결되지 않은 최종 객체가 남을 수 있다. 같은 uploadId 재시도는 동일 final 객체를 재사용하며 다른 바이트로 덮어쓰지 않는다. 미연결 객체 정리 자동화는 이번 구현 범위가 아니므로 DB 업로드 상태·연결 여부를 대조해 운영자가 정리한다.

운영 S3 업로드·CORS·실제 서버 디코더·브라우저 미리듣기는 배포 후 검증해야 한다. 로컬 mock 검증을 실제 S3 연동 성공으로 안내하지 않는다.
