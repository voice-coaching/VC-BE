# 칭호 승급 시험 API

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
| 409 | `ANALYSIS_NOT_COMPLETED` | 채점 가능한 완료 분석 없음 |
| 409 | `ANALYSIS_SCORE_UNAVAILABLE` | 분석은 완료됐지만 승급 채점용 점수 없음 |
| 409 | `TITLE_EXAM_ALREADY_GRADED` | 이미 채점된 시험 |
| 503 | `TITLE_EXAM_CONTENT_UNAVAILABLE` | 운영 DB에 시험용 게시 콘텐츠가 준비되지 않음 |

## 운영 확인 사항

- 운영 DB의 `title_policies.practice_content_id`가 실제 게시 콘텐츠를 가리키는지 확인해야 한다.
- 승급 시험 세션도 `COMPLETED`가 되면 누적 완료 연습 횟수에 포함된다.
- 운영 배포 후 생성, 세션 연결, 녹음 업로드, 분석, 채점, 칭호 갱신까지 통합 테스트가 필요하다.
