# AI API 운영 검증 결과 — 2026-09-17

최초 검증 시각: 00:08~00:25 KST. 수정 후 재검증: 00:36~00:39 KST. 대상: `https://api.voice-coaching.site` 및 연결된 RunPod 워커.

## 수정 후 재검증 — 해결 완료

아래 최초 점검에서 발견한 세 문제를 수정하고 운영 배포 및 실제 영상 분석 완료를 확인했다.

- PR [#71](https://github.com/voice-coaching/VC-BE/pull/71) 병합 및 배포 성공. 운영 리비전 `a749fa7ab25567d30d150eb742b83e350d84eb68`.
- [운영 배포 실행](https://github.com/voice-coaching/VC-BE/actions/runs/35116121448) 성공.
- Flyway V26 적용: 기존 삭제 사유를 보존하면서 `ANALYSIS_COMPLETED` 허용.
- `ANALYSIS_CONSENT_POLICY_REVISION=voice-analysis-consent-v1` 설정. 환경파일 백업: `/etc/alpha-backend.env.bak.ai-api-fix.20260916T153252Z`.
- 동의 버전 미설정 시 capabilities가 분석 요청을 `NOT_CONFIGURED`로 표시하도록 보완.
- 잘못된 페이지의 응답 메시지는 `잘못된 입력값입니다.`, 미완료 분석은 `아직 분석이 완료되지 않았습니다.`로 수정.

실제 재검증: **testvideo2.mp4 → 세션 22 / 녹음 8 / 분석 7 → COMPLETED / COACHING_READY**.

| 검증 | 실제 결과 |
| --- | --- |
| capabilities | 200, `consentPolicyRevision=voice-analysis-consent-v1` |
| capabilities에서 받은 동의 버전으로 analyze | 200, 분석 7 접수 |
| 상태 조회 | 200, PROCESSING → COMPLETED / 100% |
| 세션 요약 | 200, COMPLETED / COACHING_READY |
| 완료 상세 | 200, summaryFeedback 및 pronunciationEvidence 저장·조회 |
| 구간 조회 | 200, items=[] — 현재 HTTP 계약상 정상 |
| 피드백 재구성 | 200, 저장된 승인 피드백 반환 |
| 결과 콜백 | nginx 로그에서 200, DB 완료 결과 이벤트 저장 확인 |
| 영상 정리 | 삭제 예약 사유 ANALYSIS_COMPLETED 저장 성공, 상태 DELETED |
| 잘못된 페이지 / 미완료 상세 | 400 / 409, 수정된 메시지 확인 |

분석 접수 00:36:27, 완료 반영 00:38:45 KST. 완료 상세의 `visualSupplement`는 null이었으며 음성 근거 기반 피드백을 반환했다. 영상 보조 근거 생성까지 성공했다는 의미는 아니다. 피드백 재구성도 새 Clova 추론이 아닌 기존 문구 반환이다.

검증: Gradle `test bootJar` 성공(264개 중 238 통과, 환경 조건에 따른 26 skip). PostgreSQL 임시 테이블에서 기존·신규 6개 삭제 사유 허용과 잘못된 사유 거절을 검증 후 롤백했고, 배포 후 실제 영상 완료 경로에서도 검증했다.

남은 검증 범위: 이번 재검증은 두 번째 영상의 정상 완료 경로를 대상으로 했다. 첫 번째 영상 재분석, 실패 작업의 성공적인 retry, 동일 결과 재전달의 DUPLICATE ACK는 별도로 실행하지 않았다. 전체 API의 모든 상태·분기를 검증했다는 의미는 아니다. 테스트 계정·숨김 콘텐츠·완료 분석 7은 후속 확인을 위해 보존했다. 키 사본은 변경·삭제하지 않았다.

## 최초 점검 결론 — 아래는 수정 전 기록

**전체 정상 동작 아님.** 업로드·정규화·분석 접수·실행 소유권·heartbeat는 동작하지만, 영상 분석 결과 콜백이 DB 제약조건 위반으로 503을 반환하여 완료 결과 저장이 실패했다. 준비 상태 200만으로 실제 분석 성공을 보장할 수 없음을 확인했다.

## 검증 데이터

- 공식 회원가입 API로 생성한 전용 사용자: 26. 인증정보는 이 문서에 기록하지 않음.
- 사용자 제공 영상: `testvideo1.mp4`(2,097,382 bytes), `testvideo2.mp4`(1,193,779 bytes).
- 사용자 제공 대본: 쌀과 콩을 깨끗한 그릇에 담아요
- 승인받아 추가한 테스트 콘텐츠: 1. 세션 생성 직후 `HIDDEN` 처리. 기존 콘텐츠 변경 없음.
- 빈 세션 20: 오류 처리 검증 후 취소.
- 영상 1: 세션 21, 녹음 7, 분석 6. 결과 전달 장애 확인 후 세션 취소.
- 영상 2: 세션 22, 녹음 8. 업로드·정규화·선택까지 성공. 공통 결과 수신 장애가 확인되어 분석 접수는 하지 않음.
- 두 영상 모두 `AUDIO_VISUAL`, 음질 `PASS`. 정규화된 길이는 각각 4,843ms, 2,709ms.

## 공개 AI API

공개 8개 경로 모두 무인증 요청에 401을 반환했다. 아래는 별도 테스트 사용자 JWT로 확인한 결과다.

| API | 관측 결과 | 판정 범위 |
| --- | --- | --- |
| GET `/api/analysis-capabilities` | 200, 업로드·분석 `CONFIGURED`, 동의 버전 `null` | 응답 성공이나 설정 문제 있음 |
| POST `/api/training-sessions/{id}/analyze` | 200, 분석 6 접수. 중복 409, 동의 거부 400, 선택 녹음 없음 409 | 접수·요청 검증 성공 |
| GET `/api/training-sessions/{id}/analysis/status` | 200, `PENDING` → `PROCESSING`, 0 → 70 | 진행 상태 조회 성공. 완료 미검증 |
| POST `/api/training-sessions/{id}/analysis/retry` | 실패하지 않은 분석 409, 선택 녹음 없음 404 | 거절 동작 확인. 실패 작업 재접수 성공은 미검증 |
| GET `/api/training-sessions/{id}/analysis` | 200, 분석 6과 `PROCESSING` 반환. 분석 없음 404 | 요약 조회 성공 |
| GET `/api/analyses/{id}` | 미완료 409, 존재하지 않는 분석 404 | 오류 처리 확인. 완료 상세 200은 장애로 미검증 |
| GET `/api/analyses/{id}/segments` | 미완료 409, 없음 404, 잘못된 페이지 400 | 오류 처리 확인. 완료 목록 200은 미검증 |
| POST `/api/analyses/{id}/feedback/regenerate` | 미완료 409, 없음 404 | 오류 처리 확인. 승인 피드백 재구성 200은 미검증 |

## 내부 연동 API

내부 4개 경로 모두 무인증 요청에 401을 반환했다. claim·heartbeat·result의 빈 JSON은 서비스 토큰 인증 후 422로 거절됐다.

| API | 관측 결과 | 판정 범위 |
| --- | --- | --- |
| GET `/api/internal/ai/worker-readiness` | 200 `ready`, 계약 v1.1 | 준비 상태 성공 |
| POST `/api/internal/ai/analyses/{id}/claim` | 실제 분석 6의 worker 소유권 저장 및 PROCESSING 전환, 없는 대상 404 | 실제 claim 적용 확인 |
| POST `/api/internal/ai/analyses/{id}/heartbeat` | 분석 6의 `last_heartbeat_at` 및 lease 지속 갱신, 없는 대상 404 | 실제 갱신 적용 확인 |
| POST `/api/internal/ai/analyses/{id}/result` | nginx 로그에서 반복 503, DB SQLState 23514 | **완료 결과 적용 실패**. APPLIED·DUPLICATE 성공 미검증 |

실제 소유 워커: `b42ebeee-f819-4531-9c12-43ffbfc03023`. 워커 리비전: `b4057a008ad45c89f540e4cb6e19b6f29cd53aed`.

## 분석 준비·취소 API

세션 생성, 두 영상의 업로드 URL 발급, S3 PUT, 녹음 등록·정규화, 목록 조회, 최종 선택은 모두 200이었다. 빈 세션 및 분석 실행 세션 취소도 200이었다. 세션 21 취소 직후 분석 상태 조회는 404였다. 원격 추론 프로세스 종료 및 파일 정리 완료까지 확인한 것은 아니다.

## 확인된 문제

### 1. 영상 분석 완료 콜백의 DB 제약조건 위반

워커의 작업 상태는 `DELIVERING_RESULT`였고 `/health/services`는 여전히 200 `ready`, `executorReady: true`였다. 백엔드 nginx 로그에서 `/api/internal/ai/analyses/6/result`가 반복 503을 반환했다.

백엔드 로그:

```text
SQLState: 23514
new row for relation "recording_deletion_outbox" violates check constraint "ck_recording_deletion_outbox_reason"
```

운영 DB의 해당 CHECK 제약조건 허용값:

```text
RECORDING_DELETED, SESSION_CANCELED, HISTORY_DELETED, USER_WITHDRAWN, UPLOAD_EXPIRED
```

소스의 `AnalysisResultIngestionService.scheduleVisualDeletion()`은 영상 분석 완료 시 `RecordingDeletionReason.ANALYSIS_COMPLETED`를 저장한다. 운영 제약조건에는 이 값이 없어서 완료 트랜잭션이 롤백된다. 분석 6은 완료 결과 이벤트가 저장되지 않은 채 PROCESSING으로 남았다.

수정 방향: 기존 허용값을 유지하면서 `ANALYSIS_COMPLETED`를 추가하는 DB 마이그레이션과 영상 완료 결과의 통합 검증. 이번 점검에서는 운영 스키마를 변경하지 않았다.

### 2. 분석 동의 정책 버전 누락

capabilities의 `consentPolicyRevision`이 null이고 EC2 환경파일의 `ANALYSIS_CONSENT_POLICY_REVISION`도 미설정이었다. 응답값을 그대로 사용한 `{accepted: true, policyRevision: null}` 요청은 400이었다.

추론 경로를 분리 검증하기 위해 기존에 정한 `voice-analysis-consent-v1`을 명시한 요청은 200으로 접수됐다. 이는 capabilities를 이용하는 정상 클라이언트 흐름이 성공했다는 의미가 아니다.

수정 방향: 실제 동의 정책 버전을 서버에 설정한 후 capabilities와 분석 접수 흐름 재검증. 이번 점검에서는 운영 설정을 변경하지 않았다.

### 3. 오류 메시지 부정확

- 구간 조회 `page=-1&size=201`: HTTP 400은 맞지만 메시지가 `이메일 형식이 올바르지 않습니다.`였다.
- 미완료 분석의 상세·구간·피드백 요청: HTTP 409이나 메시지가 `분석이 완료된 후 학습을 종료할 수 있습니다.`였다.

## 검증 한계

실제 영상 분석의 완료 저장이 차단되어 상세·구간·피드백 성공 응답, 정상 결과의 중복 ACK, 실패 작업의 성공적인 retry까지 검증하지 못했다. 인증 및 오류 응답 성공을 해당 API의 전체 정상 동작으로 판정하지 않았다. 영상 2는 분석 전 단계까지만 검증했다. 테스트 계정·숨김 콘텐츠·세션 22와 녹음 8은 후속 재검증을 위해 남아 있다.
