# 녹음 저장소 API와 PostgreSQL 메타데이터

이 변경은 녹음·영상 업로드 API와 이미 있는 녹음 메타데이터 스키마를 보완한다.
Redis와 RunPod의 실제 연결, 사용자 녹음용 객체 저장소 생성, 운영 AWS DB 직접 변경은
이번 작업에 포함하지 않는다. 원격의 실제 개발 브랜치는 `develop`이며 백엔드 담당자가
해당 브랜치 대상 PR을 검토하고 배포한다.

## 파일과 DB의 역할

PostgreSQL에는 녹음의 소유 관계, 시도 번호, 객체 key, MIME, 크기, 길이, SHA-256,
기술 품질, 최종 선택 여부, 영상 처리 동의를 저장한다. 녹음 파일 자체를 DB의 binary
column에 넣지 않는다. 원본 업로드와 정규화된 WAV/MP4는 연결할 private object storage에
저장한다. 연구용 외부 데이터가 보관된 Backblaze B2와 사용자 녹음 저장소의 접근 권한은
별도로 관리한다.

다음 기존 Flyway migration이 필요한 저장 구조를 이미 제공한다. 같은 이름의 테이블이나
별도의 녹음 DB를 중복 생성하는 migration은 추가하지 않는다.

| Migration | 저장 구조 |
| --- | --- |
| `V0__create_core_application_schema.sql` | `training_sessions`, `voice_recordings`; 세션별 시도 번호 unique, 최종 선택 한 건 제약 |
| `V4__add_recording_media_integrity.sql` | 서버가 측정한 `audio_sha256` |
| `V6__create_processing_consent_ledger.sql` | 분석·영상 처리 동의 기록 |
| `V7__create_recording_deletion_outbox.sql` | 삭제 요청과 재시도 상태 |
| `V8__create_recording_upload_intents.sql` | 발급한 객체 key, 사용자·세션, MIME, 크기, 만료 시각, 소비 상태 |
| `V10__retain_restricted_visual_analysis_input.sql` | 같은 시도에 연결된 canonical MP4와 digest·동의 정보 |

운영에서는 기존 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 연결된 PostgreSQL에 Flyway를
적용한다. 새 DB는 V0부터 실행하고, 기존 DB는 설치된 migration 이력에 따라 남은 변경만
적용한다. 이 PR을 작성하면서 운영 DB에 연결하거나 DDL을 실행하지 않았다.

## 프런트엔드 호출 순서

모든 호출은 로그인 사용자의 Bearer access token을 사용한다. 기준 문장은 세션이 참조한
서버의 학습 콘텐츠에서 가져오며, 녹음 등록 요청으로 임의의 기준 문장을 전달하지 않는다.

1. `POST /api/training-sessions`로 콘텐츠의 세션을 생성한다.
2. `POST /api/training-sessions/{sessionId}/recordings/upload-url`에
   `fileName`, `mimeType`, `fileSizeBytes`를 전달한다.
3. 응답의 `uploadUrl`에 파일을 PUT한다. 발급한 MIME과 정확한 파일 크기를 유지하고
   `requiredHeaders`에 맞춘다. 브라우저의 `Content-Length`는 파일 body로 브라우저가
   계산하므로 자바스크립트에서 강제로 설정하지 않는다.
4. 발급 시각부터 10분 이내에
   `POST /api/training-sessions/{sessionId}/recordings`를 호출한다.
5. 등록 응답의 `qualityStatus`가 `PASS`이면 해당 녹음을 최종 선택할 수 있다.
   녹음 목록·재생·삭제 API는 [API 명세](api/specification.md)를 따른다.
6. 분석 요청 API 연결은 Redis와 worker를 준비한 뒤 진행한다. 이번 PR만으로 실제
   Seungun·입술 분석·Clova 추론이 수행되었다고 판단하지 않는다.

등록 예시는 다음과 같다. 숫자와 key는 직전 upload-url 응답 및 실제 파일 값으로 대체한다.

