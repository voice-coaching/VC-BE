# AI 독립 분석 API v1 — Backend 연동 계약

2026-11-07 사용자가 제공한 `AI 독립 분석 API v1`을 근거로 작성한 Backend 계약 사본이다.
AI 소스 구현 프로필은 `AI_ANALYSIS_EXECUTION_MODE=INDEPENDENT_PULL_V1`이다.
이 문서는 AI 후보 소스의 원격 배포나 운영 API 제공을 증명하지 않는다.
Backend 구현·설정·전환 제한은 [독립 분석 설계](../architecture/independent-analysis-pull.md)를 따른다.

## 인증과 경계

모든 v2 요청은 기존 Analysis 서버 토큰으로 `Authorization: Bearer <token>`을 보낸다.
토큰은 32~512자다. 사용자 JWT를 AI로 전달하지 않는다. 사용자·녹음 소유권과 상품 한도는 Backend가 확인한다.
Backend는 허용 목록에 등록한 HTTPS origin 하나에만 요청하며 redirect를 따라가지 않는다.
AI의 claim·heartbeat·journal·callback·handoff나 shared schema/release identity 교환은 이 경로의 조건이 아니다.

| Method | Path | 용도와 정상 응답 |
| --- | --- | --- |
| POST | `/v2/analysis-jobs` | 영속 접수 뒤 202 상태 응답 |
| GET | `/v2/analysis-jobs?clientRequestId=UUID` | 접수 응답 유실 조정, 200 상태 응답; 미존재 404 |
| GET | `/v2/analysis-jobs/{jobId}` | 200 상태 응답; FAILED도 HTTP 200 |
| GET | `/v2/analysis-jobs/{jobId}/result` | 확정한 원본 JSON bytes, 200; 미확정 409 |
| POST | `/v2/analysis-jobs/{jobId}/ack` | Backend commit 후 수령 확인, 200 상태 응답 |
| POST | `/v2/analysis-jobs/{jobId}/cancel` | AI 큐·실행 취소 요청 |

Backend는 취소 응답을 200/202 상태 객체로 읽는다. 실제 AI 후보의 취소 응답까지 종단 검증한 상태는 아니다.
`/health`, `/readyz` 외에 `/health/services`, `/health/deployment`에도 같은 서버 인증이 필요하다.
Backend 접수·시작·liveness는 이들 AI 진단 endpoint를 호출해 결정하지 않는다.

## PCM 업로드

`Content-Type: audio/wav`, `Content-Encoding: identity`이며 본문은 mono/16 kHz/16-bit PCM WAV다.
AI 상한은 20 MiB, 0.5~180초다. Backend는 기존 상품 제한인 원고 300자·음성 90초를 먼저 적용한다.
`X-Analysis-Metadata` 헤더 하나에 UTF-8 JSON을 표준 base64로 인코딩한다. 최대 24,000자다.

```json
{"clientRequestId":"<lowercase UUID>","scriptText":"<원고>","audioSha256":"<전송 WAV bytes SHA-256>","contentId":null}
```

| 필드 | 타입·필수·의미 |
| --- | --- |
| clientRequestId | 소문자 UUID 문자열, 필수. Backend 시도마다 한 번 발급하고 재전송에서 유지 |
| scriptText | 1~4000자 문자열, 필수. Backend 상품 상한은 더 작음 |
| audioSha256 | 64자리 소문자 SHA-256 문자열, 필수 |
| contentId | 생략/null/양의 정수. 현재 Backend는 콘텐츠 정수 ID를 보냄 |

추가 필드, 중복 JSON key, 비정상 숫자는 접수에서 거절된다.
Backend 정수 analysisId/recordingId, callback URL, lease, historyClaimSha256은 보내지 않는다.
동일 UUID·원고·음성·contentId는 동일 jobId이며 입력이 다르면 409다.
AI는 원음과 작업 기록을 atomic write+fsync한 뒤 202를 반환한다.

## 상태 응답

