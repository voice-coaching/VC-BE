# 칭호 승급 시험 API

2026-11-07 승급 판정 수정: `/submit`은 시험 생성 때 저장한 `passingScore` 이상인 경우에만
`user_titles`를 변경한다. 미달은 `FAILED`, `passed=false`와 기존 `currentTitle`을 반환한다.
본인 시험에 연결된 세션·콘텐츠의 선택된 녹음에 대해 완료된 분석 점수만 사용한다.
점수 없음·0~100 범위 밖·다른 세션·미완료 분석은 `409 ANALYSIS_SCORE_UNAVAILABLE`이며
시험을 채점하거나 칭호를 변경하지 않는다. 시험 세션이 없거나 analysisId가 없으면
`409 ANALYSIS_NOT_COMPLETED`다. 동일 분석 재제출은 기존 결과를 반환하며 다른 분석은 거부한다.
`titleExamId`가 있는 세션 생성은 일반 원고 처리보다 먼저 시험 연결을 수행한다.
시험 콘텐츠가 다르거나 클래스 단계가 전달되면 거부하고, 채점된 시험의 세션을 다시 연결하지 않는다.

이번 수정은 합격 판정과 제출에 한정한다. 아래 기존 응시 횟수 정책 설명을 복구하는 변경은
포함하지 않으며, 현재 main의 응시 자격 완화는 유지한다. AI 접수·점수 전달 계약과 DB 스키마도
변경하지 않는다. 과거 잘못 승급된 계정은 자동 강등하지 않으므로 별도 이력 확인이 필요하다.

2026-11-01: 신규 시험 음성 분석은 [통합 V6 / handoff V2 계약](ai-unified-v6-contract.md)을 사용한다.
V6 결과는 독립 검증을 통과한 committed proof의 점수로만 채점한다.

이 문서는 프론트 승급 시험 플로우와 백엔드 구현 계약을 맞추기 위한 명세다. 모든 API는 로그인 사용자 기준이며 `Authorization: Bearer <accessToken>`이 필요하다.

