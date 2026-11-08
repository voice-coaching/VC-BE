# 업로드·분석 연결 복구 계약

## 적용 범위

아래 변경은 백엔드 복구 기반이다. 프론트는 **요청 전에** 사용자별 작업 키, 세션/녹음 번호, 로컬 녹음 파일 식별자를 영속 저장해야 한다. 응답을 잃은 뒤 새 키나 새 세션으로 자동 재접수하면 이 계약으로 중복을 구분할 수 없다.

기존 요청·응답 필드는 유지한다. 영상 브라우저의 AI 직접 업로드 경로에는 별도의 `clientRequestId`와 작업 조회 계약이 있으므로 영상 파일·작업 번호의 프론트 보관도 필요하다. 이 문서는 서버 녹음 등록 경로의 음성·영상 업로드를 다룬다.

## 선택 Idempotency-Key

다음 POST가 선택 `Idempotency-Key` 헤더를 지원한다.

| 경로 | 작업 범위 |
| --- | --- |
| `/api/training-sessions` | 세션 생성 |
| `/api/training-sessions/{sessionId}/recordings/upload-url` | 업로드 객체 발급 |
| `/api/training-sessions/{sessionId}/recordings` | 녹음 등록 |
| `/api/training-sessions/{sessionId}/analyze` | 분석 접수 |
| `/api/training-sessions/{sessionId}/analysis/retry` | 실패한 분석의 명시적 재시도 |

- 키는 `[A-Za-z0-9._:-]{1,128}`이며 UUID 사용을 권장한다. 잘못된 키는 400 `INVALID_IDEMPOTENCY_KEY`다.
- 사용자·작업 종류별로 구분한다. 같은 키와 같은 본문은 최초 성공 접수 결과를 재사용한다. 세션 경로 및 분석의 선택 녹음·실행 프로필도 요청 동일성에 포함한다.
- 같은 키에 다른 본문/세션/선택 녹음을 보내면 409 `IDEMPOTENCY_KEY_CONFLICT`다. 같은 콘텐츠를 새로 연습하는 경우 새 키를 사용한다.
- 접수 기록과 도메인 변경/outbox는 한 트랜잭션이다. 롤백한 요청은 키를 소비하지 않는다. 같은 사용자에 대한 키 사용 요청은 DB 사용자 잠금으로 직렬화한다.
- 키 원문과 presigned URL은 영수증에 저장하지 않는다. 사용자별 영수증은 계정 삭제 때 함께 삭제된다.
- 재전송 응답은 **최초 접수 영수증**이다. 분석이 이미 끝났어도 접수 당시 `PENDING`을 반환할 수 있으므로 최신 상태는 조회 API로 확인한다. AI 접수 게이트가 닫혀도 기존 영수증 재전송은 새 추론 없이 처리한다.
- 업로드 URL은 같은 키에 같은 `objectKey`와 원래 만료 시각을 반환한다. URL 문자열 자체는 다시 서명하므로 달라질 수 있다. 만료되거나 등록된 intent는 409 `UPLOAD_INTENT_NOT_ACTIVE`다. 만료 후 새 업로드가 필요하면 새 업로드 키를 발급한다.
- 헤더가 없는 기존 호출도 지원한다. 녹음 등록은 발급된 동일 객체와 동일 등록 본문에 대해 기존 녹음 번호를 반환한다. 새 세션·분석 생성까지 구분하려면 키가 필요하다.
- 같은 녹음에 다른 키 또는 키 없이 분석을 중복 접수하면 진행 중에는 409 `ANALYSIS_ALREADY_RUNNING`, 기존 분석이 있으면 409 `ANALYSIS_ALREADY_REQUESTED`다. 기존 상태를 조회하거나 실패 분석 retry를 사용한다. 진행 중·완료 분석에 새 retry 키를 보내도 새 실행을 만들지 않는다.

## GET /api/training-sessions/{sessionId}/recovery

Bearer 인증이 필요하며 본인 세션만 조회한다. 타 사용자 또는 없는 세션은 404다. 응답은 공통 `{result,message,data}` 형식이며 `Cache-Control: no-store`를 반환한다.