| 필드 | 타입·의미 |
| --- | --- |
| schemaVersion | `voice-coaching.independent-analysis.v1` |
| jobId / executionId / clientRequestId | 소문자 UUID 문자열; Backend 연결과 대조 |
| status | QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED |
| sequence | 갱신 때 증가하는 1 이상의 정수 |
| createdAt / updatedAt | UTC ISO-8601 시각 |
| resultAvailable | boolean |
| resultSha256 | 결과가 있으면 SHA-256 문자열, 그 외 null |
| failure | null 또는 `{"code":"..."}` |
| acknowledgedAt | null 또는 UTC ISO-8601 시각 |

Backend는 미지의 추가 응답 필드를 허용한다. 실패 작업도 결과를 가질 수 있다.
AI 실행 제한은 RUNNING부터 기본 900초이며 Backend lease로 AI 실행을 종료하지 않는다.
QUEUED/RUNNING은 조회 대기다. HTTP 오류를 AI FAILED로 바꾸지 않는다.

## 원본 결과와 ACK

`/result`는 result.json 원본 bytes를 최대 2 MiB로 반환한다.
`X-Result-SHA256`, ETag, 상태의 resultSha256은 같은 digest다.
Backend는 bytes의 SHA-256과 상태 digest 및 X-Result-SHA256을 대조하고 원본 bytes를 DB에 보관한다.
재직렬화한 객체의 hash로 원본 hash를 대신하지 않는다.

주요 결과 필드는 `score`, `coaching`, `decision`, `coreStatus`, `selection`, `sourceIdentity`, `failure`, `practiceResult`다.
`score.overallScore`는 0~100 숫자 또는 null이며 validity/reason을 함께 보존한다.
`coaching.feedback`은 한국어 총평이며 실패 시 없을 수 있다. `failure`는 성공 시 null, 실패 시 기계 코드 문자열일 수 있다.
`sourceIdentity.executionId/audioSha256/scriptSha256`은 제출 기록과 일치해야 한다.
Backend는 전체 CORE·공식 검산표·9항목 단계·6항목 지수를 다시 검산하지 않는다.

공개 응답에서는 기존 `data.canonicalAnalysis.score.overallScore` 및 `coaching.feedback` 경로를 유지한다.
AI UUID를 Backend 정수 분석 ID 자리에 넣지 않는다. transport와 공개 view는 별도 adapter로 변환한다.
점수 null과 실패 피드백 부재를 0점·가짜 총평으로 채우지 않는다.

DB commit 뒤 JSON `{"resultSha256":"<검증한 SHA>"}`로 ACK한다.
일치 시 200 상태의 acknowledgedAt이 채워지고 동일 ACK는 멱등, 다른 digest는 409다.
ACK가 유실되면 ACK만 재시도한다. ACK는 원음·CORE·결과 삭제 명령이 아니다.

## 오류 처리

오류 본문은 `reasonCode/message/requestId/executionId/retryable` 형식이며 reasonCode는 확장 가능하다.
아직 신뢰할 identity가 없으면 requestId/executionId는 null이다.
Backend는 실제 HTTP 상태를 기준으로 처리하고 원고·토큰·원본 오류 본문을 로그에 쓰지 않는다.

| 결과 | Backend 처리 |
| --- | --- |
| 202 응답 유실·timeout | 같은 clientRequestId로 GET 조정; 미존재일 때만 동일 UUID·동일 bytes 재제출 |
| 429/503/일시 5xx | Retry-After와 상한 있는 backoff/jitter로 durable 대기 |
| 401/403 | 서버 설정 장애로 기록·경고, 긴 간격 대기; 새 UUID/사용자 재시도로 우회하지 않음 |
| 400/413/415/422 | 제출 입력 오류 종료, 수정 없는 자동 재제출 중단 |
| 409 제출 충돌 | 해당 제출 종료·기록; 새 UUID 자동 발급 금지 |
| v2 404 | 라우팅/버전 전환 오류 대기; legacy 이중 제출 금지 |
| 상태/결과 파싱·digest 오류 | 수집 장애 기록, 동일 AI job 결과만 다시 수집 |
| ACK 실패 | commit 유지, ACK만 다시 시도 |

AI 영구 spool은 원음·CORE·결과를 보존하고 기본 2 GiB/1000작업 한도에서 가득 차면 503을 반환한다.
영구 볼륨·백업·보존 정리는 AI 운영 과제다. 영상 업로드는 이번 계약 범위가 아니다.
