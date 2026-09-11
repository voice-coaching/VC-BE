# RunPod 음성·입술·Clova 파이프라인 API 연결 안내

> 현재 결정(2026-09-10): RunPod 연동은 Backend -> RunPod HTTP 요청과
> RunPod -> Backend HTTP callback 방식을 사용한다. Redis는 기존 애플리케이션 cache
> 용도로 유지하지만, 이번 단계의 Backend-AI MQ 전송에는 Redis Stream을 사용하지 않는다.
> [Backend-AI RunPod HTTP Callback 계약](api/ai-runpod-http-callback-contract.md)을 참고한다.

2026-09-10. 이번 범위는 **백엔드 API 구현과 `develop` 대상 PR**이다. 사용자의 후속 지시에 따라 Redis 연결은 다음 단계로 남긴다. 원격에는 `dev` 브랜치가 없고 기존 PR과 배포 workflow가 `develop`을 사용한다. 이 PR은 직접 merge하거나 운영 서버에 배포하지 않는다.

## 구현과 실제 연결의 구분

| 항목 | 이 PR의 상태 |
|---|---|
| 녹음·분석 capability API | 신규 구현. 인증된 사용자에게 지원 형식·한도·동의 정책·연결 설정 여부 제공 |
| 업로드 URL·등록·선택·재생·삭제 API | 기존 구현 보완. 미연결 가짜 URL 제거, 발급 intent 선검증과 동시성 보호 |
| 분석 요청·상태·결과·retry API | 기존 public API 유지. 내부 전송은 RunPod HTTP request + Backend callback, outbox/claim/heartbeat/retry로 운영 안정화 |
| AWS PostgreSQL 녹음 메타데이터 | 기존 Flyway V0~V15에 구현됨. 동일 테이블을 새로 생성하지 않음 |
| 사용자 녹음용 객체 저장소 | 아직 생성·연결하지 않음. 현재 SDK 구현은 private S3/S3-compatible 저장소용 |
| 분석 전송 | RunPod HTTP request + Backend callback으로 전환. `ANALYSIS_STREAM_ENABLED=false` 유지 |
| RunPod worker + Clova 연결 | HTTP endpoint와 callback 인증을 후속 연결 항목으로 준비 |

## 프론트에서 호출하는 순서

1. JWT로 `GET /api/analysis-capabilities`를 읽는다. 업로드·분석이 `NOT_CONFIGURED`면 관련 버튼을 비활성화할 수 있다. `CONFIGURED`는 실제 S3·Redis·RunPod health 통과가 아니라 배포 설정 여부다.
2. `POST /api/training-sessions`에 서버 콘텐츠의 `contentId`와 `learningFocus=PRONUNCIATION`을 보내 세션을 만든다. 분석 대상 대본은 서버 콘텐츠에서 선택하며 클라이언트가 임의 대본·음소·사용자 ID를 워커에 전송하지 않는다.
3. `POST /api/training-sessions/{sessionId}/recordings/upload-url`에 `fileName`, `mimeType`, `fileSizeBytes`를 보낸다. 반환된 URL에 파일을 직접 PUT한다. `requiredHeaders`와 파일 바이트 수를 일치시킨다. 브라우저에서 `Content-Length`는 브라우저가 실제 body 길이로 설정한다.
4. `POST /api/training-sessions/{sessionId}/recordings`에 발급된 `objectKey`, MIME, 실제 크기, `durationMs`를 보낸다. 영상은 `videoProcessingConsentAccepted=true`와 capability의 **영상용** `videoProcessingConsentPolicyRevision`을 함께 보낸다. 음성 분석용 policy revision과 혼용하지 않는다.
5. 등록된 녹음을 `PATCH /api/training-sessions/{sessionId}/recordings/{recordingId}/select`로 선택한다. 영상 요청은 해당 영상에서 추출한 canonical WAV와 함께 같은 시도로 저장된다. 별도 촬영의 음성과 입술 영상을 임의 결합하지 않는다.
6. RunPod HTTP endpoint와 Backend callback 인증이 완료된 뒤 `POST /api/training-sessions/{sessionId}/analyze`에 분석 동의 body를 보낸다. Backend는 같은 transaction에서 분석 상태와 outbox를 저장하고, dispatcher가 RunPod `POST /v1/analysis-jobs`로 전달한다. 현재처럼 연결이 꺼져 있으면 `503`이며 가짜 분석을 생성하지 않는다.
7. `GET /api/training-sessions/{sessionId}/analysis/status`로 상태를 확인한다. 완료되면 `GET /api/analyses/{analysisId}` 또는 세션 결과 API를 읽는다. 실패 재시도에는 새 명시적 동의가 필요하며 `/analysis/retry`를 사용한다.