## 엔드포인트

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/users/me/title` | 현재 칭호, 누적 완료 연습 횟수, 다음 승급 시험 자격 조회 |
| POST | `/api/users/me/title-exams` | 다음 칭호 승급 시험 생성 |
| GET | `/api/users/me/title-exams/{examId}` | 시험 상태 조회 |
| POST | `/api/users/me/title-exams/{examId}/submit` | 분석 결과 제출, 채점, 승급 처리 |

## 사용자 진행 순서

1. 마이페이지에서 `GET /api/users/me/title`로 현재 칭호와 `next.eligible`을 조회한다.
2. 응시 가능하면 `POST /api/users/me/title-exams`로 시험을 생성한다.
3. 생성 응답의 `practiceContentId`, `id`를 사용해 `POST /api/training-sessions`를 호출한다.
4. 기존 녹음 업로드, 최종 녹음 선택, AI 분석 요청 절차를 진행한다.
5. 분석 완료 후 `analysisId`를 `POST /api/users/me/title-exams/{examId}/submit`에 제출한다.
6. 합격 또는 불합격 결과를 표시하고 `GET /api/users/me/title`로 칭호 상태를 다시 조회한다.

### 시험 세션 생성 예시

```http
POST /api/training-sessions
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "contentId": 123,
  "titleExamId": 456,
  "courseStepId": null,
  "learningFocus": "PRONUNCIATION"
}
```

`contentId`는 시험 생성 응답의 `practiceContentId`와 일치해야 한다. 승급 시험 세션에는 `courseStepId`를 사용하지 않는다.

## GET /api/users/me/title

```json
{
  "result": true,
  "message": "칭호를 조회했습니다.",
  "data": {
    "code": "ABSOLUTE_BEGINNER",
    "label": "왕초보",
    "completedTrainingCount": 5,
    "minimumTrainingCount": 0,
    "next": {
      "code": "BEGINNER",
      "label": "초보",
      "requiredTrainingCount": 5,
      "remainingTrainingCount": 0,
      "passingScore": 70,
      "eligible": true
    },
    "updatedAt": "2026-09-15T13:30:00Z"
  }
}
```

최고 칭호라면 `next`는 `null`이다.

## POST /api/users/me/title-exams

- Request body: 없음
- Optional header: `Idempotency-Key`
- Success status: `201 Created`

```json
{
  "result": true,
  "message": "승급 시험을 준비했습니다.",
  "data": {
    "id": 456,
    "currentTitle": "왕초보",
    "targetTitle": "초보",
    "practiceContentId": 123,
    "requiredTrainingCount": 5,
    "passingScore": 70,
    "status": "READY",
    "createdAt": "2026-09-15T13:31:00Z",
    "trainingSessionId": null
  }
}
```

현재 구현은 생성 응답을 최초 생성 시점의 계약으로 고정한다. 진행 중인 시험의 최신 `status`, `trainingSessionId`가 필요하면 `GET /api/users/me/title-exams/{examId}`를 호출한다.

## GET /api/users/me/title-exams/{examId}

```json
{
  "result": true,
  "message": "승급 시험을 조회했습니다.",
  "data": {
    "id": 456,
    "currentTitle": "왕초보",
    "targetTitle": "초보",
    "practiceContentId": 123,
    "requiredTrainingCount": 5,
    "passingScore": 70,
    "status": "IN_PROGRESS",
    "createdAt": "2026-09-15T13:31:00Z",
    "trainingSessionId": 789
  }
}
```

`status` 값은 `READY`, `IN_PROGRESS`, `PASSED`, `FAILED` 중 하나다.

## POST /api/users/me/title-exams/{examId}/submit

### Request

```json
{
  "analysisId": 789
}
```

### Response

```json
{
  "result": true,
  "message": "승급 시험 채점이 완료됐습니다.",
  "data": {
    "examId": 456,
    "status": "PASSED",
    "score": 82.5,
    "passingScore": 70,
    "passed": true,
    "previousTitle": "왕초보",
    "currentTitle": "초보",
    "evaluatedAt": "2026-09-15T13:35:00Z"
  }
}
```

채점은 클라이언트가 보낸 점수가 아니라 서버에 저장된 `analysis_results.overall_score`를 사용한다.

Canonical V4/V5/V6 분석은 현재 실행과 일치하는 커밋된 결과 문서의 점수를 사용한다. 미확정 프리뷰와 수정 가능한 점수 컬럼만으로 승급하지 않는다. Independent 분석은 저장 완료된 결과의 점수와 전달 상태를 확인한다.

### 분석 저장 확정과 자동 제출

- `GET /api/v2/analyses/{analysisId}`, `GET /api/v3/analyses/{analysisId}`는 피드백을 먼저 표시할 수 있다. `jobStatus=COMPLETED`만으로 승급 제출을 시작하지 않는다.
- 결과 공개 후 DB 저장 중에는 `persistenceStatus=SAVING`, `actions.canComplete=false`다. 현재 실행의 결과와 분석 완료가 커밋되면 `persistenceStatus=SAVED`가 된다. B2 비동기 보관 완료까지 기다리는 것은 아니다.
- 프론트는 같은 분석을 조회하며 저장 확정을 기다리고, `SAVED` 및 `canComplete=true`를 확인한 뒤 학습 완료와 승급 제출을 진행한다. 기존 서버의 `AVAILABLE`은 저장 확정 증거로 사용하지 않는다.
- 일찍 도착한 승급 제출은 409 `ANALYSIS_NOT_COMPLETED`로 응답하며 시험·칭호를 변경하지 않는다. 자동 대기·재확인 시 동일 `examId`와 `analysisId`를 유지한다. 새 분석이나 재녹음은 필요하지 않다.
- 저장 완료 후 실제 점수가 없거나 유효하지 않으면 409 `ANALYSIS_SCORE_UNAVAILABLE`이며, 이를 자동 불합격이나 0점으로 처리하지 않는다.
- 기존에 거절된 제출을 서버가 자동 재실행하지는 않는다. 이미 채점된 동일 분석의 재제출은 최초 결과를 반환한다.

## 승급 정책

| 목표 칭호 | 누적 완료 연습 | 합격 점수 |
| --- | ---: | ---: |
| 초보 | 5회 | 70점 |
| 동네 아나운서 | 15회 | 75점 |
| 아나운서 지망생 | 30회 | 80점 |
| 아나운서 | 60회 | 85점 |

- 시작 칭호는 `ABSOLUTE_BEGINNER`다.
- 누적 완료 연습 횟수는 응시 자격이며 자동 승급 조건이 아니다.
- 합격하면 한 단계만 승급한다.
- 불합격하면 기존 칭호를 유지하며, 다시 시험을 생성해 재응시할 수 있다.
- 시험 생성 시 콘텐츠, 합격 기준, 필요 연습 횟수를 snapshot으로 고정한다.

## 서버 검증

- 로그인 사용자 소유의 시험, 세션, 분석인지 확인한다.
- 분석이 해당 시험의 콘텐츠와 세션에 연결되어 있는지 확인한다.
- 분석 상태가 완료됐고, 선택된 녹음이 삭제되지 않았으며, 유효한 점수가 있는지 확인한다.
- 같은 분석 재제출은 동일 결과를 반환한다.
- 이미 채점된 시험에 다른 분석을 제출하면 `TITLE_EXAM_ALREADY_GRADED`로 거부한다.
- 중복 시험 생성과 동시 제출로 중복 승급되지 않도록 사용자 단위 잠금과 트랜잭션을 사용한다.

## Error Cases

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 요청 본문 또는 `Idempotency-Key` 형식 오류 |
| 401 | `AUTHENTICATION_REQUIRED` | 인증 필요 또는 Access Token 만료 |
| 403 | `FORBIDDEN` | 정지 또는 탈퇴 사용자 |
| 404 | `RESOURCE_NOT_FOUND` | 대상 없음 또는 다른 사용자 소유 |
| 409 | `TITLE_EXAM_NOT_ELIGIBLE` | 누적 완료 연습 횟수 부족 |
| 409 | `MAX_TITLE_REACHED` | 이미 최고 칭호 |
| 409 | `TITLE_EXAM_CONTENT_MISMATCH` | 시험 콘텐츠와 세션 콘텐츠 불일치 |
| 409 | `ANALYSIS_NOT_COMPLETED` | 시험 연결이 없거나 해당 시험 분석이 아직 진행·저장 중. 같은 분석의 저장 확정 후 제출 |
| 409 | `ANALYSIS_SCORE_UNAVAILABLE` | 분석은 완료됐지만 승급 채점용 점수 없음 |
| 409 | `TITLE_EXAM_ALREADY_GRADED` | 이미 채점된 시험 |
| 503 | `TITLE_EXAM_CONTENT_UNAVAILABLE` | 운영 DB에 시험용 게시 콘텐츠가 준비되지 않음 |

## 운영 확인 사항

- 운영 DB의 `title_policies.practice_content_id`가 실제 게시 콘텐츠를 가리키는지 확인해야 한다.
- 승급 시험 세션도 `COMPLETED`가 되면 누적 완료 연습 횟수에 포함된다.
- 운영 배포 후 생성, 세션 연결, 녹음 업로드, 분석, 채점, 칭호 갱신까지 통합 테스트가 필요하다.