```json
{
  "objectKey": "recordings/users/42/sessions/123/issued-object.mp4",
  "mimeType": "video/mp4",
  "fileSizeBytes": 1048576,
  "durationMs": 5000,
  "videoProcessingConsentAccepted": true,
  "videoProcessingConsentPolicyRevision": "voice-video-processing-consent-v1"
}
```

key 형태만 맞춰 직접 만든 요청은 허용하지 않는다. 실제 발급된 upload intent와
일치해야 한다. 영상은 별도 영상 처리 동의를 확인한 뒤 decode한다. 음성 등록에는
두 영상 동의 필드를 생략할 수 있다. `durationMs`는 표시용 주장이고, 영속 값은
서버가 정규화 WAV에서 측정한다.

## 이번 업로드 보완

기존 구현은 파일을 정규화하고 업로드 원본을 삭제한 뒤 upload intent를 소비했다.
등록 전에 intent의 유효 기간과 발급 명세를 확인하지 않았고, 정규화와 만료 정리 작업이
같은 원본을 동시에 다룰 수 있었다.

이제 `VoiceRecordingService.register`의 DB transaction 안에서 다음 순서로 처리한다.

1. 요청 필수 값과 영상 처리 동의를 확인한다.
2. 사용자 행을 먼저 잠그고 탈퇴·이용 제한 상태가 아닌지 다시 확인한다.
3. 세션 행을 잠그고 사용자 소유권과 녹음 변경 가능 상태를 확인한다.
4. 해당 사용자·세션·객체 key의 upload intent 행을 잠근다.
5. `ISSUED`이고 현재 시각이 `expiresAt`보다 이전인지, 발급된 MIME과 파일 크기가
   등록 요청과 정확히 일치하는지 확인한다.
6. 객체의 실제 HEAD 정보와 container/codec을 검사하고 정규화한다.
7. intent를 `CONSUMED`로 바꾸고 녹음 메타데이터를 저장한 뒤 commit한다.

전체 잠금 순서는 사용자→세션→intent다. 사용자 탈퇴는 같은 사용자 행을 먼저 잠근 뒤
동의를 취소하고 녹음 삭제 대상을 조회하므로, 아직 commit하지 않은 등록을 조회에서
놓치는 상황을 막는다. 등록이 먼저 완료되면 탈퇴가 그 녹음·동의를 포함해 정리하고,
탈퇴가 먼저 완료되면 등록은 파일을 검사하기 전에 거부된다. 세션→intent의 상대 순서는
세션 취소 경로와 동일하다. `reserveForRegistration`은
`Propagation.MANDATORY`를 사용하므로 호출자의 transaction 없이 잠금이 즉시 해제되는
실행을 허용하지 않는다. 만료 sweeper는 같은 intent 행의 쓰기 잠금을 얻어야 하므로
정규화가 진행 중인 intent를 먼저 만료시키지 못한다. 등록을 허용한 뒤 정규화 도중 TTL이
지나더라도 현재 transaction은 완료할 수 있다. 성공 후 소비된 intent는 새 요청에서
재사용할 수 없다.

기존 정규화 작업의 transaction 경계를 유지하므로 변환 중 사용자·세션·intent 잠금도
유지된다. 동일 사용자의 다른 등록·분석·탈퇴와 세션 취소 요청은 대기할 수 있다. 객체 저장소와 ffmpeg의
timeout, DB connection pool, HTTP proxy timeout은 실제 배포 환경에 맞춰 검토해야 한다.
등록 실패 후 원본이 이미 정리되었다면 새 upload-url을 발급받아 재업로드한다.