모든 위 API는 인증된 사용자와 소유 세션·녹음 기준이다. 음성·영상의 형식·크기·길이는 capability와 실제 등록 검증을 함께 따른다. 영상에는 오디오 트랙이 있어야 한다. 현재 지원 초점은 발음이며 억양 분석을 성공한 것처럼 표시하지 않는다.

세부 DTO와 HTTP 오류는 [API 명세](api/specification.md), [엔드포인트 목록](api/endpoints.md), [녹음 저장소 API](recording_storage_api_20260909.md)를 따른다. 등록 중에는 사용자→세션→intent 잠금이 유지되므로 같은 사용자의 동시 변경은 대기할 수 있다. 업로드 파일 변환은 기존 제한시간·격리 프로세스를 사용하며 실제 배포의 DB pool과 HTTP proxy timeout은 담당자가 확인한다.

## 결과를 화면에 표시할 때

- `summaryFeedback`: 워커가 검증한 상세 학습자 설명. DB의 TEXT에 그대로 저장하고 반환한다.
- `pronunciationEvidence`: 선택 음소, 위치, 같은 시도의 Seungun 근거. detector score를 발음 정답률로 표시하지 않는다.
- `visualSupplement`: 같은 선택 음소에 연결된 입술 보완. 없으면 음성 피드백을 유지한다. 영상 미등록 시도에 대한 시각 결과는 저장하지 않는다.
- `outcome=COMPLETED_NO_ISSUE`: 새 교정 문장을 만들지 않는 정상 완료다.
- STT·종합 점수·억양 점수 등 현재 제공되지 않는 값은 null이다. 0점이나 성공한 별도 분석으로 변환하지 않는다.

`summaryFeedback` 문자열만으로 Clova가 실제 실행되었는지 판단할 수 없다. 현재 `/feedback/regenerate`의 기본 provider도 저장된 승인 문장을 다시 반환하며 새 Clova 추론을 하지 않는다. 재생성 횟수 제한은 그대로 유지한다.

## 운영용 분석 계약

현재 운영 기준 계약은 [Backend-AI RunPod HTTP Callback 계약](api/ai-runpod-http-callback-contract.md)이다.
기존 Redis Stream request/result schema는 이번 RunPod 단계에서 사용하지 않는다.

Backend는 분석 요청을 받을 때 사용자·세션·녹음 소유권과 선택된 recording을 확인한다.
그 뒤 분석 상태와 RunPod 전달 outbox를 같은 DB transaction에 저장한다.
사용자 요청 HTTP transaction 안에서 GPU 추론 완료를 기다리지 않는다.

RunPod 전달 payload에는 원본 media byte, base64, 로컬 파일 경로, 사용자 임의 callback URL을 넣지 않는다.
Backend가 만든 canonical S3 object key, SHA-256, MIME, 크기, 길이, 대본 digest, `requestId`, `executionId`,
`deadlineAt`만 보낸다. 동의 증빙이나 HMAC grant는 Backend-AI HTTP JSON에 포함하지 않는다.

RunPod은 접수 후 Backend claim API로 현재 `requestId`/`executionId` 실행을 점유한다.
처리 중에는 heartbeat로 살아 있음을 알리고, 완료 또는 실패 시 Backend result callback API에 terminal 결과를 보낸다.
Backend는 `requestId`, `executionId`, `eventId`, 등록된 canonical audio SHA-256을 검증한 뒤 결과를 저장한다.
현재 실행 세대와 다른 늦은 callback은 stale 결과로 보고 저장하지 않는다.

결과 callback은 현재 DB/model에 저장 가능한 필드만 포함한다.
현재 제공되지 않는 STT·종합 점수·억양 점수·segments를 새로 생성하거나 detector score를 발음 정답률로 변환하지 않는다.
공개 응답의 기존 미제공 값은 null/빈 목록으로 유지한다. `COMPLETED_NO_ISSUE`에는 억지 교정 문장·음소 근거를 만들지 않는다.