```json
{
  "result": true,
  "message": "학습 업로드와 분석 복구 정보를 조회했습니다.",
  "data": {
    "sessionId": 401,
    "status": "UPLOADING",
    "selectedRecordingId": null,
    "analysis": null,
    "uploads": [
      {
        "uploadIntentId": 51,
        "objectKey": "recordings/users/1/sessions/401/example.wav",
        "mimeType": "audio/wav",
        "fileSizeBytes": 32044,
        "status": "READY_TO_REGISTER",
        "recordingId": null,
        "expiresAt": "2026-11-08T11:00:00Z"
      }
    ],
    "hasMoreUploads": false,
    "serverTime": "2026-11-08T10:55:00Z"
  }
}
```

`analysis`는 현재 선택된 미삭제 녹음의 최신 분석이다. 존재하면 기존 `/analysis/status`의 필드(`analysisId`, `status`, `stage`, `progressPercent`, `failureReason`, `updatedAt`, `deadlineAt`, `serverTime`, `resultAvailable`)를 사용한다. 이 조회로 학습 완료나 승급 제출을 대신 확정하지 않는다. 완료 가능 여부는 기존 canonical 저장 확정 계약을 따른다.

`uploads`는 해당 세션에 발급한 최근 20개를 최신순으로 반환한다. `hasMoreUploads=true`면 전체 업로드 목록이라는 의미가 아니다. 이미 보관한 특정 업로드는 같은 발급 키로 재전송하여 식별할 수 있다.

| 업로드 상태 | 의미와 프론트 처리 |
| --- | --- |
| `UPLOAD_REQUIRED` | 저장소 HEAD에서 객체가 없음을 확인했다. 같은 로컬 파일 전체를 업로드한다. |
| `READY_TO_REGISTER` | 객체 존재·길이·MIME이 발급 정보와 일치한다. PUT을 반복하지 않고 녹음 등록을 진행한다. |
| `REGISTERED` | 녹음 등록이 커밋됐다. `recordingId`를 사용하고 등록을 중복 수행하지 않는다. |
| `EXPIRED` | 등록 전 intent가 만료됐다. 기존 키로 갱신하지 않고 새 업로드 시도를 시작한다. |
| `RECORDING_UNAVAILABLE` | 소비된 intent에 유효한 녹음 연결이 없다. 과거 데이터 또는 삭제된 녹음일 수 있으며 자동 새 분석을 시작하지 않는다. |

저장소 접근 거절·타임아웃·서버 오류는 503 `ANALYSIS_INTEGRATION_UNAVAILABLE`이다. 객체 크기/MIME 불일치는 기존 422 `ANALYSIS_SOURCE_NOT_READY`를 사용한다. 이 오류를 ‘파일 없음’으로 바꾸지 않는다. 과거에 발급한 객체도 등록 시 실제 HEAD 검증을 통과해야 한다.

등록 완료 intent는 만료 정리에서 제외한다. 새 연결 정보는 이 변경 이후 성공한 등록에 저장하며, 이미 존재하던 과거 intent/녹음의 관계를 추측해 소급 연결하지 않는다.

## 프론트 복구 순서

1. 최초 HTTP 요청 전에 사용자·연습 시도별 키와 녹음 파일 연결을 보관한다. 각 단계 키를 응답 수신 뒤에만 만들거나 새로고침 때 바꾸지 않는다.
2. 세션 생성 응답을 잃었다면 같은 생성 키·본문으로 POST를 재전송하여 세션 번호를 받는다.
3. 알고 있는 세션의 `/recovery`를 조회한다. 연결 실패 시 대기/재조회하며 파일이 없다고 단정하지 않는다.
4. 파일이 저장됐으면 등록을, 이미 등록됐으면 선택 녹음/분석 상태 조회를 진행한다. 분석 POST 응답을 잃었다면 같은 키·본문으로 재전송한다.
5. `PENDING`/`PROCESSING`은 GET으로 기다리고 `COMPLETED`는 기존 결과·저장 확정 흐름으로 이동한다. 실패 분석 재실행은 별도 사용자 동작과 retry 키로 요청한다.

현재 PUT은 **전체 파일 재전송**이다. 바이트 단위 이어 올리기나 브라우저에 남지 않은 파일 복원은 제공하지 않는다. 원본 파일이 없으면 새 녹음이 필요하다.

## 검증 범위

자동 테스트는 실제 HTTP·인증·PostgreSQL·마이그레이션·동시 요청·커밋 후 응답 유실을 검사한다. 저장소, 미디어 변환, AI 접수 경계는 테스트 대역을 사용한다. S3 HEAD adapter의 누락·메타데이터 불일치·403/5xx 구분도 별도로 검사한다. 운영 S3/AI 모델 실행이나 실제 기기의 Wi-Fi 전환 성공을 뜻하지 않는다.
