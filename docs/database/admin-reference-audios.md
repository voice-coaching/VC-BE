# 관리자 아나운서 음성 업로드 DB: V62

V61 관리자 기능 위에 V62을 적용한다. 기존 문장·기준 음성·학습 기록을 삭제하거나 ID·원문을 바꾸지 않는다. `reference_audios`에 새 음성을 연결하는 기능이며 재생 URL은 저장하지 않는다.

## 변경

1. `reference_audios(id,content_id)`에 복합 UNIQUE `uq_reference_audio_content`를 추가한다. 기존 id PK가 있으므로 기존 행을 정리·수정할 필요는 없다.
2. 아래 `admin_reference_audio_uploads` 테이블을 추가한다. 담당 모듈 admin, soft delete 없음. 삭제·보존 자동화는 이번 범위에 포함하지 않는다.

| 필드 | 타입 | 필수·기본값 | 목적 |
| --- | --- | --- | --- |
| id | UUID | PK, 서버 생성 | 업로드 식별자 |
| actor_user_id | bigint | 필수, users.id FK | 발급 관리자 |
| content_id | bigint | 필수, practice_contents.id FK | 대상 아나운서 문장 |
| extension | varchar(5) | 필수 | .mp3 또는 .m4a |
| content_type | varchar(30) | 필수 | audio/mpeg 또는 audio/mp4. 확장자와 쌍 CHECK |
| size_bytes | bigint | 필수, 1~20971520 CHECK | 예상 원본 크기 |
| sha256 | varchar(64) | 필수, lowercase hex CHECK | 예상 파일 해시 |
| script_sha256 | varchar(64) | 필수, lowercase hex CHECK | 발급 시 문장 원문 해시 |
| created_at | timestamptz | 필수, 서버 Clock | 생성 시각 |
| expires_at | timestamptz | 필수, created_at보다 이후 CHECK | 생성 후 10분 등록 기한 |
| state | varchar(20) | 필수 | PENDING / COMPLETED |
| reference_audio_id | bigint | nullable, UNIQUE | 완료된 음성 ID |
| completion_digest | varchar(64) | nullable, lowercase hex CHECK | 화자·대표 요청값·사유의 완료 요청 해시 |

COMPLETED이면 reference_audio_id·completion_digest가 모두 있어야 하고 PENDING이면 둘 다 null이다. 완료 음성은 `(reference_audio_id,content_id)` 복합 FK로 같은 콘텐츠의 음성만 연결할 수 있다. 회원·콘텐츠·음성 FK의 삭제 동작은 기본 NO ACTION이다.

조회 인덱스는 `(actor_user_id,content_id,created_at DESC)`, 만료 관리용 partial index는 `expires_at WHERE state='PENDING'`다. signed URL·클라이언트 파일명·바이너리는 저장하지 않는다.

## 업무 연결

업로드 ID는 actor·content에 바인딩된다. UUID만 아는 다른 관리자는 해당 업로드를 연결할 수 없다. 파일 검증 중에는 DB 잠금을 잡지 않고, 완료 시 콘텐츠→업로드 행 순으로 잠근 뒤 상태·원문 해시·version을 재검증한다.

등록은 ReferenceAudio를 ANNOUNCER로 생성하고 object_key·서버 검증 duration_ms·화자·대표 여부를 저장한다. `audio_url`에는 임시 재생 URL을 넣지 않는다. 신규 파일 키는 서버 전용 prefix에 만들어 클라이언트의 재업로드로 기존 재생 파일이 덮어써지지 않게 한다.

기준 음성 등록·대표 변경에서는 이미 잠근 콘텐츠의 version을 즉시 증가시키고 Entity를 refresh한다. 이는 자식 관계의 변경도 콘텐츠 편집 충돌로 감지하고 커밋 전 응답에도 새 version을 반환하기 위한 처리다. 원문·공개 시각·기존 학습 연결은 바꾸지 않는다.

업무 변경과 REFERENCE_AUDIO_REGISTER/PRIMARY 감사 INSERT는 같은 트랜잭션으로 저장한다. 같은 완료 요청은 기존 연결을 반환하고 다시 대표를 바꾸거나 감사 기록을 중복 생성하지 않는다.

신규 업로드가 아니라 기존 seeded 아나운서 음성도 관리자 목록·대표 변경·미리듣기에서 사용할 수 있다. 기존 duration_ms=null 값은 그대로 유지한다. TTS·COACH 음성 편집은 제공하지 않는다.

## 운영 정리

임시 객체 prefix만 S3 lifecycle 만료 대상으로 지정한다. final 객체는 DB 연결 후 재생에 필요하므로 일괄 만료하지 않는다. S3 저장과 DB 커밋은 분산 트랜잭션이 아니며, 등록 충돌·DB 장애로 미연결 final 객체가 남을 수 있다. 만료된 PENDING 기록과 실제 reference_audios 연결 여부를 대조해 정리해야 한다. 자동 정리 작업·삭제 API는 구현하지 않았다.

실제 연동·CORS·권한·디코더 요구사항은 [API 계약](../api/admin-reference-audios.md)을 따른다. 운영 DB 변경은 migration 배포로 수행한다.