callback 전달 실패를 모델 분석 실패로 덮어쓰지 않는다.
RunPod은 같은 `eventId`로 callback을 재전송하고, Backend는 동일 terminal 결과를 멱등하게 처리한다.
사용자 취소·녹음 삭제가 발생하면 Backend가 먼저 작업을 terminal/canceled 상태로 만들고 RunPod cancel을 전송한다.
이후 도착한 늦은 결과는 저장하지 않는다.

## AWS DB와 녹음 파일 저장소 준비

현재 backend는 PostgreSQL을 사용한다. 기존 AWS PostgreSQL의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 연결하면 Flyway가 설치 이력에 맞는 migration을 적용한다. 녹음 저장을 위해 사용자별 DB나 별도 binary 테이블을 만들지 않는다.

| 보관 대상 | 위치 |
|---|---|
| 사용자·세션·녹음 소유 관계, 객체 key, MIME·크기·길이·SHA-256 | PostgreSQL `training_sessions`, `voice_recordings` |
| 업로드 발급·만료·소비 상태 | PostgreSQL `recording_upload_intents` |
| 음성 분석·얼굴 영상 처리 동의 | PostgreSQL `processing_consents` |
| 삭제 요청과 재시도 | PostgreSQL `recording_deletion_outbox` |
| 분석 job·outbox·상세 피드백 | PostgreSQL의 분석 관련 기존 테이블 |
| 실제 원본 업로드와 canonical WAV/MP4 바이트 | 생성할 private 객체 저장소 |
| 연구·교육용 외부 데이터 | 기존 Backblaze B2. 사용자 녹음 저장소와 자격증명 용도를 구분 |

AWS의 DB에 아직 녹음 테이블이 없다면 담당자는 먼저 `flyway_schema_history`와 [스키마 문서](database/schema.md)를 대조한다. 빈 DB에는 V0부터, 기존 DB에는 아직 적용되지 않은 migration만 적용한다. 이 PR은 기존 migration checksum을 바꾸거나 이미 있는 테이블을 재생성하지 않는다. 실제 운영 DDL은 실행하지 않았다.

현재 객체 저장소 adapter는 AWS SDK S3를 사용한다. AWS S3를 선택하는 경우 담당자가 private 버킷과 backend 실행 role을 준비하고, [환경변수 예제](../.env.example)의 `OBJECT_STORAGE_BUCKET/REGION`을 설정한다. 일반 AWS S3에는 `OBJECT_STORAGE_ENDPOINT`를 비워 둔다. 커스텀 S3 endpoint는 HTTPS와 조건부 GET/DELETE·VersionId 동작을 확인해야 한다.

backend는 presigned PUT/GET과 업로드 HEAD, 정규화 객체 쓰기·읽기·삭제가 필요하다. 후속 RunPod worker에는 canonical 입력을 읽는 범위만 부여한다. 실제 bucket 정책·CORS·보존 기간은 담당자가 사용하는 도메인·role·삭제 정책에 맞춰 설정한다. 키를 URL·로그·Git에 넣지 않는다. 제공된 B2 Master Key는 S3용 AWS 자격증명으로 사용할 수 없다.

S3와 정규화가 준비되기 전에는 `OBJECT_STORAGE_ENABLED=false`, `MEDIA_NORMALIZATION_ENABLED=false`를 유지한다. 음성 업로드와 영상 등록용 API는 구현되어 있지만, 저장소 없는 상태에서 실제 녹음이 저장됐다고 응답하지 않는다.

## 환경변수와 후속 RunPod 연결

`.env.example`은 비밀 값 없는 입력 목록이다. 파일을 `.env`로 복사하는 것만으로 Spring Boot에 적용되지 않는다. 담당자의 기존 systemd `EnvironmentFile`, 컨테이너 `env_file` 또는 secret manager에서 **backend 프로세스 환경변수로 주입**한다. intelligentAI의 B2 `.env`를 backend에 통째로 복사하지 않는다.

`REDIS_*`는 기존 애플리케이션 cache이다. 이번 RunPod 단계에서는 `ANALYSIS_STREAM_ENABLED=false`를 유지하고, 분석 전송에는 `AI_ANALYSIS_TRANSPORT=runpod_http`, `RUNPOD_ENDPOINT_URL`, `AI_ANALYSIS_API_TOKEN`, `AI_ANALYSIS_CALLBACK_TOKEN`, `AI_ANALYSIS_CALLBACK_BASE_URL`을 사용한다. `ANALYSIS_STREAM_ENABLED=false`가 기존 cache의 Redis 의존성까지 제거하지는 않는다.