| 실패 조건 | API 오류 |
| --- | --- |
| 필수 값 없음 또는 빈 key/MIME | `400 INVALID_INPUT_VALUE` |
| 영상 처리 동의 없음 | `400 VIDEO_PROCESSING_CONSENT_REQUIRED` |
| 인증 이후 탈퇴 완료 또는 이용 제한 | `401 UNAUTHORIZED` 또는 `403 USER_SUSPENDED` |
| 다른 사용자 세션 또는 없는 세션 | 기존 소유권 조회 정책의 `404 RESOURCE_NOT_FOUND` |
| 분석 중이거나 종료된 세션 | `409 INVALID_SESSION_STATE` |
| 해당 사용자·세션에 발급되지 않은 key | `409 UPLOAD_INTENT_NOT_FOUND` |
| 만료·소비·취소된 발급 건 | `409 UPLOAD_INTENT_NOT_ACTIVE` |
| 발급 MIME·크기 불일치 또는 객체 정보 불일치 | `422 ANALYSIS_SOURCE_NOT_READY` |
| 저장소·정규화 기능 미연결 | `503 ANALYSIS_INTEGRATION_UNAVAILABLE` |

## 영상과 음성의 연결

허용된 하나의 영상 업로드로 canonical MP4를 만든 뒤 **그 MP4에서** 16 kHz mono PCM
WAV를 추출한다. MP4와 WAV의 SHA-256, 크기, 영상 동의를 하나의 `voice_recordings` 행에
연결한다. 따로 녹음한 오디오와 다른 시점의 영상을 임의로 결합하지 않는다. 음성만
업로드하면 visual 필드는 비어 있다.

현재 허용 형식은 음성 `audio/webm`, `audio/mpeg`, `audio/wav` 최대 20 MiB,
영상 `video/mp4`, `video/quicktime`, `video/webm` 최대 100 MiB이다. 실제 codec/stream
검사는 [Backend–AI 계약](api/ai-redis-stream-contract.md)을 따른다.

## 저장소를 연결할 때

`OBJECT_STORAGE_ENABLED=false`일 때 upload-url과 사용자 녹음 재생 URL API는
`503 ANALYSIS_INTEGRATION_UNAVAILABLE`를 반환한다. 가짜 upload/playback URL이나
DB에 들어 있던 임의의 HTTP URL을 성공 응답으로 반환하지 않는다. upload-url 요청의
세션 상태 변경과 발급 intent 저장도 같은 transaction에서 rollback된다. 녹음 목록 등
메타데이터 조회는 유지되며, 객체 삭제 worker는 미연결 저장소에서 삭제가 완료되었다고
기록하지 않고 기존 실패·재시도 정책을 따른다.

기준 발음 음성의 `ReferenceAudioReaderImpl`은 이 사용자 녹음 저장소 포트를 호출하지
않으며, 등록된 기준 음성 URL을 반환하는 별도 경로다. 이번 disabled-provider 보완의
영향은 사용자 녹음 업로드·재생·삭제 경계에 한정된다. 프런트엔드는 미연결 상태의
503 응답에서 업로드를 진행하거나 재생 버튼을 정상 URL처럼 처리하지 않아야 한다.

기존 포트 구현은 S3 호환 API와 AWS SDK 기본 credential chain을 사용한다.
`OBJECT_STORAGE_*`, `MEDIA_NORMALIZATION_*`는
[배포 문서](architecture/deployment.md)에 따라 별도로 설정한다. 이 코드는 B2 Native API
Master Application Key를 AWS 자격 증명으로 변환하지 않는다. 외부 데이터용 `.env`의
B2 키를 백엔드의 AWS 환경변수에 복사하지 않는다.

저장소 담당자는 private bucket, 사용자별 upload key 정책, canonical prefix 쓰기 권한,
프런트엔드 PUT을 위한 CORS, 원본 정리와 canonical 재생·삭제 권한을 준비한다. 정규화는
원본 GET/DELETE를 ETag와 지원되는 VersionId에 묶으므로 실제 S3 호환 공급자에서 이
동작이 지원되는지 확인해야 한다. 버킷이나 새 자격 증명은 이 PR에서 생성하지 않았다.

코드의 잠금·검증 흐름을 정적으로 검토했다. 브라우저 테스트, 자동 회귀 테스트,
실제 파일 업로드·정규화·Redis 분석 실행은 수행하지 않았으며 담당 개발자가 검증한다.