현재 RunPod HTTP callback 전환에서 준비할 값은 다음이다.

1. RunPod HTTP endpoint URL과 Backend -> RunPod 접수/조회/cancel 서버 인증 token.
2. RunPod -> Backend callback/claim/heartbeat 서버 인증 token과 Backend callback base URL.
3. Backend outbox dispatcher, RunPod claim/heartbeat, callback retry, stale execution 차단 설정.
4. RunPod worker의 S3 읽기 권한, 고정 Seungun/visual 자산, release SHA와 artifact attestation.
5. intelligentAI `backend_analysis/production.py`와 `service.py`에 기존 로컬 Clova 생성 adapter 연결.
6. 연결 후 실제 요청, 결과 callback, 취소, 재시도, Pod 재시작 복구, 음성, 입술, Clova 결과를 담당자가 검증한 뒤 분석 admission을 활성화한다.

아래 Redis Stream 준비 목록은 과거 MQ 전송안의 참고 기록이며, 이번 RunPod HTTP callback 단계에서는 적용하지 않는다.

## 과거 Redis Stream 준비 목록

후속 연결 담당자는 다음을 준비한다.

1. private TLS Redis·ACL과 양쪽 request/result Stream·consumer group, 요청 취소 prefix.
2. 같은 HMAC key ID·secret, 음성 동의 policy revision. 서버 TTL은 기본 5분이며 워커의 최대 10분 제한을 넘기지 않는다.
3. RunPod worker의 S3 읽기 권한, 고정 Seungun/visual 자산, release SHA와 artifact attestation.
4. intelligentAI `backend_analysis/production.py`와 `service.py`에 기존 로컬 Clova 생성 adapter 연결. 현재 Redis worker는 Seungun+visual까지만 조합한다. `generate_grounded_video_feedback`를 같은 시도의 근거에 적용하고 검증한 message를 `summaryFeedback`으로 전달해야 한다. Clova 실패 시 기존 승인 카탈로그 fallback을 보존한다.
5. 연결 후 실제 요청·삭제·취소·재시도와 음성·입술·Clova 결과를 담당자가 검증한 뒤 분석 admission을 활성화한다.

이 PR은 위 값들을 임의 생성하거나 기존 RunPod를 재기동하지 않는다. 로컬 Clova의 별도 readiness 기록을 전체 서비스 성공 근거로 사용하지 않는다.

## 검증과 인계

Codex는 Java 및 테스트 소스 컴파일과 실행 JAR 생성을 수행하고, 정적 소스·전송 계약을 대조한다. 로컬 자동 테스트·브라우저·실제 AWS/Redis/RunPod 추론 QA는 사용자의 지시에 따라 실행하지 않는다. CI 수정 확인 명령은 `gradlew.bat compileJava compileTestJava bootJar -x test --no-daemon`이다. 기존 GitHub CI workflow는 변경하지 않으며 PR 푸시에 따라 자동 실행되는 결과를 확인한다.

초기 PR의 CI에서는 139개 테스트 중 12개가 실패했다. 운영 기본 모드로 바꾼 뒤에도 베타 context·v4 HMAC·대용량 원본 결과를 전제한 테스트가 남아 있었고, 결과 저장 테스트의 등록 음성 SHA·영상 metadata·관찰 시각도 새 계약에 맞지 않았다. 해당 fixture를 갱신하고 베타 HMAC vector·대용량 결과 검증은 명시적 베타 설정으로 유지했다. 운영 기본 모드의 개인 context 제외, 같은 녹음과 등록 영상의 결과 검증도 함께 확인하도록 테스트 코드를 보완했다.

새 `GET /api/analysis-capabilities`는 중앙 OpenAPI 문서 목록에서 누락돼 영문 기본 태그를 노출했다. 한국어 문서와 인증 설정을 등록하고, 기존 API 문서 테스트의 정확한 엔드포인트 수를 53개에서 54개로 갱신했다. 기존 한국어 문서·운영 입력 검증은 유지한다. CI 통과는 실제 Redis·저장소·RunPod 연결 검증을 대체하지 않는다.

검토·배포 대상은 `develop`이다. PR 생성은 배포 완료가 아니며 AWS 저장소 생성, DB migration 적용 결과, Redis 연결 및 전체 pipeline QA는 각각 별도 상태로 확인한다.
